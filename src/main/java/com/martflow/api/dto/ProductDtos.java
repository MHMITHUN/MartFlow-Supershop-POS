package com.martflow.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Request/response records for the inventory endpoints. Kept as one file: they are plain
 *  data carriers, and colocating them keeps the dto package scannable. */
public final class ProductDtos {

    private ProductDtos() {
    }

    public record BatchResponse(String batchNo, LocalDate expiry, BigDecimal receivedQty) {
    }

    public record ProductResponse(
            String id,
            String sku,
            String barcode,
            String name,
            String description,
            String type,
            String categoryId,
            String categoryName,
            BigDecimal vatRatePercent,
            String unit,
            BigDecimal costPrice,
            BigDecimal price,
            BigDecimal mrp,
            BigDecimal pricePerUnit,
            BigDecimal stock,
            int reorderLevel,
            boolean lowStock,
            List<BatchResponse> batches,
            String supplierId,
            List<String> componentIds) {
    }

    public record CategoryResponse(String id, String name, BigDecimal vatRatePercent) {
    }

    public record ProductCreateRequest(
            String type,
            String sku,
            String barcode,
            String name,
            String description,
            String categoryId,
            String supplierId,
            String unit,
            BigDecimal costPrice,
            BigDecimal price,
            BigDecimal stock,
            Integer reorderLevel,
            List<String> componentIds,
            BigDecimal fixedPrice) {
    }

    public record ProductUpdateRequest(
            String name,
            String description,
            BigDecimal costPrice,
            BigDecimal price,
            Integer reorderLevel) {
    }

    public record RestockRequest(BigDecimal quantity, String batchNo, LocalDate expiry) {
    }

    public record AlertResponse(
            String id,
            boolean read,
            String type,
            String productId,
            String productName,
            String message) {
    }
}
