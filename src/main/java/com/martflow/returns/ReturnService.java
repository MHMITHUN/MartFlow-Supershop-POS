package com.martflow.returns;

import com.martflow.common.MoneyUtil;
import com.martflow.common.NotFoundException;
import com.martflow.common.TimeSource;
import com.martflow.inventory.InventoryService;
import com.martflow.payment.CashAdapter;
import com.martflow.payment.PaymentChannel;
import com.martflow.persistence.Repository;
import com.martflow.sales.Sale;
import com.martflow.sales.SaleLine;
import com.martflow.sales.SaleStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Processes returns against a receipt: validates quantities (per line, across all previous
 * returns of that receipt), restocks the goods, refunds pro-rata through the original channel
 * (cash by default) and advances the sale's status to PARTIALLY_RETURNED / RETURNED.
 *
 * <p>An exchange is simply a return followed by a new bill — the till handles the new sale
 * through the normal pipeline. Earned loyalty points are intentionally NOT clawed back on
 * partial returns (standard loyalty practice); a full void does reverse them.
 */
public class ReturnService {

    private final Repository<Sale> sales;
    private final Repository<SaleReturn> returns;
    private final InventoryService inventory;

    public ReturnService(Repository<Sale> sales, Repository<SaleReturn> returns,
                         InventoryService inventory) {
        this.sales = sales;
        this.returns = returns;
        this.inventory = inventory;
    }

    /** One line the cashier wants to return. */
    public record RequestedLine(int lineNo, BigDecimal quantity, String reason) {
    }

    public SaleReturn returnItems(String receiptNo, List<RequestedLine> requested,
                                  String preferredChannel, String cashierUsername) {
        Sale sale = sales.findById(receiptNo)
                .orElseThrow(() -> new NotFoundException("Unknown receipt: " + receiptNo));
        if (sale.getStatus() == SaleStatus.VOIDED) {
            throw new IllegalStateException("Cannot return against a voided receipt");
        }
        if (requested == null || requested.isEmpty()) {
            throw new IllegalArgumentException("A return needs at least one line");
        }

        Map<Integer, BigDecimal> alreadyReturned = returnedQuantities(receiptNo);
        List<ReturnLine> returnLines = new ArrayList<>();
        BigDecimal refund = BigDecimal.ZERO.setScale(2);

        for (RequestedLine req : requested) {
            SaleLine line = sale.getLines().stream()
                    .filter(l -> l.lineNo() == req.lineNo())
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Receipt " + receiptNo + " has no line " + req.lineNo()));
            if (line.productId() == null) {
                throw new IllegalArgumentException(
                        "Line " + req.lineNo() + " (" + line.name() + ") is not returnable merchandise");
            }
            BigDecimal remaining = line.quantity()
                    .subtract(alreadyReturned.getOrDefault(req.lineNo(), BigDecimal.ZERO));
            if (req.quantity() == null || req.quantity().signum() <= 0) {
                throw new IllegalArgumentException("Return quantity must be positive on line " + req.lineNo());
            }
            if (req.quantity().compareTo(remaining) > 0) {
                throw new IllegalArgumentException("Line " + req.lineNo() + ": only "
                        + remaining.stripTrailingZeros().toPlainString() + " left to return");
            }
            BigDecimal unitNet = line.net().divide(line.quantity(), 6, RoundingMode.HALF_UP);
            refund = refund.add(MoneyUtil.round(unitNet.multiply(req.quantity())));
            returnLines.add(new ReturnLine(req.lineNo(), line.name(), req.quantity(),
                    req.reason() == null ? "" : req.reason()));
        }

        // restock first — a refund failure then rolls back nothing physical (best effort below)
        for (ReturnLine line : returnLines) {
            String productId = productIdOf(sale, line.saleLineNo());
            inventory.restore(productId, line.quantity());
        }

        PaymentChannel channel = channelFor(sale, preferredChannel);
        var result = channel.refund(refund, receiptNo);

        SaleReturn saleReturn = new SaleReturn(
                "RET-" + receiptNo + "-" + (returnsFor(receiptNo).size() + 1),
                receiptNo, TimeSource.now(), cashierUsername, returnLines, refund,
                channel.type().name(), result.transactionId());
        returns.save(saleReturn);

        sale.getReturnIds().add(saleReturn.getId());
        sale.setStatus(fullyReturned(sale, saleReturn) ? SaleStatus.RETURNED : SaleStatus.PARTIALLY_RETURNED);
        sales.save(sale);
        return saleReturn;
    }

    public List<SaleReturn> returnsFor(String receiptNo) {
        List<SaleReturn> result = new ArrayList<>();
        for (SaleReturn r : returns.findAll()) {
            if (receiptNo.equals(r.getReceiptNo())) {
                result.add(r);
            }
        }
        return result;
    }

    public List<SaleReturn> all() {
        return returns.findAll();
    }

    private Map<Integer, BigDecimal> returnedQuantities(String receiptNo) {
        Map<Integer, BigDecimal> map = new HashMap<>();
        for (SaleReturn r : returnsFor(receiptNo)) {
            for (ReturnLine line : r.getLines()) {
                map.merge(line.saleLineNo(), line.quantity(), BigDecimal::add);
            }
        }
        return map;
    }

    private boolean fullyReturned(Sale sale, SaleReturn latest) {
        Map<Integer, BigDecimal> returned = returnedQuantities(sale.getReceiptNo());
        for (SaleLine line : sale.getLines()) {
            if (line.productId() == null) {
                continue; // fees and round-off are not returnable
            }
            BigDecimal back = returned.getOrDefault(line.lineNo(), BigDecimal.ZERO);
            if (back.compareTo(line.quantity()) < 0) {
                return false;
            }
        }
        return true;
    }

    private String productIdOf(Sale sale, int lineNo) {
        return sale.getLines().stream()
                .filter(l -> l.lineNo() == lineNo)
                .map(SaleLine::productId)
                .findFirst().orElseThrow();
    }

    /** Refunds ride the original channel when possible; cash is the universal fallback. */
    private PaymentChannel channelFor(Sale sale, String preferred) {
        if (preferred != null && !preferred.isBlank()) {
            return channelByName(preferred.trim().toUpperCase());
        }
        if (!sale.getTenders().isEmpty()) {
            return channelByName(sale.getTenders().get(0).type().name());
        }
        return new CashAdapter();
    }

    private PaymentChannel channelByName(String name) {
        try {
            com.martflow.payment.TenderType type = com.martflow.payment.TenderType.valueOf(name);
            if (type == com.martflow.payment.TenderType.POINTS) {
                // Refunding cash value back into points would invent loyalty liability that was
                // never sold; earned points are only reversed by a full void.
                throw new IllegalArgumentException("POINTS refunds are not supported — points are "
                        + "reversed only by a full void; refund cash or the original electronic channel");
            }
            if (type == com.martflow.payment.TenderType.CASH) {
                return new CashAdapter();
            }
            if (type == com.martflow.payment.TenderType.CARD) {
                return new com.martflow.payment.CardAdapter();
            }
            if (type == com.martflow.payment.TenderType.BKASH) {
                return new com.martflow.payment.BkashAdapter();
            }
            if (type == com.martflow.payment.TenderType.NAGAD) {
                return new com.martflow.payment.NagadAdapter();
            }
        } catch (IllegalArgumentException unknown) {
            if (unknown.getMessage() != null && unknown.getMessage().contains("POINTS refunds")) {
                throw unknown; // our explicit rejection above, not an unknown channel name
            }
            // fall through to cash
        }
        return new CashAdapter();
    }
}
