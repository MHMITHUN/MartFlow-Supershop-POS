package com.martflow.persistence;

import com.martflow.auth.User;
import com.martflow.auth.UserRepository;
import com.martflow.catalog.Product;
import com.martflow.loyalty.Customer;
import com.martflow.loyalty.CustomerRepository;
import com.martflow.pricing.Promotion;
import com.martflow.sales.Sale;

/**
 * Startup selector: tries MongoDB (Atlas) and falls back to in-memory repositories so the app
 * keeps working with zero configuration. One aggregate is added per build phase.
 *
 * <p>Plain POJO with static state — no Spring. {@code AppConfig} calls {@link #initialize()} once.
 */
public final class Repositories {

    private static Repository<Product> productRepository;
    private static UserRepository userRepository;
    private static Repository<Sale> saleRepository;
    private static Repository<Promotion> promotionRepository;
    private static CustomerRepository customerRepository;
    private static Repository<com.martflow.returns.SaleReturn> returnRepository;
    private static Repository<com.martflow.suppliers.Supplier> supplierRepository;
    private static Repository<com.martflow.suppliers.PurchaseOrder> purchaseOrderRepository;
    private static Repository<com.martflow.suppliers.StandingOrderTemplate> templateRepository;
    private static Repository<com.martflow.audit.AuditLog> auditLogRepository;
    private static Repository<com.martflow.reports.DayClose> dayCloseRepository;
    private static boolean initialized = false;

    private Repositories() {
    }

    /** Idempotent: bean creation order must not matter, so any bean may trigger bootstrap. */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        boolean mongoAvailable = DatabaseConnection.initialize();
        if (mongoAvailable) {
            productRepository = new MongoProductRepository();
            userRepository = new MongoUserRepository();
            saleRepository = new MongoSaleRepository();
            promotionRepository = new MongoRepository<>("promotions", new PromotionMapper());
            customerRepository = new MongoCustomerRepository();
            returnRepository = new MongoRepository<>("returns", new ReturnMapper());
            supplierRepository = new MongoRepository<>("suppliers", new SupplierMapper());
            purchaseOrderRepository = new MongoRepository<>("purchaseOrders", new PurchaseOrderMapper());
            templateRepository = new MongoRepository<>("orderTemplates", new TemplateMapper());
            auditLogRepository = new MongoRepository<>("auditLogs", new AuditLogMapper());
            dayCloseRepository = new MongoRepository<>("dayCloses", new DayCloseMapper());
        } else {
            productRepository = new InMemoryProductRepository();
            userRepository = new InMemoryUserRepository();
            saleRepository = new InMemorySaleRepository();
            promotionRepository = new InMemoryPromotionRepository();
            customerRepository = new InMemoryCustomerRepository();
            returnRepository = new InMemoryReturnRepository();
            supplierRepository = new InMemorySupplierRepository();
            purchaseOrderRepository = new InMemoryPurchaseOrderRepository();
            templateRepository = new InMemoryTemplateRepository();
            auditLogRepository = new InMemoryAuditLogRepository();
            dayCloseRepository = new InMemoryDayCloseRepository();
        }
        initialized = true;
    }

    public static Repository<Product> products() {
        return productRepository;
    }

    public static UserRepository users() {
        return userRepository;
    }

    public static Repository<Sale> sales() {
        return saleRepository;
    }

    public static Repository<Promotion> promotions() {
        return promotionRepository;
    }

    public static CustomerRepository customers() {
        return customerRepository;
    }

    public static Repository<com.martflow.returns.SaleReturn> returns() {
        return returnRepository;
    }

    public static Repository<com.martflow.suppliers.Supplier> suppliers() {
        return supplierRepository;
    }

    public static Repository<com.martflow.suppliers.PurchaseOrder> purchaseOrders() {
        return purchaseOrderRepository;
    }

    public static Repository<com.martflow.suppliers.StandingOrderTemplate> orderTemplates() {
        return templateRepository;
    }

    public static Repository<com.martflow.audit.AuditLog> auditLogs() {
        return auditLogRepository;
    }

    public static Repository<com.martflow.reports.DayClose> dayCloses() {
        return dayCloseRepository;
    }

    public static boolean isMongo() {
        return DatabaseConnection.isAvailable();
    }
}
