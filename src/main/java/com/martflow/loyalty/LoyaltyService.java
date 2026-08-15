package com.martflow.loyalty;

import com.martflow.common.NotFoundException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Loyalty accounting: points earned, points redeemed, points reversed on void/return. The only
 * code allowed to touch a customer's balance.
 */
public class LoyaltyService {

    /** 1 point per 100 BDT of net spend. */
    public static final BigDecimal EARN_PER = new BigDecimal("100");

    private final CustomerRepository customers;

    public LoyaltyService(CustomerRepository customers) {
        this.customers = customers;
    }

    /** Points a bill of {@code netTotal} earns (floored). */
    public int pointsFor(BigDecimal netTotal) {
        if (netTotal == null || netTotal.signum() <= 0) {
            return 0;
        }
        return netTotal.divide(EARN_PER, 0, RoundingMode.FLOOR).intValue();
    }

    /** Registers a loyalty member (phone unique). */
    public Customer register(String name, String phone, String cardNo) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Customer name is required");
        }
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Customer phone is required");
        }
        if (customers.findByPhone(phone).isPresent()) {
            throw new IllegalArgumentException("A customer with this phone already exists: " + phone);
        }
        Customer customer = new Customer("cust-" + UUID.randomUUID().toString().substring(0, 8),
                name.trim(), phone.trim(), cardNo, 0, null, true);
        return customers.save(customer);
    }

    public Optional<Customer> findById(String id) {
        return customers.findById(id);
    }

    public Optional<Customer> findByPhone(String phone) {
        return customers.findByPhone(phone);
    }

    public List<Customer> all() {
        return customers.findAll();
    }

    /** Credits earned points after a completed sale. */
    public Customer earn(String customerId, int points) {
        Customer customer = require(customerId);
        customer.setPointsBalance(customer.getPointsBalance() + Math.max(0, points));
        return customers.save(customer);
    }

    /** Redeems points as tender (1 pt = 1 BDT). Throws when the balance is short. */
    public Customer redeem(String customerId, int points) {
        Customer customer = require(customerId);
        if (points <= 0) {
            throw new IllegalArgumentException("Redeemed points must be positive");
        }
        if (customer.getPointsBalance() < points) {
            throw new IllegalStateException("Insufficient points: balance "
                    + customer.getPointsBalance() + ", needed " + points);
        }
        customer.setPointsBalance(customer.getPointsBalance() - points);
        return customers.save(customer);
    }

    /** Puts redeemed/earned points back after a void or return. */
    public Customer reverse(String customerId, int points) {
        return earn(customerId, points);
    }

    /** Takes earned points back after a voided sale (rolled-back pipeline). Floors at zero. */
    public Customer reverseEarn(String customerId, int points) {
        Customer customer = require(customerId);
        customer.setPointsBalance(Math.max(0, customer.getPointsBalance() - Math.max(0, points)));
        return customers.save(customer);
    }

    /** Manager correction of a balance. */
    public Customer adjust(String customerId, int newBalance) {
        Customer customer = require(customerId);
        customer.setPointsBalance(Math.max(0, newBalance));
        return customers.save(customer);
    }

    private Customer require(String id) {
        return customers.findById(id)
                .orElseThrow(() -> new NotFoundException("Unknown customer: " + id));
    }
}
