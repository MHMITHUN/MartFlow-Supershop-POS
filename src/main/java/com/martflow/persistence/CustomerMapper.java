package com.martflow.persistence;

import com.martflow.common.TimeSource;
import com.martflow.loyalty.Customer;
import org.bson.Document;

import java.time.LocalDate;

/** Maps {@link Customer} documents. */
public class CustomerMapper implements DocumentMapper<Customer> {

    @Override
    public Document toDocument(Customer c) {
        return new Document("_id", c.getId())
                .append("name", c.getName())
                .append("phone", c.getPhone())
                .append("cardNo", c.getCardNo())
                .append("pointsBalance", c.getPointsBalance())
                .append("memberSince", c.getMemberSince() == null ? null : c.getMemberSince().toString())
                .append("active", c.isActive());
    }

    @Override
    public Customer fromDocument(Document d) {
        LocalDate memberSince;
        try {
            String raw = d.getString("memberSince");
            memberSince = raw == null || raw.isBlank() ? null : LocalDate.parse(raw);
        } catch (Exception unparseable) {
            memberSince = TimeSource.today();
        }
        return new Customer(
                d.getString("_id"),
                d.getString("name"),
                d.getString("phone"),
                d.getString("cardNo"),
                d.getInteger("pointsBalance", 0),
                memberSince,
                d.getBoolean("active", true));
    }

    @Override
    public String idOf(Customer customer) {
        return customer.getId();
    }
}
