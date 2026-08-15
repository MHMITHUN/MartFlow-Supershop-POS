package com.martflow.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Request/response records for billing, sales, customers and promotions. */
public final class BillingDtos {

    private BillingDtos() {
    }

    // ---- bill (in-progress) ----

    public record AddLineRequest(String productId, String barcode, Integer quantity,
                                 BigDecimal weightKg) {
    }

    public record UpdateLineRequest(BigDecimal quantity) {
    }

    public record CouponRequest(String code) {
    }

    public record CustomerRequest(String customerIdOrPhone) {
    }

    public record ChargesRequest(Integer carryBags, BigDecimal deliveryFee) {
    }

    public record TenderItem(String type, BigDecimal amount, String reference) {
    }

    public record TenderRequest(List<TenderItem> tenders) {
    }

    public record BillLineResponse(
            int lineNo,
            String name,
            String sku,
            String productId,
            String describe,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal gross,
            BigDecimal discount,
            BigDecimal net,
            BigDecimal vatRate,
            BigDecimal vatAmount,
            String kind) {
    }

    public record BillTotalsResponse(
            BigDecimal gross,
            BigDecimal discount,
            BigDecimal coupon,
            BigDecimal fees,
            BigDecimal net,
            BigDecimal vat) {
    }

    public record BillCustomerSummary(String id, String name, String phone, int points) {
    }

    public record BillResponse(
            List<BillLineResponse> lines,
            BillTotalsResponse totals,
            BillCustomerSummary customer,
            String couponCode,
            int carryBags,
            BigDecimal carryBagUnitFee,
            BigDecimal deliveryFee,
            int undoDepth) {
    }

    // ---- sale (completed) ----

    public record SaleResponse(
            String receiptNo,
            LocalDateTime at,
            String cashier,
            String customerId,
            String status,
            String voidReason,
            LocalDateTime voidedAt,
            List<BillLineResponse> lines,
            SaleTotalsResponse totals,
            List<TenderResponse> tenders) {
    }

    public record SaleTotalsResponse(
            BigDecimal gross,
            BigDecimal discount,
            BigDecimal coupon,
            BigDecimal fees,
            BigDecimal roundOff,
            BigDecimal net,
            BigDecimal vat,
            BigDecimal tendered,
            BigDecimal change) {
    }

    public record TenderResponse(String type, BigDecimal amount, String transactionId) {
    }

    public record SaleSummaryResponse(
            String receiptNo,
            LocalDateTime at,
            String cashier,
            String customerId,
            String status,
            BigDecimal net,
            BigDecimal vat) {
    }

    // ---- customers ----

    public record CustomerResponse(String id, String name, String phone, String cardNo,
                                   int pointsBalance, String memberSince, boolean active) {
    }

    public record RegisterCustomerRequest(String name, String phone, String cardNo) {
    }

    public record PointsAdjustRequest(Integer points) {
    }

    // ---- promotions ----

    public record PromotionResponse(
            String id,
            String name,
            String type,
            String categoryId,
            BigDecimal percentOff,
            BigDecimal flatAmount,
            String code,
            String startsOn,
            String endsOn,
            boolean active) {
    }

    public record PromotionUpsertRequest(
            String name,
            String type,
            String categoryId,
            BigDecimal percentOff,
            BigDecimal flatAmount,
            String code,
            String startsOn,
            String endsOn,
            Boolean active) {
    }

    public record CouponCheckRequest(String code, BigDecimal netTotal) {
    }

    public record CouponCheckResponse(String code, BigDecimal amount) {
    }
}
