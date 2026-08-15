package com.martflow.app;

import com.martflow.auth.PasswordHasher;
import com.martflow.auth.User;
import com.martflow.auth.UserRepository;
import com.martflow.catalog.Batch;
import com.martflow.catalog.ComboProduct;
import com.martflow.catalog.Product;
import com.martflow.catalog.ProductUnit;
import com.martflow.catalog.UnitProduct;
import com.martflow.catalog.WeighedProduct;
import com.martflow.common.TimeSource;
import com.martflow.persistence.Repository;
import com.martflow.security.Role;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * First-boot demo data: a realistic Bangladeshi supershop shelf — ~30 items with cost/MRP
 * margins, batches with expiry on perishables, a couple of deliberately low/out-of-stock items
 * and near-expiry batches so the alert views have something to show, plus two combos.
 *
 * <p>Writes straight to the raw repository (startup path — no request, hence no role context),
 * idempotently: seeding only happens when the products collection is empty.
 */
public final class SeedData {

    private SeedData() {
    }

    /**
     * Demo staff — the documented demo credentials (admin/admin123 as the owner,
     * manager/manager123, cashier/cashier123, developer/developer123 for Developer Mode).
     * Seeded per-username so an existing database still gains newly introduced accounts;
     * passwords exist only as PBKDF2 hashes.
     */
    public static void seedUsersIfEmpty(UserRepository users, PasswordHasher hasher) {
        seedUserIfAbsent(users, new User("u-admin", "admin", "Shop Owner",
                hasher.hash("admin123"), Role.ADMIN, true, null));
        seedUserIfAbsent(users, new User("u-manager", "manager", "Floor Manager",
                hasher.hash("manager123"), Role.MANAGER, true, null));
        seedUserIfAbsent(users, new User("u-cashier", "cashier", "Till Operator",
                hasher.hash("cashier123"), Role.CASHIER, true, null));
        seedUserIfAbsent(users, new User("u-developer", "developer", "Demo Developer",
                hasher.hash("developer123"), Role.DEVELOPER, true, null));
    }

    private static void seedUserIfAbsent(UserRepository users, User user) {
        boolean exists = users.findAll().stream()
                .anyMatch(existing -> existing.getUsername().equals(user.getUsername()));
        if (!exists) {
            users.save(user);
        }
    }

    /** First-boot promotions: an active category sale, member pricing and two coupons. */
    public static void seedPromotionsIfEmpty(Repository<com.martflow.pricing.Promotion> promotions) {
        if (!promotions.findAll().isEmpty()) {
            return;
        }
        LocalDate today = TimeSource.today();
        promotions.save(new com.martflow.pricing.Promotion(
                "prm-bev-sale", "Monsoon Beverages Sale", com.martflow.pricing.Promotion.Type.CATEGORY_SALE,
                "beverages", new BigDecimal("10"), null, null,
                today.minusDays(3), today.plusDays(25), true));
        promotions.save(new com.martflow.pricing.Promotion(
                "prm-member", "Loyalty Member Price", com.martflow.pricing.Promotion.Type.MEMBER_PRICE,
                null, new BigDecimal("5"), null, null,
                today.minusDays(30), null, true));
        promotions.save(new com.martflow.pricing.Promotion(
                "prm-save50", "SAVE50 — BDT 50 off", com.martflow.pricing.Promotion.Type.COUPON_FLAT,
                null, null, new BigDecimal("50"), "SAVE50",
                today.minusDays(3), today.plusDays(25), true));
        promotions.save(new com.martflow.pricing.Promotion(
                "prm-welcome10", "WELCOME10 — 10% off", com.martflow.pricing.Promotion.Type.COUPON_PERCENT,
                null, new BigDecimal("10"), null, "WELCOME10",
                today.minusDays(3), today.plusDays(25), true));
    }

