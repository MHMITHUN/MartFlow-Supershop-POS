package com.martflow.pricing;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A marketing promotion configured by the manager: a category sale, a member-price benefit, or
 * a coupon (flat or percent). Date-bounded and toggleable.
 */
public class Promotion {

    public enum Type {
        CATEGORY_SALE,   // percentOff on categoryId
        MEMBER_PRICE,    // percentOff for loyalty members
        COUPON_FLAT,     // flatAmount BDT off with code
        COUPON_PERCENT   // percentOff with code
    }

    private final String id;
    private String name;
    private final Type type;
    private String categoryId;   // CATEGORY_SALE
    private BigDecimal percentOff; // CATEGORY_SALE / MEMBER_PRICE / COUPON_PERCENT
    private BigDecimal flatAmount; // COUPON_FLAT
    private String code;          // COUPON_*
    private LocalDate startsOn;
    private LocalDate endsOn;
    private boolean active;

    public Promotion(String id, String name, Type type, String categoryId,
                     BigDecimal percentOff, BigDecimal flatAmount, String code,
                     LocalDate startsOn, LocalDate endsOn, boolean active) {
        this.id = id == null || id.isBlank() ? "prm-" + System.nanoTime() : id;
        this.name = name;
        this.type = type == null ? Type.CATEGORY_SALE : type;
        this.categoryId = categoryId;
        this.percentOff = percentOff;
        this.flatAmount = flatAmount;
        this.code = code == null ? null : code.trim().toUpperCase();
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.active = active;
    }

    /** {@code true} when the promotion applies on {@code day}. */
    public boolean isActiveOn(LocalDate day) {
        if (!active || day == null) {
            return false;
        }
        if (startsOn != null && day.isBefore(startsOn)) {
            return false;
        }
        if (endsOn != null && day.isAfter(endsOn)) {
            return false;
        }
        return true;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Type getType() {
        return type;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public BigDecimal getPercentOff() {
        return percentOff;
    }

    public void setPercentOff(BigDecimal percentOff) {
        this.percentOff = percentOff;
    }

    public BigDecimal getFlatAmount() {
        return flatAmount;
    }

    public void setFlatAmount(BigDecimal flatAmount) {
        this.flatAmount = flatAmount;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code == null ? null : code.trim().toUpperCase();
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public void setStartsOn(LocalDate startsOn) {
        this.startsOn = startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public void setEndsOn(LocalDate endsOn) {
        this.endsOn = endsOn;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
