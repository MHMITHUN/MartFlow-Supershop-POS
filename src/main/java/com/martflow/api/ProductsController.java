package com.martflow.api;

import com.martflow.api.dto.ApiMappers;
import com.martflow.api.dto.ProductDtos;
import com.martflow.api.dto.ProductDtos.ProductCreateRequest;
import com.martflow.api.dto.ProductDtos.ProductResponse;
import com.martflow.api.dto.ProductDtos.ProductUpdateRequest;
import com.martflow.api.dto.ProductDtos.RestockRequest;
import com.martflow.app.MartFlowFacade;
import com.martflow.audit.AuditLog;
import com.martflow.audit.AuditService;
import com.martflow.catalog.ProductInput;
import com.martflow.security.Role;
import com.martflow.security.RoleGate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Inventory endpoints: browsing (iterator views), barcode lookup, catalog administration and
 * manual restock. Thin — every call is a one-liner into {@link MartFlowFacade}.
 */
@RestController
@RequestMapping("/api")
public class ProductsController {

    private final MartFlowFacade shop;
    private final AuditService audit;

    public ProductsController(MartFlowFacade shop, AuditService audit) {
        this.shop = shop;
        this.audit = audit;
    }

    @GetMapping("/categories")
    public List<ProductDtos.CategoryResponse> categories() {
        return shop.categories().stream().map(ApiMappers::toResponse).toList();
    }

    /**
     * Lists items. {@code view} picks a server-side Iterator view:
     * {@code in_stock}, {@code low_stock} or {@code expiring} (with {@code days}, default 14).
     */
    @GetMapping("/products")
    public List<ProductResponse> products(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String view,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) BigDecimal maxPrice) {
        return shop.listProducts(q, categoryId, view, days, maxPrice)
                .stream().map(ApiMappers::toResponse).toList();
    }

    @GetMapping("/products/barcode/{code}")
    public ProductResponse byBarcode(@PathVariable String code) {
        return ApiMappers.toResponse(shop.findByBarcode(code));
    }

    @GetMapping("/products/{id}")
    public ProductResponse product(@PathVariable String id) {
        return ApiMappers.toResponse(shop.getProduct(id));
    }

    /** Creates an item (Factory Method) or a combo (Composite) when {@code type == COMBO}. */
    @PostMapping("/products")
    public ProductResponse create(@RequestBody ProductCreateRequest req) {
        RoleGate.requireAtLeast(Role.MANAGER);
        if (req.type() != null && "COMBO".equals(req.type().toUpperCase(Locale.ROOT))) {
            ProductResponse combo = ApiMappers.toResponse(shop.createCombo(req.sku(), req.barcode(),
                    req.name(), req.description(), req.categoryId(), req.componentIds(), req.fixedPrice()));
            audit.record(AuditLog.Action.PRODUCT_CREATED, "COMBO", combo.id(), "combo " + combo.name());
            return combo;
        }
        ProductInput input = new ProductInput(
                req.sku(), req.barcode(), req.name(), req.description(), req.categoryId(),
                req.supplierId(), parseUnit(req.unit()), req.costPrice(), req.price(),
                req.stock(), req.reorderLevel() == null ? 0 : req.reorderLevel());
        ProductResponse created = ApiMappers.toResponse(shop.createProduct(req.type(), input));
        audit.record(AuditLog.Action.PRODUCT_CREATED, "PRODUCT", created.id(),
                req.type() + " item " + created.name());
        return created;
    }

    @PutMapping("/products/{id}")
    public ProductResponse update(@PathVariable String id, @RequestBody ProductUpdateRequest req) {
        var before = shop.getProduct(id);
        ProductResponse updated = ApiMappers.toResponse(shop.updateProduct(id, req.name(),
                req.description(), req.costPrice(), req.price(), req.reorderLevel()));
        String detail = "updated " + updated.name();
        if (before.getPrice().compareTo(updated.price()) != 0) {
            detail += " · price " + before.getPrice() + " → " + updated.price();
        }
        audit.record(AuditLog.Action.PRODUCT_UPDATED, "PRODUCT", id, detail);
        return updated;
    }

    @DeleteMapping("/products/{id}")
    public void delete(@PathVariable String id) {
        RoleGate.requireAtLeast(Role.ADMIN); // the repository proxy enforces this again
        String name = shop.getProduct(id).getName();
        shop.deleteProduct(id);
        audit.record(AuditLog.Action.PRODUCT_DELETED, "PRODUCT", id, "deleted " + name);
    }

    @PostMapping("/products/{id}/restock")
    public ProductResponse restock(@PathVariable String id, @RequestBody RestockRequest req) {
        RoleGate.requireAtLeast(Role.MANAGER);
        ProductResponse restocked = ApiMappers.toResponse(
                shop.restock(id, req.quantity(), req.batchNo(), req.expiry()));
        audit.record(AuditLog.Action.STOCK_RESTOCKED, "PRODUCT", id,
                "+" + req.quantity() + (req.batchNo() == null || req.batchNo().isBlank()
                        ? "" : " (batch " + req.batchNo() + ")"));
        return restocked;
    }

    /** Records shrinkage: damage, loss, theft or a physical count correction (manager). */
    @PostMapping("/products/{id}/adjust")
    public ProductResponse adjust(@PathVariable String id, @RequestBody AdjustRequest req) {
        RoleGate.requireAtLeast(Role.MANAGER);
        ProductResponse adjusted = ApiMappers.toResponse(
                shop.adjustStock(id, req.reason(), req.quantity(), req.note()));
        audit.record(AuditLog.Action.SHRINKAGE_RECORDED, "PRODUCT", id,
                req.reason() + " " + req.quantity() + (req.note() == null || req.note().isBlank()
                        ? "" : " — " + req.note()));
        return adjusted;
    }

    public record AdjustRequest(String reason, java.math.BigDecimal quantity, String note) {
    }

    private static com.martflow.catalog.ProductUnit parseUnit(String unit) {
        if (unit == null || unit.isBlank()) {
            return null;
        }
        return com.martflow.catalog.ProductUnit.valueOf(unit.toUpperCase(Locale.ROOT));
    }
}
