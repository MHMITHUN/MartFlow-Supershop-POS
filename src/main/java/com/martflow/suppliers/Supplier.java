package com.martflow.suppliers;

import java.util.Objects;

/** A distributor the shop buys from, with contact details and payment terms. */
public class Supplier {

    private final String id;
    private String name;
    private String phone;
    private String contactPerson;
    private String paymentTerms; // e.g. "Net 15" / "Cash on delivery"
    private String address;

    public Supplier(String id, String name, String phone, String contactPerson,
                    String paymentTerms, String address) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Supplier id is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Supplier name is required");
        }
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.contactPerson = contactPerson;
        this.paymentTerms = paymentTerms;
        this.address = address;
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

    public String getContactPerson() {
        return contactPerson;
    }

    public void setContactPerson(String contactPerson) {
        this.contactPerson = contactPerson;
    }

    public String getPaymentTerms() {
        return paymentTerms;
    }

    public void setPaymentTerms(String paymentTerms) {
        this.paymentTerms = paymentTerms;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Supplier s)) return false;
        return Objects.equals(id, s.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
