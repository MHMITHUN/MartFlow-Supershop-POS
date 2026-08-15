package com.martflow.app;

import com.martflow.auth.AuthService;
import com.martflow.auth.PasswordHasher;
import com.martflow.auth.TokenStore;
import com.martflow.billing.BillingFacade;
import com.martflow.billing.BillingSessionRegistry;
import com.martflow.catalog.InventoryCatalog;
import com.martflow.catalog.Product;
import com.martflow.catalog.ProductFactory;
import com.martflow.catalog.ProductWritePolicy;
import com.martflow.catalog.UnitProductFactory;
import com.martflow.catalog.WeighedProductFactory;
import com.martflow.inventory.AlertService;
import com.martflow.inventory.InventoryService;
import com.martflow.inventory.ReorderSuggestionObserver;
import com.martflow.inventory.StockObserver;
import com.martflow.loyalty.CustomerRepository;
import com.martflow.loyalty.LoyaltyService;
import com.martflow.payment.BkashAdapter;
import com.martflow.payment.CardAdapter;
import com.martflow.payment.NagadAdapter;
import com.martflow.pricing.PromotionEngine;
import com.martflow.sales.ReceiptNoGenerator;
import com.martflow.sales.Sale;
import com.martflow.persistence.Repositories;
import com.martflow.persistence.Repository;
import com.martflow.persistence.proxy.RoleGuardProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The ONLY Spring {@code @Configuration}. It builds the entire object graph as plain POJOs and
 * exposes the facades/services as beans for the controllers. Every pattern class is created
 * here without framework annotations — that is the layer-separation rule.
 */
@Configuration
public class AppConfig {

    @Bean
    public TokenStore tokenStore() {
        return new TokenStore();
    }

    @Bean
    public AuthService authService(TokenStore tokens) {
        Repositories.initialize(); // idempotent; bean order must not matter
        PasswordHasher hasher = new PasswordHasher();
        SeedData.seedUsersIfEmpty(Repositories.users(), hasher);
        return new AuthService(Repositories.users(), hasher, tokens);
    }

    @Bean
    public MartFlowFacade martFlowFacade() {
        Repositories.initialize();
        Repository<Product> rawRepo = Repositories.products();
        SeedData.seedIfEmpty(rawRepo);

        // protection Proxy over the catalog repository (create=MANAGER+, delete=ADMIN)
        RoleGuardProxy<Product> guardedRepo =
                new RoleGuardProxy<>(rawRepo, Product::getId, new ProductWritePolicy());
        InventoryCatalog catalog = InventoryCatalog.initialize(guardedRepo);

        AlertService alerts = new AlertService(200);
        ReorderSuggestionObserver reorderSuggestions = new ReorderSuggestionObserver(alerts);
        for (Product product : catalog.getAll()) {
            product.subscribe(alerts);
            product.subscribe(reorderSuggestions);
        }
        InventoryService inventoryService = new InventoryService(catalog);

        // expiry watch: raise alerts for batches inside the 14-day window on every boot
        new com.martflow.inventory.ExpiryWatcher(catalog).watch(14);

        Map<String, ProductFactory> factories = new LinkedHashMap<>();
        factories.put("UNIT", new UnitProductFactory());
        factories.put("WEIGHED", new WeighedProductFactory());

        List<StockObserver> watchers = List.of(alerts, reorderSuggestions);
        return new MartFlowFacade(catalog, inventoryService, alerts, factories, watchers);
    }

    @Bean
    public LoyaltyService loyaltyService() {
        Repositories.initialize();
        CustomerRepository customers = Repositories.customers();
        LoyaltyService loyalty = new LoyaltyService(customers);
        SeedData.seedCustomersIfEmpty(loyalty);
        return loyalty;
    }

    @Bean
    public PromotionEngine promotionEngine() {
        Repositories.initialize();
        PromotionEngine engine = new PromotionEngine(Repositories.promotions());
        SeedData.seedPromotionsIfEmpty(Repositories.promotions());
        return engine;
    }

    @Bean
    public BillingFacade billingFacade(MartFlowFacade shop, LoyaltyService loyalty,
                                       PromotionEngine promotionEngine) {
        Repositories.initialize();
        BillingSessionRegistry sessions = new BillingSessionRegistry(System::currentTimeMillis);
        Repository<Sale> sales = Repositories.sales();
        BillingFacade billing = new BillingFacade(sessions, shop.catalog(), shop.inventory(),
                promotionEngine, loyalty, sales, new ReceiptNoGenerator(sales), new BigDecimal("5.00"));
        billing.registerChannel(new CardAdapter());
        billing.registerChannel(new BkashAdapter());
        billing.registerChannel(new NagadAdapter());
        return billing;
    }

    @Bean
    public com.martflow.sales.SalesAdminService salesAdminService(MartFlowFacade shop,
                                                                  LoyaltyService loyalty,
                                                                  BillingFacade billing) {
        Repositories.initialize();
        java.util.Map<com.martflow.payment.TenderType, com.martflow.payment.PaymentChannel> channels =
                new java.util.EnumMap<>(com.martflow.payment.TenderType.class);
        channels.put(com.martflow.payment.TenderType.CASH, new com.martflow.payment.CashAdapter());
        channels.put(com.martflow.payment.TenderType.CARD, new CardAdapter());
        channels.put(com.martflow.payment.TenderType.BKASH, new BkashAdapter());
        channels.put(com.martflow.payment.TenderType.NAGAD, new NagadAdapter());
        return new com.martflow.sales.SalesAdminService(Repositories.sales(), shop.inventory(),
                loyalty, channels);
    }

    @Bean
    public com.martflow.returns.ReturnService returnService(MartFlowFacade shop) {
        Repositories.initialize();
        return new com.martflow.returns.ReturnService(Repositories.sales(),
                Repositories.returns(), shop.inventory());
    }

    @Bean
    public com.martflow.audit.AuditService auditService() {
        Repositories.initialize();
        return new com.martflow.audit.AuditService(Repositories.auditLogs());
    }

    @Bean
    public com.martflow.reports.DayCloseService dayCloseService(
            com.martflow.audit.AuditService audit) {
        Repositories.initialize();
        return new com.martflow.reports.DayCloseService(Repositories.sales(), Repositories.returns(),
                Repositories.dayCloses(), audit);
    }

    @Bean
    public com.martflow.suppliers.PurchasingService purchasingService(MartFlowFacade shop) {
        Repositories.initialize();
        com.martflow.suppliers.PurchasingService purchasing =
                new com.martflow.suppliers.PurchasingService(
                        Repositories.purchaseOrders(), Repositories.suppliers(),
                        Repositories.orderTemplates(), shop.inventory(),
                        new com.martflow.inventory.ExpiryWatcher(shop.catalog()));
        SeedData.seedPurchasingIfEmpty(purchasing);
        return purchasing;
    }
}
