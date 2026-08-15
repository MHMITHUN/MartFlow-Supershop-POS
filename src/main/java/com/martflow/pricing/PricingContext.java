package com.martflow.pricing;

import com.martflow.loyalty.Customer;

import java.time.LocalDate;

/** What a pricing strategy needs to know about the bill in progress. */
public record PricingContext(Customer customer, LocalDate today) {
}
