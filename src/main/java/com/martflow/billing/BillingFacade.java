package com.martflow.billing;

import com.martflow.billing.commands.AwardPointsCommand;
import com.martflow.billing.commands.BillingInvoker;
import com.martflow.billing.commands.ChargeTenderCommand;
import com.martflow.billing.commands.CreateSaleCommand;
import com.martflow.billing.commands.ReserveStockCommand;
import com.martflow.billing.decorator.LineDiscount;
import com.martflow.billing.item.BillableItem;
import com.martflow.billing.item.ComboLine;
import com.martflow.billing.item.UnitLine;
import com.martflow.billing.item.WeighedLine;
import com.martflow.billing.validation.Handlers;
import com.martflow.billing.validation.ValidationChain;
import com.martflow.billing.validation.ValidationDtos.BillingCheck;
import com.martflow.billing.validation.ValidationDtos.TenderRequest;
import com.martflow.billing.validation.ValidationDtos.ValidationResult;
import com.martflow.catalog.ComboProduct;
import com.martflow.catalog.InventoryCatalog;
import com.martflow.catalog.Product;
import com.martflow.catalog.UnitProduct;
import com.martflow.catalog.WeighedProduct;
import com.martflow.common.MoneyUtil;
import com.martflow.common.NotFoundException;
import com.martflow.common.TimeSource;
import com.martflow.inventory.InventoryService;
import com.martflow.loyalty.Customer;
import com.martflow.loyalty.LoyaltyService;
import com.martflow.payment.CashAdapter;
import com.martflow.payment.PaymentChannel;
import com.martflow.payment.PointsAdapter;
import com.martflow.payment.TenderType;
import com.martflow.pricing.PromotionEngine;
import com.martflow.sales.ReceiptNoGenerator;
import com.martflow.sales.Sale;
import com.martflow.sales.SaleLine;
import com.martflow.sales.Tender;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * <b>Pattern: Facade.</b> One-call operations behind every POS button: scan, edit, undo, attach
 * customer, apply coupon, take tenders. Controllers stay dumb; the tender pipeline (validation
 * chain + command invoker) lives behind {@link #tender}.
 *
 * <p>Every bill operation is keyed by the cashier's login token — two tills never share a bill.
 */
public class BillingFacade {

    private final BillingSessionRegistry sessions;
    private final InventoryCatalog catalog;
    private final InventoryService inventory;
    private final PromotionEngine engine;
    private final LoyaltyService loyalty;
    private final com.martflow.persistence.Repository<Sale> sales;
    private final ReceiptNoGenerator receiptNumbers;
    private final ValidationChain validation;
    private final Map<TenderType, PaymentChannel> baseChannels;
    private final BigDecimal carryBagUnitFee;

    public BillingFacade(BillingSessionRegistry sessions, InventoryCatalog catalog,
                         InventoryService inventory, PromotionEngine engine,
                         LoyaltyService loyalty, com.martflow.persistence.Repository<Sale> sales,
                         ReceiptNoGenerator receiptNumbers, BigDecimal carryBagUnitFee) {
        this.sessions = sessions;
        this.catalog = catalog;
        this.inventory = inventory;
        this.engine = engine;
        this.loyalty = loyalty;
        this.sales = sales;
        this.receiptNumbers = receiptNumbers;
        this.carryBagUnitFee = carryBagUnitFee;
        this.validation = new ValidationChain(List.of(
                new Handlers.EmptyBillHandler(),
                new Handlers.WeighmentRequiredHandler(),
                new Handlers.StockAvailabilityHandler(),
                new Handlers.PromotionEligibilityHandler(),
                new Handlers.LoyaltyCardValidHandler(),
                new Handlers.TenderSufficientHandler()));
        this.baseChannels = new EnumMap<>(TenderType.class);
        this.baseChannels.put(TenderType.CASH, new CashAdapter());
    }

    // ---------------- bill editing ----------------

    public Bill billOf(String token) {
        return sessions.sessionFor(token).bill();
    }

    /** Scans an item by id or barcode. {@code quantity} is pieces; {@code weightKg} is kg. */
    public Bill addLine(String token, String productIdOrBarcode, Integer quantity, BigDecimal weightKg) {
        BillingSession session = sessions.sessionFor(token);
        Product product = catalog.findById(productIdOrBarcode)
                .or(() -> catalog.findByBarcode(productIdOrBarcode))
                .orElseThrow(() -> new NotFoundException("Unknown item: " + productIdOrBarcode));
        BillableItem line = lineFor(product, quantity, weightKg);
        session.snapshot();
        session.bill().addItem(line);
        return session.bill();
    }

    /** Changes a line's quantity/weight in place (Memento snapshot first). */
    public Bill updateLine(String token, int index, BigDecimal quantity) {
        BillingSession session = sessions.sessionFor(token);
        session.snapshot();
        session.bill().updateQuantity(index, quantity);
        return session.bill();
    }

    public Bill removeLine(String token, int index) {
        BillingSession session = sessions.sessionFor(token);
        session.snapshot();
        session.bill().removeItem(index);
        return session.bill();
    }

    public Bill clearBill(String token) {
        BillingSession session = sessions.sessionFor(token);
        session.snapshot();
        session.bill().clear();
        return session.bill();
    }

    /** Memento restore — the cashier's Undo. */
    public boolean undo(String token) {
        return sessions.sessionFor(token).undo();
    }

    public int undoDepth(String token) {
        return sessions.sessionFor(token).undoDepth();
    }

    /** Attaches a loyalty customer by id or phone (enables member pricing + points). */
    public Bill setCustomer(String token, String customerIdOrPhone) {
        BillingSession session = sessions.sessionFor(token);
        if (customerIdOrPhone == null || customerIdOrPhone.isBlank()) {
            session.snapshot();
            session.bill().setCustomer(null);
            return session.bill();
        }
        Customer customer = loyalty.findById(customerIdOrPhone)
                .or(() -> loyalty.findByPhone(customerIdOrPhone.trim()))
                .orElseThrow(() -> new NotFoundException("Unknown customer: " + customerIdOrPhone));
        session.snapshot();
        session.bill().setCustomer(customer);
        return session.bill();
    }

    public Bill setCoupon(String token, String code) {
        BillingSession session = sessions.sessionFor(token);
        if (code != null && !code.isBlank()) {
            engine.couponAmount(code.trim(), session.bill().totals(engine).gross());
        }
        session.snapshot();
        session.bill().setCouponCode(code == null || code.isBlank() ? null : code.trim().toUpperCase(java.util.Locale.ROOT));
        return session.bill();
    }

    /** Sets the bill's charges: number of carry bags and an optional delivery fee. */
    public Bill setCharges(String token, Integer carryBags, BigDecimal deliveryFee) {
        BillingSession session = sessions.sessionFor(token);
        session.snapshot();
        if (carryBags != null) {
            session.bill().setCarryBags(Math.max(0, carryBags));
        }
        if (deliveryFee != null) {
            session.bill().setDeliveryFee(MoneyUtil.round(deliveryFee));
        }
        return session.bill();
    }

    // ---------------- tender ----------------

    /**
     * Takes the money and completes the sale: validation chain, then a command pipeline
     * (reserve stock &rarr; charge tenders &rarr; award points &rarr; persist sale) that rolls
     * back atomically on any failure. Cash tenders round the payable to a whole taka.
     *
     * @return the persisted sale, snapshot-complete for reprinting and reporting
     */
    public Sale tender(String token, String cashierUsername, List<TenderRequest> tenderRequests) {
        BillingSession session = sessions.sessionFor(token);
        synchronized (session) {
            Bill bill = session.bill();
            Bill.Totals totals = bill.totals(engine);

            // cash tends round the payable to whole taka
            BigDecimal roundOff = BigDecimal.ZERO.setScale(2);
            boolean cash = tenderRequests.stream().anyMatch(t -> "CASH".equalsIgnoreCase(t.type()));
            BigDecimal net = totals.net();
            if (cash) {
                BigDecimal whole = net.setScale(0, RoundingMode.HALF_UP);
                roundOff = whole.subtract(net);
                net = whole;
            }

            List<TenderRequest> tenders = normalizeTenders(tenderRequests);
            BillingCheck check = new BillingCheck(bill, totalsWithNet(totals, net), tenders,
                    catalog, engine);
            ValidationResult verdict = validation.validate(check);
            if (!verdict.passed()) {
                throw new IllegalArgumentException(verdict.failure());
            }

            String receiptNo = receiptNumbers.next();
            Customer customer = bill.customer();
            List<Tender> charged = new ArrayList<>();
            final BigDecimal finalNet = net;

            BillingInvoker invoker = new BillingInvoker();
            for (BillableItem item : bill.items()) {
                if (item.productId() != null) {
                    invoker.addCommand(new ReserveStockCommand(inventory, item.productId(), item.quantity()));
                }
            }
            for (TenderRequest request : tenders) {
                PaymentChannel channel = channelFor(TenderType.valueOf(request.type().toUpperCase()),
                        () -> customer);
                invoker.addCommand(new ChargeTenderCommand(channel, request.amount(), receiptNo, charged::add));
            }
            int earnedPoints = customer == null ? 0 : loyalty.pointsFor(net);
            if (customer != null) {
                invoker.addCommand(new AwardPointsCommand(loyalty, customer.getId(), earnedPoints));
            }
            List<SaleLine> snapshot = snapshotLines(bill, totals, roundOff, finalNet);
            Sale.Totals saleTotals = new Sale.Totals(
                    totals.gross(), totals.lineDiscount(), totals.coupon(),
                    totals.fees(), roundOff, finalNet, totals.vat(),
                    charged.stream().map(Tender::amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                            .setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2));
            LocalDateTime at = TimeSource.now();
            CreateSaleCommand create = new CreateSaleCommand(sales,
                    () -> finishSale(receiptNo, at, cashierUsername, customer, snapshot, saleTotals, charged));
            invoker.addCommand(create);
            invoker.run();

            session.resetAfterSale();
            return create.created();
        }
    }

    private Sale finishSale(String receiptNo, LocalDateTime at, String cashierUsername,
                            Customer customer, List<SaleLine> snapshot,
                            Sale.Totals totals, List<Tender> charged) {
        BigDecimal tendered = BigDecimal.ZERO;
        for (Tender t : charged) {
            tendered = tendered.add(t.amount());
        }
        Sale.Totals finalTotals = new Sale.Totals(totals.gross(), totals.discount(), totals.coupon(),
                totals.fees(), totals.roundOff(), totals.net(), totals.vat(),
                tendered, tendered.subtract(totals.net()).max(BigDecimal.ZERO).setScale(2));
        return new Sale(receiptNo, at, cashierUsername, customer == null ? null : customer.getId(),
                snapshot, finalTotals, charged);
    }

    // ---------------- helpers ----------------

    private BillableItem lineFor(Product product, Integer quantity, BigDecimal weightKg) {
        if (product instanceof UnitProduct unit) {
            int qty = quantity == null ? 1 : quantity;
            return new UnitLine(unit, qty);
        }
        if (product instanceof WeighedProduct weighed) {
            if (weightKg == null && quantity != null) {
                // quick-keys send pieces for weighed goods too — treat them as kg
                return new WeighedLine(weighed, BigDecimal.valueOf(quantity));
            }
            return new WeighedLine(weighed, weightKg == null ? BigDecimal.ONE : weightKg);
        }
        if (product instanceof ComboProduct combo) {
            return new ComboLine(combo, quantity == null ? 1 : quantity);
        }
        throw new IllegalArgumentException("Cannot bill product type: " + product.getType());
    }

    private List<TenderRequest> normalizeTenders(List<TenderRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("At least one tender is required");
        }
        List<TenderRequest> normalized = new ArrayList<>();
        for (TenderRequest request : requests) {
            if (request.type() == null || request.type().isBlank()) {
                throw new IllegalArgumentException("Every tender needs a type (CASH, CARD, BKASH, NAGAD, POINTS)");
            }
            BigDecimal amount = MoneyUtil.round(MoneyUtil.requirePositive(request.amount(), "Tender amount"));
            normalized.add(new TenderRequest(request.type().toUpperCase(), amount, request.reference()));
        }
        return normalized;
    }

    private Bill.Totals totalsWithNet(Bill.Totals totals, BigDecimal net) {
        return new Bill.Totals(totals.pricedLines(), totals.gross(), totals.lineDiscount(),
                totals.coupon(), totals.fees(), net, totals.vat());
    }

    private PaymentChannel channelFor(TenderType type, java.util.function.Supplier<Customer> customer) {
        if (type == TenderType.POINTS) {
            return new PointsAdapter(loyalty, customer);
        }
        PaymentChannel channel = baseChannels.get(type);
        if (channel == null) {
            throw new IllegalArgumentException("Unsupported tender type: " + type);
        }
        return channel;
    }

    /** Builds the persisted line snapshots from raw+priced pairs, plus fee/round-off lines. */
    private List<SaleLine> snapshotLines(Bill bill, Bill.Totals totals, BigDecimal roundOff,
                                         BigDecimal finalNet) {
        List<BillableItem> raw = bill.items();
        List<BillableItem> priced = totals.pricedLines();
        List<SaleLine> lines = new ArrayList<>();
        int lineNo = 1;
        for (int i = 0; i < raw.size(); i++) {
            lines.add(snapshotLine(lineNo++, raw.get(i), priced.get(i)));
        }
        BigDecimal bagFee = bill.carryBagUnitFee().multiply(BigDecimal.valueOf(bill.carryBags()));
        if (bagFee.signum() > 0) {
            lines.add(adjustmentLine(lineNo++, "Carry Bag x" + bill.carryBags(), bagFee, "CARRY_BAG_FEE", bagFee));
        }
        if (bill.deliveryFee().signum() > 0) {
            lines.add(adjustmentLine(lineNo++, "Home Delivery", bill.deliveryFee(), "DELIVERY_FEE", bill.deliveryFee()));
        }
        if (roundOff.signum() != 0) {
            lines.add(adjustmentLine(lineNo++, "Round Off", roundOff, "ROUND_OFF", roundOff));
        }
        return lines;
    }

    private SaleLine snapshotLine(int lineNo, BillableItem raw, BillableItem priced) {
        String kind = raw instanceof UnitLine ? "UNIT"
                : raw instanceof WeighedLine ? "WEIGHED"
                : raw instanceof ComboLine ? "COMBO"
                : "ADJUSTMENT";
        BigDecimal gross = raw.lineNet();
        BigDecimal net = priced.lineNet();
        List<SaleLine.Decoration> decorations = new ArrayList<>();
        BillableItem layer = priced;
        while (layer instanceof LineDiscount d) {
            decorations.add(new SaleLine.Decoration("LINE_DISCOUNT", d.percentOff(), d.savedAmount()));
            layer = d.inner();
        }
        return new SaleLine(
                lineNo,
                kind,
                raw.productId(),
                raw.sku(),
                raw.name(),
                raw.categoryId(),
                priced.vatRate(),
                raw.quantity(),
                raw.unitPrice(),
                gross,
                gross.subtract(net).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                net,
                VatCalculator.vatOf(net, priced.vatRate()),
                raw.unitCost(),
                decorations);
    }

    private SaleLine adjustmentLine(int lineNo, String label, BigDecimal amount, String type,
                                    BigDecimal param) {
        return new SaleLine(lineNo, "ADJUSTMENT", null, null, label, null,
                BigDecimal.ZERO, BigDecimal.ONE, amount, amount, BigDecimal.ZERO, amount,
                BigDecimal.ZERO, null,
                List.of(new SaleLine.Decoration(type, param, amount)));
    }

    /** Registers a payment channel (card/bKash/Nagad adapters are wired by AppConfig). */
    public void registerChannel(PaymentChannel channel) {
        baseChannels.put(channel.type(), channel);
    }

    public BigDecimal carryBagUnitFee() {
        return carryBagUnitFee;
    }
}
