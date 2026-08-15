package com.martflow.dev;

import java.util.List;

/**
 * Server-owned catalog of the 18 GoF patterns implemented across MartFlow and served to the
 * Developer Mode console. This class is the single source of truth for pattern metadata: for each
 * pattern it declares the business problem, why the pattern fits, the rejected alternative, the
 * real classes, a snippet copied from the real source, the test class that exercises it and where
 * to see it live in the UI — so the frontend can never drift from what the code actually does.
 * {@code PatternCatalogTest} guards this file with drift checks: every snippet must stay within
 * its line budget, mention one of its own class names, and point at files and tests that really
 * exist in the repository.
 */
public final class PatternCatalog {

    private PatternCatalog() {
    }

    public record Snippet(String file, String code) {
    }

    public record Live(String href, String label, String minRole) {
    }

    public record Pattern(String id, String name, String category, String icon,
                          String problem, String whyThisPattern, String alternative,
                          List<String> classes, Snippet snippet, String testClass, Live live) {
    }

    public static List<Pattern> all() {
        return List.of(

                // ---- Creational ---------------------------------------------------------------

                // Singleton — one stock truth behind every screen
                new Pattern(
                        "singleton", "Singleton", "Creational", "bi-record-circle",
                        "A supershop runs several tills, dashboards and reports at once; if every screen built its own product list, stock counts would disagree and two cashiers could sell the same last bottle.",
                        "One lazily-created, thread-safe instance (volatile field plus synchronized initialization) guarantees that every screen prices and decrements the very same shelf truth.",
                        "Passing the catalog through every constructor couples dozens of classes to wiring order and still invites someone to new up a second copy; the singleton makes duplication structurally impossible.",
                        List.of("InventoryCatalog", "DatabaseConnection"),
                        new Snippet("src/main/java/com/martflow/catalog/InventoryCatalog.java",
                                """
                                public class InventoryCatalog {

                                    private static volatile InventoryCatalog instance;

                                    private final Map<String, Product> productsById = new LinkedHashMap<>();

                                    private InventoryCatalog(Repository<Product> repository) {
                                        this.repository = repository;
                                        reload();
                                    }

                                    /** Initializes the single catalog with the chosen repository. Called once at startup. */
                                    public static InventoryCatalog initialize(Repository<Product> repository) {
                                        synchronized (InventoryCatalog.class) {
                                            if (instance == null) {
                                                instance = new InventoryCatalog(repository);
                                            }
                                            return instance;
                                        }
                                    }

                                    public static InventoryCatalog getInstance() { /* throws if not initialized */ }
                                }"""),
                        "InventoryCatalogTest",
                        new Live("#/inventory", "Browse the shelf — one catalog instance backs every till, alert and report.", "CASHIER")),

                // Factory Method — one add-item form, two product worlds
                new Pattern(
                        "factory-method", "Factory Method", "Creational", "bi-gear-wide-connected",
                        "The manager's add-item form must create per-piece MRP goods (oil, soap) and per-kilo weighed goods (rice, fish) from one screen, with the same validation but different concrete types.",
                        "The factory template validates the shared input while each subclass decides which Product materializes, so a new item kind plugs in without touching the form.",
                        "A type-string if/else inside one creator grows with every product kind and scatters validation; subclasses keep the fork sealed and independently testable.",
                        List.of("ProductFactory", "UnitProductFactory", "WeighedProductFactory"),
                        new Snippet("src/main/java/com/martflow/catalog/ProductFactory.java",
                                """
                                public abstract class ProductFactory {

                                    /** Template: validates the shared input, then defers to the subclass creation hook. */
                                    public final Product create(String id, ProductInput in) {
                                        if (id == null || id.isBlank()) {
                                            throw new IllegalArgumentException("Product id is required");
                                        }
                                        MoneyUtil.requirePositive(in.price(), "Price for " + in.name());
                                        BigDecimal stock = in.stock() == null ? BigDecimal.ZERO : in.stock();
                                        if (stock.signum() < 0) {
                                            throw new IllegalArgumentException("Stock cannot be negative");
                                        }
                                        return createProduct(id, in);
                                    }

                                    /** The creation hook subclasses implement. */
                                    protected abstract Product createProduct(String id, ProductInput in);

                                    /** The type discriminator this factory produces: "UNIT" or "WEIGHED". */
                                    public abstract String supportedType();
                                }"""),
                        "InventoryCatalogTest",
                        new Live("#/inventory", "As manager, add an item — the UNIT/WEIGHED choice routes to the matching factory.", "CASHIER")),

                // Builder — a PO assembled line by line, valid only when finished
                new Pattern(
                        "builder", "Builder", "Creational", "bi-hammer",
                        "A purchase order needs a supplier, many lines with catalog snapshots and unit costs, and a unique PO number — assembling all of that in one constructor call invites half-configured orders.",
                        "The builder validates each line as it is added and only mints the PO (with its generated PO-yyyyMMdd-NNN number) on build(), so nothing incomplete escapes into the system.",
                        "A telescoping constructor over parallel line arrays forces callers to keep the arrays in sync and defers every validation error to crash-at-tender time.",
                        List.of("PurchaseOrderBuilder"),
                        new Snippet("src/main/java/com/martflow/suppliers/PurchaseOrderBuilder.java",
                                """
                                public class PurchaseOrderBuilder {

                                    /** Adds a line for a catalog product with an explicit unit cost. */
                                    public PurchaseOrderBuilder line(String productId, BigDecimal quantity, BigDecimal unitCost) {
                                        Product product = InventoryCatalog.getInstance().findById(productId)
                                                .orElseThrow(() -> new IllegalArgumentException("Unknown product: " + productId));
                                        MoneyUtil.requirePositive(quantity, "Order quantity");
                                        MoneyUtil.requirePositive(unitCost == null ? product.getCostPrice() : unitCost,
                                                "Unit cost for " + product.getName());
                                        lines.add(new PurchaseOrderLine(productId, product.getSku(), product.getName(),
                                                quantity, BigDecimal.ZERO,
                                                unitCost == null ? product.getCostPrice() : MoneyUtil.round(unitCost)));
                                        return this;
                                    }

                                    public PurchaseOrder build() {
                                        if (lines.isEmpty()) {
                                            throw new IllegalArgumentException("A purchase order needs at least one line");
                                        }
                                        return new PurchaseOrder(nextPoNo(), supplierId, lines); // PO-yyyyMMdd-NNN
                                    }
                                }"""),
                        "PurchasingFlowTest",
                        new Live("#/purchases", "Create a draft PO line by line — the builder stamps the PO number when you finish.", "MANAGER")),

                // Prototype — the weekly restock list cloned, not retyped
                new Pattern(
                        "prototype", "Prototype", "Creational", "bi-copy",
                        "Every Monday the shop orders the same 24 crates of water and 60 packs of snacks — retyping that order each week wastes the manager's morning and invites a missed line.",
                        "A named template clones into a fresh DRAFT on demand, and clone independence means receipts against one order never contaminate the template or its sibling clones.",
                        "Copy-pasting a previous order hides the template inside history and drifts as soon as an old order is amended or cancelled.",
                        List.of("StandingOrderTemplate"),
                        new Snippet("src/main/java/com/martflow/suppliers/StandingOrderTemplate.java",
                                """
                                public class StandingOrderTemplate {

                                    /** One template line: product reference + standing quantity (unit cost read at order time). */
                                    public record TemplateLine(String productId, String name, BigDecimal quantity) {
                                    }

                                    /** Clones this template into a fresh DRAFT purchase order (the Prototype payoff). */
                                    public PurchaseOrder instantiate(PurchaseOrderBuilder builder) {
                                        for (TemplateLine line : lines) {
                                            builder.line(line.productId(), line.quantity(), null);
                                        }
                                        return builder.build();
                                    }

                                    public List<TemplateLine> getLines() {
                                        return List.copyOf(lines); // callers can never mutate the template's lines
                                    }
                                }"""),
                        "PurchasingFlowTest",
                        new Live("#/purchases", "On the standing templates tab, clone a weekly restock template into a fresh draft.", "MANAGER")),

                // ---- Structural -----------------------------------------------------------------

                // Adapter — five vendor dialects behind one money port
                new Pattern(
                        "adapter", "Adapter", "Structural", "bi-plug",
                        "Cash is trivial, but the card terminal speaks doubles and int codes, bKash wants poisha, Nagad answers in strings — the tender pipeline cannot learn five vendor dialects.",
                        "Each vendor SDK hides behind the PaymentChannel port (charge/refund in BigDecimal), with money rounded before any double conversion so no paisa vanishes in translation.",
                        "If/else on tender type inside billing spreads vendor quirks through checkout code and couples every new wallet to a release of the app.",
                        List.of("PaymentChannel", "CashAdapter", "CardAdapter", "BkashAdapter", "NagadAdapter", "PointsAdapter"),
                        new Snippet("src/main/java/com/martflow/payment/CardAdapter.java",
                                """
                                public final class CardAdapter implements PaymentChannel {

                                    private final CardTerminalApi terminal = new CardTerminalApi();

                                    @Override
                                    public TenderType type() {
                                        return TenderType.CARD;
                                    }

                                    @Override
                                    public PaymentResult charge(BigDecimal amount, String reference) {
                                        double taka = amount.setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
                                        int auth = terminal.authorizePayment(taka, reference);
                                        if (auth < 0) {
                                            return PaymentResult.failed("Card declined by terminal");
                                        }
                                        return PaymentResult.ok("CARD-" + auth, "Authorized at terminal");
                                    }

                                    @Override
                                    public PaymentResult refund(BigDecimal amount, String reference) { /* voids the CARD-auth */ }
                                }"""),
                        "PaymentChannelTest",
                        new Live("#/pos", "Split the tender — pay part cash, part card; each leg goes through its own adapter.", "CASHIER")),

                // Decorator — discounts and fees wrap the line, never rewrite it
                new Pattern(
                        "decorator", "Decorator", "Structural", "bi-layers",
                        "A line's price is never just MRP: category-sale discounts, member prices, carry-bag fees and the till's round-off all modify the same line, in combinations chosen at runtime.",
                        "Wrappers implement the same BillableItem interface and override only what changes, so pricing layers stack in any order while the receipt still reads every layer.",
                        "A flag-filled super-line (discountPercent, bagFee, isMember...) turns every pricing combination into a new if-branch and breaks the VAT math quietly.",
                        List.of("LineDecorator", "LineDiscount", "CarryBagFee"),
                        new Snippet("src/main/java/com/martflow/billing/decorator/LineDiscount.java",
                                """
                                public final class LineDiscount extends LineDecorator {

                                    private final BigDecimal percentOff;

                                    public LineDiscount(BillableItem inner, BigDecimal percentOff) {
                                        super(inner); // rejects percentOff outside (0, 100)
                                        this.percentOff = percentOff;
                                    }

                                    @Override
                                    public BigDecimal lineNet() {
                                        BigDecimal factor = BigDecimal.ONE.subtract(
                                                percentOff.divide(new BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP));
                                        return MoneyUtil.round(inner.lineNet().multiply(factor));
                                    }

                                    @Override
                                    public String describe() {
                                        return inner.describe() + " (-" + percentOff.stripTrailingZeros().toPlainString() + "%)";
                                    }
                                }"""),
                        "BillPricingTest",
                        new Live("#/pos", "Add a member card and carry bags — discount and fee layers wrap the scanned lines.", "CASHIER")),

                // Facade — one call behind every POS button
                new Pattern(
                        "facade", "Facade", "Structural", "bi-building",
                        "Tendering one bill touches sessions, pricing, stock, five payment channels, loyalty and persistence — controllers cannot be expected to orchestrate that ordering correctly.",
                        "BillingFacade exposes one-call operations (scan, undo, setCustomer, tender) and hides the validation chain and command pipeline behind them, keyed by till token.",
                        "Controllers wiring services directly would duplicate the transaction ordering in every endpoint, and the first new controller would forget the undo snapshot.",
                        List.of("BillingFacade", "MartFlowFacade"),
                        new Snippet("src/main/java/com/martflow/billing/BillingFacade.java",
                                """
                                /** One-call operations behind every POS button; controllers stay dumb. */
                                public class BillingFacade {

                                    private final BillingSessionRegistry sessions;
                                    private final InventoryCatalog catalog;
                                    private final PromotionEngine engine;
                                    private final LoyaltyService loyalty;
                                    // ... plus inventory, sales repository, receipt numbers, validation chain, channels

                                    /** Scans an item by id or barcode. quantity is pieces; weightKg is kg. */
                                    public Bill addLine(String token, String productIdOrBarcode, Integer quantity, BigDecimal weightKg) {
                                        BillingSession session = sessions.sessionFor(token);
                                        Product product = catalog.findById(productIdOrBarcode)
                                                .or(() -> catalog.findByBarcode(productIdOrBarcode))
                                                .orElseThrow(() -> new NotFoundException("Unknown item: " + productIdOrBarcode));
                                        BillableItem line = lineFor(product, quantity, weightKg);
                                        session.snapshot(); // Memento before every destructive edit
                                        session.bill().addItem(line);
                                        return session.bill();
                                    }
                                }"""),
                        "BillingApiTest",
                        new Live("#/pos", "Scan, edit, undo, tender — every POS button is one facade call.", "CASHIER")),

                // Proxy — role checks at the data boundary
                new Pattern(
                        "proxy", "Proxy (Protection)", "Structural", "bi-shield-lock",
                        "Cashiers legitimately move stock with every sale, but assortment changes are a buying decision and deleting an item is owner-only — and the rule must hold even if a controller is buggy.",
                        "RoleGuardProxy wraps the repository so every save and delete passes a role check at the data boundary while reads stay transparent — the last line of defense.",
                        "Checking roles in each controller duplicates the permission matrix per endpoint, and one missed check leaks a write straight to the database.",
                        List.of("RoleGuardProxy", "ProductWritePolicy"),
                        new Snippet("src/main/java/com/martflow/persistence/proxy/RoleGuardProxy.java",
                                """
                                /** Sits in front of a Repository and enforces who may write what. */
                                public final class RoleGuardProxy<T> implements Repository<T> {

                                    @Override
                                    public Optional<T> findById(String id) {
                                        return delegate.findById(id); // reads are transparent — that is the proxy point
                                    }

                                    @Override
                                    public T save(T entity) {
                                        T existing = delegate.findById(idOf.apply(entity)).orElse(null);
                                        policy.checkSave(existing, entity); // throws AccessDeniedException to block
                                        return delegate.save(entity);
                                    }

                                    @Override
                                    public void delete(String id) {
                                        policy.checkDelete(id); // products: ADMIN only — the delete button's backbone
                                        delegate.delete(id);
                                    }
                                }"""),
                        "RoleGuardProxyTest",
                        new Live("#/inventory", "Watch the delete button: admin-only, enforced by the proxy at the data boundary.", "MANAGER")),

                // Composite — the hamper is a product made of products
                new Pattern(
                        "composite", "Composite", "Structural", "bi-boxes",
                        "Eid hampers and breakfast combos sell as one line but draw on many components — pricing, stock and low-stock alerts must fan out to what is actually inside.",
                        "ComboProduct is a Product like any leaf, so the till bills it blind; its operations delegate to components (stock = the scarcest, consume = all-or-nothing).",
                        "Exploding combos into component lines at the till confuses the customer's receipt and lets a short component half-sell a hamper.",
                        List.of("ComboProduct", "ComboLine"),
                        new Snippet("src/main/java/com/martflow/catalog/ComboProduct.java",
                                """
                                public class ComboProduct extends Product {

                                    private final List<Product> components; // restocking happens on components, never the combo

                                    /** Combo stock is the scarcest component — how many complete combos exist. */
                                    @Override
                                    public BigDecimal getStock() {
                                        BigDecimal min = null;
                                        for (Product component : components) {
                                            if (min == null || component.getStock().compareTo(min) < 0) {
                                                min = component.getStock();
                                            }
                                        }
                                        return min == null ? BigDecimal.ZERO : min;
                                    }

                                    @Override
                                    public synchronized void consume(java.math.BigDecimal quantity) {
                                        // Pre-check every component first so a short one cannot leave the others consumed.
                                        for (Product component : components) { /* throws IllegalStateException if short */ }
                                        for (Product component : components) {
                                            component.consume(quantity); // fan-out: each component fires its own low-stock event
                                        }
                                    }
                                }"""),
                        "ComboProductTest",
                        new Live("#/inventory", "Find a combo hamper — its stock is the scarcest component inside it.", "CASHIER")),

                // ---- Behavioral -----------------------------------------------------------------

                // Observer — the shelf announces, the back office reacts
                new Pattern(
                        "observer", "Observer", "Behavioral", "bi-bell",
                        "Stock crosses its reorder level mid-sale; the alert center, the reorder suggester and the expiry watcher all need to know instantly, without the product knowing they exist.",
                        "Products (the subject) announce stock events to whoever subscribed, so a new reaction — say a WhatsApp notifier — is one new observer and zero product changes.",
                        "Calling the alert service directly from every stock mutation hardcodes reactions into the domain and turns each new listener into another edit point in Product.",
                        List.of("StockSubject", "Product", "AlertService", "ReorderSuggestionObserver", "ExpiryWatcher"),
                        new Snippet("src/main/java/com/martflow/catalog/Product.java",
                                """
                                /** The Subject: every product announces stock events to its subscribers. */
                                public abstract class Product implements StockSubject {

                                    private final List<StockObserver> observers = new ArrayList<>();

                                    @Override
                                    public void subscribe(StockObserver observer) {
                                        if (!observers.contains(observer)) {
                                            observers.add(observer);
                                        }
                                    }

                                    protected void notifyObservers(StockEvent event) {
                                        for (StockObserver observer : new ArrayList<>(observers)) {
                                            observer.update(event); // AlertService, ReorderSuggestionObserver, ... react here
                                        }
                                    }
                                }"""),
                        "AlertObserverTest",
                        new Live("#/alerts", "Sell an item past its reorder level and watch the alert land in this feed.", "CASHIER")),

                // Strategy — Tuesday's price depends on Tuesday's promotions
                new Pattern(
                        "strategy", "Strategy", "Behavioral", "bi-shuffle",
                        "A shampoo's price depends on context — regular MRP, an active category sale, or a member price — and a manager flips promotions mid-day, between one scan and the next.",
                        "PricingStrategy implementations are interchangeable per line; PromotionEngine picks the winner from the promotion table fresh on every bill, so billing code never changes.",
                        "Embedding discount rules in the line classes couples pricing policy to product code and would need a redeploy for every promotion the manager toggles.",
                        List.of("PricingStrategy", "RegularPrice", "CategorySale", "MemberPrice", "PromotionEngine"),
                        new Snippet("src/main/java/com/martflow/pricing/PromotionEngine.java",
                                """
                                /** Picks the winning strategy for a line: best category sale beats the member price. */
                                public PricingStrategy strategyFor(BillableItem line, Customer customer) {
                                    LocalDate today = TimeSource.today();
                                    Optional<Promotion> bestSale = promotions.findAll().stream()
                                            .filter(p -> p.getType() == Promotion.Type.CATEGORY_SALE && p.isActiveOn(today))
                                            .filter(p -> p.getCategoryId() != null && p.getCategoryId().equals(line.categoryId()))
                                            .max(Comparator.comparing(p -> p.getPercentOff() == null ? BigDecimal.ZERO : p.getPercentOff()));
                                    if (bestSale.isPresent()) {
                                        Promotion sale = bestSale.get();
                                        return new CategorySale(sale.getCategoryId(), sale.getPercentOff(), sale.getName());
                                    }
                                    boolean member = customer != null && promotions.findAll().stream()
                                            .anyMatch(p -> p.getType() == Promotion.Type.MEMBER_PRICE && p.isActiveOn(today));
                                    if (member) {
                                        BigDecimal percent = promotions.findAll().stream()
                                                .filter(p -> p.getType() == Promotion.Type.MEMBER_PRICE && p.isActiveOn(today))
                                                .map(Promotion::getPercentOff)
                                                .filter(java.util.Objects::nonNull)
                                                .max(Comparator.naturalOrder())
                                                .orElse(new BigDecimal("5"));
                                        return new MemberPrice(percent);
                                    }
                                    return new RegularPrice();
                                }"""),
                        "BillPricingTest",
                        new Live("#/promotions", "Toggle a category sale mid-day — the next scan re-prices, the till code never changes.", "MANAGER")),

                // Command — a tender that can take itself back
                new Pattern(
                        "command", "Command", "Behavioral", "bi-terminal",
                        "A tender is four side-effecting steps — reserve stock, charge money, award points, persist the sale — and a card declined halfway through must leave the shop exactly as before.",
                        "Each step is a reversible BillingCommand; the invoker executes them in order and undoes the executed ones in reverse on any failure, so a declined card can never strand a half-sale.",
                        "try/catch rollback spaghetti in the service must hand-code the reverse of every step and silently rots each time a new step is added to the pipeline.",
                        List.of("BillingCommand", "BillingInvoker", "ReserveStockCommand", "ChargeTenderCommand",
                                "AwardPointsCommand", "CreateSaleCommand"),
                        new Snippet("src/main/java/com/martflow/billing/commands/BillingInvoker.java",
                                """
                                /** Runs an ordered list of BillingCommands as one transaction. */
                                public class BillingInvoker {

                                    private final List<BillingCommand> commands = new ArrayList<>();

                                    public void run() {
                                        List<BillingCommand> executed = new ArrayList<>();
                                        try {
                                            for (BillingCommand command : commands) {
                                                command.execute();
                                                executed.add(command);
                                            }
                                        } catch (RuntimeException failure) {
                                            rollback(executed);
                                            throw failure;
                                        }
                                    }

                                    private void rollback(List<BillingCommand> executed) {
                                        for (int i = executed.size() - 1; i >= 0; i--) { // reverse order — undo the latest first
                                            executed.get(i).undo(); // best-effort: keep undoing even if one step errors
                                        }
                                    }
                                }"""),
                        "BillingPipelineTest",
                        new Live("#/pos", "Tender with a card that declines — stock, points and sale all roll back.", "CASHIER")),

                // Template Method — the report skeleton written exactly once
                new Pattern(
                        "template-method", "Template Method", "Behavioral", "bi-file-earmark-code",
                        "Daily sales, best sellers, profit and VAT summaries all fetch a sales window, aggregate it and shape a result — and the discipline (voided receipts never count as revenue) must be identical in each.",
                        "The abstract generator fixes fetch → aggregate → build once; a new report supplies only name, headers and aggregate hooks with zero control-flow duplication.",
                        "Each report re-implementing the window filter risks one of them counting a voided receipt — exactly the kind of bug that misfiles a month's VAT.",
                        List.of("AbstractReportGenerator", "DailySalesReport", "VatSummaryReport", "ProfitReport", "BestSellersReport"),
                        new Snippet("src/main/java/com/martflow/reports/AbstractReportGenerator.java",
                                """
                                public abstract class AbstractReportGenerator {

                                    protected abstract List<String> headers();

                                    /** Turns the fetched sales into rows. */
                                    protected abstract List<List<String>> aggregate(List<Sale> sales);

                                    public final ReportResult generate(LocalDate from, LocalDate to) {
                                        LocalDate start = from == null ? TimeSource.today().minusDays(30) : from;
                                        LocalDateTime endExclusive = (to == null ? TimeSource.today() : to)
                                                .plusDays(1).atStartOfDay();
                                        List<Sale> window = new ArrayList<>();
                                        for (Sale sale : sales.findAll()) {
                                            if (sale.getStatus() == SaleStatus.VOIDED) {
                                                continue; // voided receipts are not revenue, ever
                                            }
                                            if (!sale.getAt().isBefore(start.atStartOfDay()) && sale.getAt().isBefore(endExclusive)) {
                                                window.add(sale);
                                            }
                                        }
                                        return new ReportResult(name(), headers(), aggregate(window), meta(window));
                                    }
                                }"""),
                        "ReportEngineTest",
                        new Live("#/reports", "Run the profit and VAT reports — same skeleton, different hooks per report.", "MANAGER")),

                // Chain of Responsibility — the first failing rule talks to the cashier
                new Pattern(
                        "chain-of-responsibility", "Chain of Responsibility", "Behavioral", "bi-link-45deg",
                        "Before money moves, a bill must pass ordered rules — not empty, weighments sane, stock sufficient, coupon valid, tender enough — and the cashier needs the first real reason, not a stack trace.",
                        "Independent handlers each own one rule and the chain stops at the first failure, so a new rule (say a credit limit) is a new handler, not an edit to a 200-line validator.",
                        "One mega-validate method with nested ifs mixes unrelated rules, is only testable as a blob, and buries the first-failure message the cashier actually needs.",
                        List.of("ValidationChain", "Handlers"),
                        new Snippet("src/main/java/com/martflow/billing/validation/ValidationChain.java",
                                """
                                /** Ordered billing rules; the first failing handler decides the error the cashier sees. */
                                public class ValidationChain {

                                    private final List<ValidationHandler> handlers;

                                    public ValidationChain(List<ValidationHandler> handlers) {
                                        this.handlers = List.copyOf(handlers);
                                    }

                                    public ValidationResult validate(BillingCheck check) {
                                        for (ValidationHandler handler : handlers) {
                                            ValidationResult result = handler.validate(check);
                                            if (!result.passed()) {
                                                return result;
                                            }
                                        }
                                        return ValidationResult.ok();
                                    }
                                }"""),
                        "BillingChainTest",
                        new Live("#/pos", "Tender an empty or short-paid bill — the first failing rule names the error.", "CASHIER")),

                // State — the PO lifecycle that guards itself
                new Pattern(
                        "state", "State", "Behavioral", "bi-diagram-3",
                        "A purchase order lives for days — draft, ordered, partially received, received, closed — and a draft cannot be received any more than a closed PO can be cancelled.",
                        "Each state object answers canSubmit/canReceive/canClose/canCancel, so the lifecycle rules live in six tiny classes instead of status checks scattered over the service.",
                        "Status-string if-chains in the service duplicate the permission matrix per action and make the partially-received edge cases impossible to test in isolation.",
                        List.of("PurchaseOrderState", "States"),
                        new Snippet("src/main/java/com/martflow/suppliers/postate/States.java",
                                """
                                /** The six concrete PO states (package file: they are one-liners each). */
                                final class States {

                                    static final class DraftState extends PurchaseOrderState {
                                        @Override
                                        public String name() { return "DRAFT"; }

                                        @Override
                                        public boolean canSubmit() { return true; }

                                        @Override
                                        public boolean canCancel() { return true; }
                                    }

                                    static final class ReceivedState extends PurchaseOrderState {
                                        @Override
                                        public String name() { return "RECEIVED"; }

                                        @Override
                                        public boolean canClose() { return true; } // pay and close
                                    }

                                    // ... OrderedState, PartiallyReceivedState, ClosedState, CancelledState: same shape
                                }"""),
                        "PurchaseOrderStateTest",
                        new Live("#/purchases", "Walk a PO through submit, receive and close — each state enables only legal moves.", "MANAGER")),

                // Visitor — one traversal, every report
                new Pattern(
                        "visitor", "Visitor", "Behavioral", "bi-person-walking",
                        "The same bill lines must feed different computations — VAT by NBR rate slab, profit margin, thermal-receipt text — over lines and discount wrappers alike, live or reloaded from storage.",
                        "Double dispatch through accept() lets each item announce its exact type, so one visitor interface carries every report with no instanceof chains anywhere.",
                        "Switches on line type inside each report duplicate the type list per report and break the day a new decorator layer is added to the bill.",
                        List.of("BillItemVisitor", "VatVisitor", "ProfitVisitor", "ReceiptFormatterVisitor"),
                        new Snippet("src/main/java/com/martflow/reports/visitor/VatVisitor.java",
                                """
                                /** Walks one bill's lines and back-calculates the output VAT per NBR rate slab. */
                                public final class VatVisitor implements BillItemVisitor {

                                    private final Map<String, BigDecimal> vatByRate = new LinkedHashMap<>();

                                    @Override
                                    public void visit(UnitLine line) {
                                        record(line.lineNet(), line.vatRate());
                                    }

                                    @Override
                                    public void visit(CarryBagFee carryBag) {
                                        // VAT-exempt fee — deliberately nothing to record
                                    }

                                    private void record(BigDecimal net, BigDecimal rate) {
                                        BigDecimal vat = VatCalculator.vatOf(net, rate);
                                        String key = rate.stripTrailingZeros().toPlainString();
                                        vatByRate.merge(key, vat, BigDecimal::add);
                                    }
                                }"""),
                        "ReportEngineTest",
                        new Live("#/reports", "Open the VAT summary — one walk over the lines splits output VAT by NBR slab.", "MANAGER")),

                // Memento — the Undo button earns its keep
                new Pattern(
                        "memento", "Memento", "Behavioral", "bi-arrow-counterclockwise",
                        "Cashiers mis-scan constantly — wrong item, wrong weight — and recomputing a twenty-line bill by hand during the evening rush costs real money and goodwill.",
                        "Every destructive edit snapshots the bill into an opaque BillMemento on a capped stack; Undo pops and restores it exactly, with no partial states possible.",
                        "Storing editable copies (or JSON blobs) of the bill lets code mutate history; opacity guarantees a snapshot can only be restored, never read or forged.",
                        List.of("BillMemento", "BillingSession"),
                        new Snippet("src/main/java/com/martflow/billing/BillMemento.java",
                                """
                                /** An opaque snapshot of a bill taken before every destructive edit. */
                                public final class BillMemento {

                                    private final List<BillableItem> items;
                                    private final Customer customer;
                                    private final String couponCode;
                                    // ... carry bags, unit fee, delivery fee — the full bill state

                                    static BillMemento of(Bill bill) {
                                        return new BillMemento(bill.items(), bill.customer(), bill.couponCode(),
                                                bill.carryBags(), bill.carryBagUnitFee(), bill.deliveryFee());
                                    }

                                    void restoreInto(Bill bill) {
                                        bill.clear();
                                        for (BillableItem item : items) {
                                            bill.addItem(item);
                                        }
                                        bill.setCustomer(customer);
                                        bill.setCouponCode(couponCode);
                                        // ... rest of the state
                                    }
                                }"""),
                        "BillMementoTest",
                        new Live("#/pos", "Remove a line by mistake, hit Undo — the bill snaps back to the snapshot.", "CASHIER")),

                // Iterator — the same shelf, three named walks
                new Pattern(
                        "iterator", "Iterator", "Behavioral", "bi-list-ol",
                        "Purchasing needs the low-stock walk, wastage control needs the expiring-batches walk, and the till needs in-stock — the same shelf answering three different questions.",
                        "Each view is a named, re-iterable cursor (hasNext/next/reset) over the catalog, so a filtered walk can be replayed without rebuilding it.",
                        "Inline stream predicates at each call site duplicate the query logic everywhere and cannot be reset; a named iterator is a testable, reusable object.",
                        List.of("ProductIterator", "AbstractProductIterator", "InStockIterator",
                                "LowStockIterator", "ExpiringSoonIterator"),
                        new Snippet("src/main/java/com/martflow/catalog/iter/AbstractProductIterator.java",
                                """
                                abstract class AbstractProductIterator implements ProductIterator {

                                    protected final List<Product> source;
                                    protected int cursor = 0;

                                    /** InStockIterator, LowStockIterator, ExpiringSoonIterator implement just this. */
                                    protected abstract boolean matches(Product product);

                                    @Override
                                    public boolean hasNext() {
                                        while (cursor < source.size() && !matches(source.get(cursor))) {
                                            cursor++;
                                        }
                                        return cursor < source.size();
                                    }

                                    @Override
                                    public Product next() {
                                        if (!hasNext()) { throw new NoSuchElementException(); }
                                        return source.get(cursor++);
                                    }

                                    @Override
                                    public void reset() {
                                        cursor = 0; // the same filtered view can be walked again
                                    }
                                }"""),
                        "InventoryIteratorTest",
                        new Live("#/inventory", "Switch the view dropdown to low stock or expiring — each view is a named iterator walk.", "CASHIER"))
        );
    }
}
