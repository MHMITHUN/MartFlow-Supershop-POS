package com.martflow.api;

import com.martflow.api.dto.BillingDtos.CouponCheckRequest;
import com.martflow.api.dto.BillingDtos.CouponCheckResponse;
import com.martflow.api.dto.BillingDtos.PromotionResponse;
import com.martflow.api.dto.BillingDtos.PromotionUpsertRequest;
import com.martflow.audit.AuditLog;
import com.martflow.audit.AuditService;
import com.martflow.pricing.Promotion;
import com.martflow.pricing.PromotionEngine;
import com.martflow.security.Role;
import com.martflow.security.RoleGate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Promotion administration: managers run the marketing calendar; the till only reads it. */
@RestController
@RequestMapping("/api/promotions")
public class PromotionsController {

    private final PromotionEngine engine;
    private final AuditService audit;

    public PromotionsController(PromotionEngine engine, AuditService audit) {
        this.engine = engine;
        this.audit = audit;
    }

    @GetMapping
    public List<PromotionResponse> list() {
        return engine.all().stream().map(PromotionsController::toResponse).toList();
    }

    @PostMapping
    public PromotionResponse create(@RequestBody PromotionUpsertRequest req) {
        RoleGate.requireAtLeast(Role.MANAGER);
        Promotion promotion = new Promotion(
                "prm-" + UUID.randomUUID().toString().substring(0, 8),
                req.name(),
                Promotion.Type.valueOf(req.type().toUpperCase()),
                req.categoryId(),
                req.percentOff(),
                req.flatAmount(),
                req.code(),
                parseDate(req.startsOn()),
                parseDate(req.endsOn()),
                req.active() == null || req.active());
        PromotionResponse created = toResponse(engine.save(promotion));
        audit.record(AuditLog.Action.PROMOTION_CREATED, "PROMOTION", created.id(),
                created.type() + " " + created.name());
        return created;
    }

    @PutMapping("/{id}")
    public PromotionResponse update(@PathVariable String id, @RequestBody PromotionUpsertRequest req) {
        RoleGate.requireAtLeast(Role.MANAGER);
        Promotion promotion = engine.all().stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new com.martflow.common.NotFoundException("Unknown promotion: " + id));
        if (req.name() != null) promotion.setName(req.name());
        if (req.categoryId() != null) promotion.setCategoryId(req.categoryId());
        if (req.percentOff() != null) promotion.setPercentOff(req.percentOff());
        if (req.flatAmount() != null) promotion.setFlatAmount(req.flatAmount());
        if (req.code() != null) promotion.setCode(req.code());
        if (req.startsOn() != null) promotion.setStartsOn(parseDate(req.startsOn()));
        if (req.endsOn() != null) promotion.setEndsOn(parseDate(req.endsOn()));
        if (req.active() != null) promotion.setActive(req.active());
        PromotionResponse updated = toResponse(engine.save(promotion));
        audit.record(AuditLog.Action.PROMOTION_UPDATED, "PROMOTION", id,
                (updated.active() ? "activated " : "updated ") + updated.name());
        return updated;
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        RoleGate.requireAtLeast(Role.MANAGER);
        engine.delete(id);
        audit.record(AuditLog.Action.PROMOTION_DELETED, "PROMOTION", id, "promotion removed");
    }

    /** What the till calls while typing a coupon: does it exist, what is it worth? */
    @PostMapping("/validate")
    public CouponCheckResponse validate(@RequestBody CouponCheckRequest req) {
        BigDecimal amount = engine.couponAmount(req.code(), req.netTotal());
        return new CouponCheckResponse(req.code() == null ? null : req.code().trim().toUpperCase(), amount);
    }

    private static LocalDate parseDate(String raw) {
        return raw == null || raw.isBlank() ? null : LocalDate.parse(raw);
    }

    private static PromotionResponse toResponse(Promotion p) {
        return new PromotionResponse(
                p.getId(),
                p.getName(),
                p.getType().name(),
                p.getCategoryId(),
                p.getPercentOff(),
                p.getFlatAmount(),
                p.getCode(),
                p.getStartsOn() == null ? null : p.getStartsOn().toString(),
                p.getEndsOn() == null ? null : p.getEndsOn().toString(),
                p.isActive());
    }
}