    /** First-boot loyalty members with a little history. */
    public static void seedCustomersIfEmpty(com.martflow.loyalty.LoyaltyService loyalty) {
        if (!loyalty.all().isEmpty()) {
            return;
        }
        com.martflow.loyalty.Customer c1 = loyalty.register("Nusrat Jahan", "01711111111", "MF-CARD-1001");
        loyalty.adjust(c1.getId(), 450);
        com.martflow.loyalty.Customer c2 = loyalty.register("Jahid Hasan", "01822222222", "MF-CARD-1002");
        loyalty.adjust(c2.getId(), 150);
        com.martflow.loyalty.Customer c3 = loyalty.register("Rafiq Islam", "01933333333", "MF-CARD-1003");
        loyalty.adjust(c3.getId(), 900);
        com.martflow.loyalty.Customer c4 = loyalty.register("Shirin Akter", "01644444444", "MF-CARD-1004");
        loyalty.adjust(c4.getId(), 60);
        com.martflow.loyalty.Customer c5 = loyalty.register("Tanvir Ahmed", "01555555555", "MF-CARD-1005");
        loyalty.adjust(c5.getId(), 320);
    }

    /** First-boot purchasing data: distributors matching the catalog's supplier references,
     *  one standing order template and one PO already on its way. */
    public static void seedPurchasingIfEmpty(com.martflow.suppliers.PurchasingService purchasing) {
        if (!purchasing.suppliers().isEmpty()) {
            return;
        }
        var rawSuppliers = com.martflow.persistence.Repositories.suppliers();
        rawSuppliers.save(new com.martflow.suppliers.Supplier(
                "SUP-001", "City Foods Distribution", "02-5551201", "Kamrul Islam",
                "Net 15", "12/3 Khatunganj, Chattogram"));
        rawSuppliers.save(new com.martflow.suppliers.Supplier(
                "SUP-002", "Fresh Farm Direct", "01711-882233", "Shahin Alam",
                "Net 7", "Savar Dairy Hub, Dhaka"));
        rawSuppliers.save(new com.martflow.suppliers.Supplier(
                "SUP-003", "Pran RFL Distributor — Zone 4", "02-9664411", "Ruma Akter",
                "Net 30", "PRAN RFL Center, Tejgaon, Dhaka"));

        var templates = com.martflow.persistence.Repositories.orderTemplates();
        templates.save(new com.martflow.suppliers.StandingOrderTemplate(
                "tpl-weekly-staples", "Weekly Staples Restock", "SUP-001",
                List.of(
                        new com.martflow.suppliers.StandingOrderTemplate.TemplateLine(
                                "p01", null, new BigDecimal("12")),
                        new com.martflow.suppliers.StandingOrderTemplate.TemplateLine(
                                "p02", null, new BigDecimal("8")),
                        new com.martflow.suppliers.StandingOrderTemplate.TemplateLine(
                                "p04", null, new BigDecimal("10")),
                        new com.martflow.suppliers.StandingOrderTemplate.TemplateLine(
                                "p05", null, new BigDecimal("15")))));

        // one PO already on the truck, so the purchasing board has something to receive.
        // Bootstrap privilege: startup code acts as the manager for this one call.
        com.martflow.security.RoleContext.set(new com.martflow.security.Caller(
                "seed", "seed", com.martflow.security.Role.MANAGER));
        try {
            var po = purchasing.createDraft("SUP-003", List.of(
                    new com.martflow.suppliers.PurchasingService.LineRequest(
                            "p17", new BigDecimal("48"), new BigDecimal("10.50")),
                    new com.martflow.suppliers.PurchasingService.LineRequest(
                            "p19", new BigDecimal("60"), new BigDecimal("36.80"))));
            purchasing.submit(po.getPoNo());
        } catch (Exception seedingIssue) {
            // seeding must never block startup (e.g. catalog ids changed in a dev database)
        } finally {
            com.martflow.security.RoleContext.clear();
        }
    }

