package com.martflow.api.dto;

import com.martflow.billing.Bill;
import com.martflow.billing.item.BillableItem;
import com.martflow.billing.item.ComboLine;
import com.martflow.billing.item.UnitLine;
import com.martflow.billing.item.WeighedLine;
import com.martflow.loyalty.Customer;
import com.martflow.sales.Sale;
import com.martflow.sales.SaleLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Maps bills and sales to their API responses. */
public final class BillingMappers {

    private BillingMappers() {
    }

    public static BillingDtos.BillResponse toBillResponse(Bill bill, Bill.Totals totals, int undoDepth) {
        List<BillingDtos.BillLineResponse> lines = new ArrayList<>();
        List<BillableItem> raw = bill.items();
        List<BillableItem> priced = totals.pricedLines();
        for (int i = 0; i < raw.size(); i++) {
            lines.add(toLineResponse(i + 1, raw.get(i), priced.get(i)));
        }
        BillingDtos.BillCustomerSummary customer = bill.customer() == null ? null
                : toCustomerSummary(bill.customer());
        return new BillingDtos.BillResponse(
                lines,
                new BillingDtos.BillTotalsResponse(totals.gross(), totals.lineDiscount(),
                        totals.coupon(), totals.fees(), totals.net(), totals.vat()),
                customer,
                bill.couponCode(),
                bill.carryBags(),
                bill.carryBagUnitFee(),
                bill.deliveryFee(),
                undoDepth);
    }

    public static BillingDtos.BillLineResponse toLineResponse(int lineNo, BillableItem raw,
                                                              BillableItem priced) {
        BigDecimal gross = raw.lineNet();
        BigDecimal net = priced.lineNet();
        return new BillingDtos.BillLineResponse(
                lineNo,
                raw.name(),
                raw.sku(),
                raw.productId(),
                priced.describe(),
                raw.quantity(),
                raw.unitPrice(),
                gross,
                gross.subtract(net).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                net,
                priced.vatRate(),
                com.martflow.billing.VatCalculator.vatOf(net, priced.vatRate()),
                kindOf(raw));
    }

    public static BillingDtos.SaleResponse toSaleResponse(Sale sale) {
        List<BillingDtos.BillLineResponse> lines = new ArrayList<>();
        for (SaleLine line : sale.getLines()) {
            lines.add(new BillingDtos.BillLineResponse(
                    line.lineNo(),
                    line.name(),
                    line.sku(),
                    line.productId(),
                    line.name() + decorationSuffix(line),
                    line.quantity(),
                    line.unitPrice(),
                    line.gross(),
                    line.discount(),
                    line.net(),
                    line.vatRatePercent(),
                    line.vatAmount(),
                    line.kind()));
        }
        List<BillingDtos.TenderResponse> tenders = sale.getTenders().stream()
                .map(t -> new BillingDtos.TenderResponse(t.type().name(), t.amount(), t.transactionId()))
                .toList();
        Sale.Totals t = sale.getTotals();
        return new BillingDtos.SaleResponse(
                sale.getReceiptNo(),
                sale.getAt(),
                sale.getCashierUsername(),
                sale.getCustomerId(),
                sale.getStatus().name(),
                sale.getVoidReason(),
                sale.getVoidedAt(),
                lines,
                new BillingDtos.SaleTotalsResponse(t.gross(), t.discount(), t.coupon(), t.fees(),
                        t.roundOff(), t.net(), t.vat(), t.tendered(), t.change()),
                tenders);
    }

    private static String decorationSuffix(SaleLine line) {
        if (line.decorations().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(" (");
        for (SaleLine.Decoration decoration : line.decorations()) {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(decoration.type());
        }
        return sb.append(")").toString();
    }

    public static BillingDtos.BillCustomerSummary toCustomerSummary(Customer customer) {
        return new BillingDtos.BillCustomerSummary(customer.getId(), customer.getName(),
                customer.getPhone(), customer.getPointsBalance());
    }

    public static BillingDtos.CustomerResponse toCustomerResponse(Customer customer) {
        return new BillingDtos.CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getPhone(),
                customer.getCardNo(),
                customer.getPointsBalance(),
                customer.getMemberSince() == null ? null : customer.getMemberSince().toString(),
                customer.isActive());
    }

    /** Kind of a priced line — used by tests and the pattern studio. */
    public static String kindOf(BillableItem item) {
        if (item instanceof UnitLine) return "UNIT";
        if (item instanceof WeighedLine) return "WEIGHED";
        if (item instanceof ComboLine) return "COMBO";
        return "ADJUSTMENT";
    }
}
