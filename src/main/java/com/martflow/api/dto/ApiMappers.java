package com.martflow.api.dto;

import com.martflow.catalog.Category;
import com.martflow.catalog.ComboProduct;
import com.martflow.catalog.Product;
import com.martflow.catalog.UnitProduct;
import com.martflow.catalog.WeighedProduct;
import com.martflow.inventory.AlertService;
import com.martflow.inventory.StockEvent;

import java.util.ArrayList;
import java.util.List;

/** Maps domain objects to their API response records (one direction only — requests are mapped
 *  in the controllers where the domain call is made). */
public final class ApiMappers {

    private ApiMappers() {
    }

    public static ProductDtos.ProductResponse toResponse(Product p) {
        List<ProductDtos.BatchResponse> batches = new ArrayList<>();
        for (var batch : p.getBatches()) {
            batches.add(new ProductDtos.BatchResponse(batch.batchNo(), batch.expiry(), batch.receivedQty()));
        }
        return new ProductDtos.ProductResponse(
                p.getId(),
                p.getSku(),
                p.getBarcode(),
                p.getName(),
                p.getDescription(),
                p.getType(),
                p.getCategoryId(),
                categoryName(p),
                com.martflow.catalog.CategoryRegistry.vatRateOf(p.getCategoryId()),
                p.getUnit().name(),
                p.getCostPrice(),
                p.getPrice(),
                p instanceof UnitProduct u ? u.getMrp() : null,
                p instanceof WeighedProduct w ? w.getPricePerUnit() : null,
                p.getStock(),
                p.getReorderLevel(),
                p.isLowStock(),
                batches,
                p.getSupplierId(),
                p instanceof ComboProduct c ? c.getComponentIds() : null);
    }

    private static String categoryName(Product p) {
        return com.martflow.catalog.CategoryRegistry.find(p.getCategoryId())
                .map(Category::name)
                .orElse(p.getCategoryId());
    }

    public static ProductDtos.CategoryResponse toResponse(Category c) {
        return new ProductDtos.CategoryResponse(c.id(), c.name(), c.vatRatePercent());
    }

    public static ProductDtos.AlertResponse toResponse(AlertService.Alert alert) {
        StockEvent event = alert.getEvent();
        return new ProductDtos.AlertResponse(
                alert.getId(),
                alert.isRead(),
                event.getType().name(),
                event.getProductId(),
                event.getProductName(),
                event.getMessage());
    }
}
