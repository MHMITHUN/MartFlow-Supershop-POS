package com.martflow.loyalty;

import com.martflow.common.TimeSource;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A loyalty member: identified by phone (unique) with an optional card number. Points are the
 * shop's currency — 1 point per 100 BDT spent, 1 point redeems as 1 BDT at the till.
 */
public class Customer {

    private final String id;
    private String name;
    private String phone;
    private String cardNo;
    private int pointsBalance;
    private final LocalDate memberSince;
    private boolean active;

    public Customer(String id, String name, String phone, String cardNo,
                    int pointsBalance, LocalDate memberSince, boolean active) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Customer id is required");
        }
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.cardNo = cardNo;
        this.pointsBalance = Math.max(0, pointsBalance);
        this.memberSince = memberSince == null ? TimeSource.today() : memberSince;
        this.active = active;
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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCardNo() {
        return cardNo;
    }

    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }

    public int getPointsBalance() {
        return pointsBalance;
    }

    /** Points movement is package-adjacent: go through {@link LoyaltyService}. */
    void setPointsBalance(int pointsBalance) {
        this.pointsBalance = Math.max(0, pointsBalance);
    }

    public LocalDate getMemberSince() {
        return memberSince;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Customer c)) return false;
        return Objects.equals(id, c.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
