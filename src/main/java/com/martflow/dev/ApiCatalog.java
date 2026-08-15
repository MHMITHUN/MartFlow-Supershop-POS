package com.martflow.dev;

import java.util.List;

/**
 * Server-owned inventory of every API route, served to Developer Mode's API Explorer. The
 * {@code DevModeApiTest} drift guard asserts this catalog matches Spring's actually-registered
 * {@code /api/**} mappings in both directions — a new endpoint that forgets to document itself
 * here fails the build.
 *
 * <p>{@code minRole} vocabulary: PUBLIC (no token), AUTH (any staff token), CASHIER / MANAGER /
 * ADMIN (privilege ladder), DEVELOPER (exact-match, outside the ladder).
 */
public final class ApiCatalog {

    private ApiCatalog() {
    }

    public record Endpoint(String method, String path, String minRole, String description) {
    }

    public record Group(String name, List<Endpoint> endpoints) {
    }

    public static List<Group> all() {
        return List.of(
                new Group("Auth", List.of(
                        new Endpoint("POST", "/api/auth/login", "PUBLIC", "Sign in; returns the bearer token, user and role"),
                        new Endpoint("POST", "/api/auth/logout", "AUTH", "Revoke the current token"),
                        new Endpoint("GET", "/api/auth/me", "AUTH", "Who am I — server-side identity refresh"))),
                new Group("Catalog", List.of(
                        new Endpoint("GET", "/api/categories", "AUTH", "The 9 VAT-slab categories"),
                        new Endpoint("GET", "/api/products", "AUTH", "Browse items; ?view=in_stock|low_stock|expiring picks a server-side Iterator"),
                        new Endpoint("GET", "/api/products/barcode/{code}", "AUTH", "Barcode lookup for the till scanner"),
                        new Endpoint("GET", "/api/products/{id}", "AUTH", "One item with batches"),
                        new Endpoint("POST", "/api/products", "MANAGER", "Create item (Factory Method) or combo (Composite)"),
                        new Endpoint("PUT", "/api/products/{id}", "MANAGER", "Edit name/cost/price/reorder"),
                        new Endpoint("DELETE", "/api/products/{id}", "ADMIN", "Remove from catalog (also enforced by the RoleGuardProxy)"),
                        new Endpoint("POST", "/api/products/{id}/restock", "MANAGER", "Manual restock with optional batch + expiry"),
                        new Endpoint("POST", "/api/products/{id}/adjust", "MANAGER", "Shrinkage: DAMAGE/LOSS/THEFT/COUNT"))),
                new Group("Billing (POS)", List.of(
                        new Endpoint("GET", "/api/bill", "AUTH", "The caller's in-progress bill (per-token session)"),
                        new Endpoint("POST", "/api/bill/lines", "AUTH", "Scan an item (quantity, or weightKg for weighed goods)"),
                        new Endpoint("PUT", "/api/bill/lines/{index}", "AUTH", "Change a line's quantity/weight"),
                        new Endpoint("DELETE", "/api/bill/lines/{index}", "AUTH", "Remove a line"),
                        new Endpoint("DELETE", "/api/bill", "AUTH", "Clear the bill"),
                        new Endpoint("POST", "/api/bill/undo", "AUTH", "Undo (Memento)"),
                        new Endpoint("PUT", "/api/bill/coupon", "AUTH", "Apply/remove a coupon code"),
                        new Endpoint("PUT", "/api/bill/customer", "AUTH", "Attach a loyalty member"),
                        new Endpoint("PUT", "/api/bill/charges", "AUTH", "Carry bags / delivery fee"),
                        new Endpoint("POST", "/api/bill/tender", "AUTH", "Take payment — the Command pipeline with atomic rollback"))),
                new Group("Sales", List.of(
                        new Endpoint("GET", "/api/sales", "MANAGER", "Sales history; ?from&to&status&cashier"),
                        new Endpoint("GET", "/api/sales/{receiptNo}", "AUTH", "One receipt — reprint source"),
                        new Endpoint("POST", "/api/sales/{receiptNo}/void", "MANAGER", "Full reversal: stock, tenders, points"))),
                new Group("Returns", List.of(
                        new Endpoint("POST", "/api/sales/{receiptNo}/returns", "AUTH", "Process a return/exchange refund"),
                        new Endpoint("GET", "/api/returns", "MANAGER", "Return history"))),
                new Group("Promotions", List.of(
                        new Endpoint("GET", "/api/promotions", "AUTH", "The marketing calendar"),
                        new Endpoint("POST", "/api/promotions", "MANAGER", "Create (category sale / member price / coupon)"),
                        new Endpoint("PUT", "/api/promotions/{id}", "MANAGER", "Update or toggle active"),
                        new Endpoint("DELETE", "/api/promotions/{id}", "MANAGER", "Remove"),
                        new Endpoint("POST", "/api/promotions/validate", "AUTH", "Coupon tester — what is this code worth?"))),
                new Group("Customers", List.of(
                        new Endpoint("GET", "/api/customers", "AUTH", "Search members by name/phone/card"),
                        new Endpoint("POST", "/api/customers", "AUTH", "Register a loyalty member"),
                        new Endpoint("GET", "/api/customers/{id}", "AUTH", "One member"),
                        new Endpoint("POST", "/api/customers/{id}/points/adjust", "MANAGER", "Correct a points balance"))),
                new Group("Suppliers & purchasing", List.of(
                        new Endpoint("GET", "/api/suppliers", "MANAGER", "Distributor list"),
                        new Endpoint("GET", "/api/suppliers/{id}", "MANAGER", "One supplier"),
                        new Endpoint("POST", "/api/suppliers", "MANAGER", "Register a supplier"),
                        new Endpoint("GET", "/api/purchase-orders", "MANAGER", "PO board; ?status filter"),
                        new Endpoint("GET", "/api/purchase-orders/{poNo}", "MANAGER", "One PO with payments"),
                        new Endpoint("POST", "/api/purchase-orders", "MANAGER", "Create a draft PO (Builder)"),
                        new Endpoint("POST", "/api/purchase-orders/{poNo}/submit", "MANAGER", "DRAFT → ORDERED (State)"),
                        new Endpoint("POST", "/api/purchase-orders/{poNo}/cancel", "MANAGER", "Cancel with a reason"),
                        new Endpoint("POST", "/api/purchase-orders/{poNo}/receive", "MANAGER", "GRN: batch + expiry + cost land on the shelf"),
                        new Endpoint("POST", "/api/purchase-orders/{poNo}/payments", "MANAGER", "Record a supplier payment"),
                        new Endpoint("POST", "/api/purchase-orders/{poNo}/close", "MANAGER", "RECEIVED → CLOSED"),
                        new Endpoint("POST", "/api/purchase-orders/from-template", "MANAGER", "Clone a standing template (Prototype)"),
                        new Endpoint("GET", "/api/purchase-orders/templates", "MANAGER", "Standing order templates"),
                        new Endpoint("POST", "/api/purchase-orders/templates", "MANAGER", "Save a standing template"))),
                new Group("Reports", List.of(
                        new Endpoint("GET", "/api/reports/dashboard", "MANAGER", "Today's KPI tiles"),
                        new Endpoint("GET", "/api/reports/day-close/preview", "MANAGER", "Z-report preview: the drawer math before counting"),
                        new Endpoint("POST", "/api/reports/day-close", "MANAGER", "Close the day with counted cash; server computes variance"),
                        new Endpoint("GET", "/api/reports/day-close", "MANAGER", "Past closes (Z-report history)"),
                        new Endpoint("GET", "/api/reports/{key}", "CASHIER", "8 report keys (Template Method); profit/VAT/staff/returns rows are manager-only"))),
                new Group("Alerts", List.of(
                        new Endpoint("GET", "/api/alerts", "AUTH", "The feed; ?unreadOnly=true"),
                        new Endpoint("POST", "/api/alerts/{id}/read", "AUTH", "Mark one alert read"))),
                new Group("Users", List.of(
                        new Endpoint("GET", "/api/users", "ADMIN", "Staff accounts"),
                        new Endpoint("POST", "/api/users", "ADMIN", "Create a staff account (any role incl. DEVELOPER)"),
                        new Endpoint("PUT", "/api/users/{id}", "ADMIN", "Edit / disable / reset password"))),
                new Group("Audit", List.of(
                        new Endpoint("GET", "/api/audit", "MANAGER", "Activity trail; ?from&to&actor&action&limit"))),
                new Group("Developer Mode", List.of(
                        new Endpoint("GET", "/api/dev/patterns", "DEVELOPER", "The 18-pattern catalog with real snippets"),
                        new Endpoint("GET", "/api/dev/endpoints", "DEVELOPER", "This catalog, served as data"),
                        new Endpoint("GET", "/api/dev/system", "DEVELOPER", "Diagnostics: persistence mode, counts, sessions, uptime"))));
    }
}