    public static void seedIfEmpty(Repository<Product> repo) {
        if (!repo.findAll().isEmpty()) {
            return;
        }
        LocalDate today = TimeSource.today();

        // ---- Staples (VAT 0%) ----
        save(repo, unit("p01", "TEER-OIL-5L", "8941234500011", "Teer Soybean Oil 5L",
                "Super refinery soybean oil, 5 litre jar", "staples", "SUP-001", "PACK",
                "810", "850", "24", 10, batch("B-OIL-2411", today.plusDays(300), "24")));
        save(repo, unit("p02", "MK-RICE-5K", "8941234500028", "Minikit Kumra Rice 5kg",
                "Premium minikit pari rice", "staples", "SUP-001", "PACK",
                "340", "380", "30", 10, null));
        save(repo, unit("p03", "FR-ATTA-2K", "8941234500035", "Fresh Super Atta 2kg",
                "Wheat flour, 2 kg pack", "staples", "SUP-001", "PACK",
                "118", "135", "40", 12, null));
        save(repo, unit("p04", "FR-SALT-1K", "8941234500042", "Fresh Iodized Salt 1kg",
                "Iodized edible salt", "staples", "SUP-001", "PACK",
                "24", "30", "50", 15, null));
        save(repo, unit("p05", "SUGAR-1K", "8941234500059", "Refined Sugar 1kg",
                "LOL refined sugar", "staples", "SUP-001", "PACK",
                "120", "135", "35", 10, null));

        // ---- Fresh (VAT 0%) — weighed + eggs ----
        save(repo, unit("p06", "EGG-DZN", "8941234500066", "Farm Eggs (Dozen)",
                "Brown farm eggs, one dozen", "fresh", "SUP-002", "PACK",
                "108", "132", "60", 20, batch("B-EGG-0112", today.plusDays(18), "60")));
        save(repo, weighed("w01", "POTATO-KG", "8941234500073", "Potato (per kg)",
                "Local potato, sold by weight", "fresh", "SUP-002",
                "28", "35", "80", 20, null));
        save(repo, weighed("w02", "ONION-KG", "8941234500080", "Onion (per kg)",
                "Local red onion, sold by weight", "fresh", "SUP-002",
                "55", "65", "45", 15, null));
        save(repo, weighed("w03", "TOMATO-KG", "8941234500097", "Tomato (per kg)",
                "Local tomato, sold by weight", "fresh", "SUP-002",
                "40", "55", "6", 10, null)); // deliberately low: LOW_STOCK demo
        save(repo, weighed("w04", "RUI-KG", "8941234500103", "Rui Fish (per kg)",
                "Fresh river rui, sold by weight", "fresh", "SUP-002",
                "260", "320", "18", 5, batch("B-FISH-0112", today.plusDays(3), "18")));
        save(repo, weighed("w05", "CHICKEN-KG", "8941234500110", "Broiler Chicken (per kg)",
                "Fresh broiler chicken, dressed", "fresh", "SUP-002",
                "175", "210", "25", 8, batch("B-CHK-0112", today.plusDays(2), "25")));

        // ---- Grocery (VAT 7.5%) ----
        save(repo, unit("p07", "RDN-HALUD-100", "8941234500127", "Radhuni Turmeric Powder 100g",
                "Pure turmeric powder", "grocery", "SUP-003", "PACK",
                "38", "45", "55", 15, null));
        save(repo, unit("p08", "RDN-MORICH-100", "8941234500134", "Radhuni Chilli Powder 100g",
                "Hot chilli powder", "grocery", "SUP-003", "PACK",
                "42", "50", "48", 15, null));
        save(repo, unit("p09", "PRAN-CHINI-1K", "8941234500141", "Pran Chinigura Rice 1kg",
                "Aromatic chinigura rice", "grocery", "SUP-003", "PACK",
                "105", "125", "26", 8, null));
        save(repo, unit("p10", "FR-GARLIC-200", "8941234500158", "Fresh Garlic Paste 200g",
                "Ready garlic paste", "grocery", "SUP-003", "PACK",
                "55", "65", "30", 10, batch("B-GAR-0112", today.plusDays(90), "30")));
        save(repo, weighed("w06", "MURI-KG", "8941234500165", "Muri (per kg)",
                "Puffed rice, sold by weight", "grocery", "SUP-003",
                "70", "85", "30", 10, null));

        // ---- Dairy (VAT 7.5%) ----
        save(repo, unit("p12", "DANO-FC-500", "8941234500172", "Dano Full Cream Milk Powder 500g",
                "Full cream milk powder", "dairy", "SUP-002", "PACK",
                "385", "430", "20", 6, batch("B-DANO-0625", today.plusDays(180), "20")));
        save(repo, unit("p13", "MILKVITA-UHT-1L", "8941234500189", "Milk Vita UHT Milk 1L",
                "UHT treated liquid milk", "dairy", "SUP-002", "PACK",
                "88", "100", "36", 12, batch("B-MV-0112", today.plusDays(40), "36")));
        save(repo, unit("p14", "PRAN-GHEE-500", "8941234500196", "Pran Ghee 500ml",
                "Pure butter ghee", "dairy", "SUP-003", "PACK",
                "465", "520", "10", 4, batch("B-GHE-0325", today.plusDays(365), "10")));

        // ---- Beverages (VAT 15%) ----
        save(repo, unit("p15", "COKE-125L", "8941234500202", "Coca-Cola 1.25L",
                "Carbonated soft drink", "beverages", "SUP-003", "PACK",
                "78", "90", "45", 15, null));
        save(repo, unit("p16", "PRAN-MANGO-1L", "8941234500219", "Pran Mango Juice 1L",
                "Mango fruit drink", "beverages", "SUP-003", "PACK",
                "82", "95", "40", 12, batch("B-MJ-0225", today.plusDays(120), "40")));
        save(repo, unit("p17", "MINER-500", "8941234500226", "Miner Life Water 500ml",
                "Packaged drinking water", "beverages", "SUP-003", "PACK",
                "12", "15", "8", 24, null)); // deliberately low: LOW_STOCK demo
        save(repo, unit("p18", "CLEMON-2L", "8941234500233", "Clemon 2L",
                "Lemon flavoured clear drink", "beverages", "SUP-003", "PACK",
                "95", "110", "25", 8, null));

        // ---- Snacks (VAT 15%) ----
        Product chanachur = unit("p19", "PRAN-CHAN-200", "8941234500240", "Pran Chanachur 200g",
                "Spicy chanachur mix", "snacks", "SUP-003", "PACK",
                "42", "50", "34", 20, batch("B-CHN-0225", today.plusDays(150), "24"));
        chanachur.addBatch(batch("B-CHN-0125", today.plusDays(10), "10")); // near-expiry batch demo
        save(repo, chanachur);
        save(repo, unit("p20", "FR-STICKS-30", "8941234500257", "Fresh Potato Sticks 30g",
                "Crispy potato snacks", "snacks", "SUP-003", "PACK",
                "14", "20", "80", 25, null));
        save(repo, unit("p21", "CADBURY-DM-40", "8941234500264", "Cadbury Dairy Milk 40g",
                "Milk chocolate bar", "snacks", "SUP-003", "PACK",
                "55", "65", "30", 10, batch("B-CDM-0125", today.plusDays(90), "30")));
        save(repo, unit("p22", "FW-CHOCO-70", "8941234500271", "Fuwang Chocolate Biscuit 70g",
                "Chocolate cream biscuit", "snacks", "SUP-003", "PACK",
                "25", "30", "0", 15, null)); // deliberately out of stock

        // ---- Toiletries (VAT 15%) ----
        save(repo, unit("p23", "LUX-100", "8941234500288", "Lux Soap Bar 100g",
                "Beauty soap bar", "toiletries", "SUP-001", "PACK",
                "58", "70", "44", 15, null));
        save(repo, unit("p24", "CLOSEUP-130", "8941234500295", "Close-up Toothpaste 130g",
                "Gel toothpaste with micro-whiteners", "toiletries", "SUP-001", "PACK",
                "130", "150", "28", 10, null));
        save(repo, unit("p25", "DETTOL-HW-200", "8941234500301", "Dettol Handwash 200ml",
                "Germ-protection liquid handwash", "toiletries", "SUP-001", "PACK",
                "148", "170", "22", 8, null));

        // ---- Personal care (VAT 15%) ----
        save(repo, unit("p26", "PARA-COCO-200", "8941234500318", "Parachute Coconut Oil 200ml",
                "100% pure coconut oil", "personal-care", "SUP-001", "PACK",
                "210", "240", "18", 6, null));
        save(repo, unit("p27", "NIVEA-SOFT-50", "8941234500325", "Nivea Soft Cream 50ml",
                "Moisturising cream", "personal-care", "SUP-001", "PACK",
                "190", "220", "12", 4, null));

        // ---- Household (VAT 15%) ----
        save(repo, unit("p28", "VIM-BAR-200", "8941234500332", "Vim Dishwash Bar 200g",
                "Lemon dishwash bar", "household", "SUP-001", "PACK",
                "32", "40", "36", 12, null));
        save(repo, unit("p29", "HARPIC-500", "8941234500349", "Harpic Toilet Cleaner 500ml",
                "Disinfectant toilet cleaner", "household", "SUP-001", "PACK",
                "178", "205", "14", 5, null));
        save(repo, unit("p30", "BASH-TISSUE", "8941234500356", "Bashundhara Tissue Box",
                "2-ply facial tissue, 100 pulls", "household", "SUP-001", "PACK",
                "85", "100", "26", 10, null));

        // ---- Combos (Composite) ----
        repo.save(new ComboProduct("c01", "COMBO-EID-01", "8941234500363", "Eid Hamper Classic",
                "Sugar + Pran Ghee + Dano + Chinigura rice gift hamper", "grocery",
                List.of(repo.findById("p05").orElseThrow(), repo.findById("p14").orElseThrow(),
                        repo.findById("p12").orElseThrow(), repo.findById("p09").orElseThrow()),
                new BigDecimal("1190")));
        repo.save(new ComboProduct("c02", "COMBO-BKFST-01", "8941234500370", "Breakfast Combo",
                "Eggs + Milk Vita + Fresh Atta breakfast bundle", "fresh",
                List.of(repo.findById("p06").orElseThrow(), repo.findById("p13").orElseThrow(),
                        repo.findById("p03").orElseThrow()),
                new BigDecimal("349")));
    }

    private static Product unit(String id, String sku, String barcode, String name, String description,
                                String categoryId, String supplierId, String unitName,
                                String cost, String mrp, String stock, int reorder, Batch batch) {
        UnitProduct product = new UnitProduct(id, sku, barcode, name, description, categoryId,
                supplierId, ProductUnit.valueOf(unitName), new BigDecimal(cost), new BigDecimal(mrp),
                new BigDecimal(stock), reorder);
        if (batch != null) {
            product.addBatch(batch);
        }
        return product;
    }

    private static Product weighed(String id, String sku, String barcode, String name, String description,
                                   String categoryId, String supplierId,
                                   String cost, String perUnit, String stock, int reorder, Batch batch) {
        WeighedProduct product = new WeighedProduct(id, sku, barcode, name, description, categoryId,
                supplierId, ProductUnit.KG, new BigDecimal(cost), new BigDecimal(perUnit),
                new BigDecimal(stock), reorder);
        if (batch != null) {
            product.addBatch(batch);
        }
        return product;
    }

    private static Batch batch(String batchNo, LocalDate expiry, String qty) {
        return new Batch(batchNo, expiry, new BigDecimal(qty));
    }

    private static void save(Repository<Product> repo, Product product) {
        repo.save(product);
    }
}
