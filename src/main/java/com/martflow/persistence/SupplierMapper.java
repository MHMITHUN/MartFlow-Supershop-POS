package com.martflow.persistence;

import com.martflow.suppliers.Supplier;
import org.bson.Document;

/** Maps {@link Supplier} documents. */
public class SupplierMapper implements DocumentMapper<Supplier> {

    @Override
    public Document toDocument(Supplier s) {
        return new Document("_id", s.getId())
                .append("name", s.getName())
                .append("phone", s.getPhone())
                .append("contactPerson", s.getContactPerson())
                .append("paymentTerms", s.getPaymentTerms())
                .append("address", s.getAddress());
    }

    @Override
    public Supplier fromDocument(Document d) {
        return new Supplier(
                d.getString("_id"),
                d.getString("name"),
                d.getString("phone"),
                d.getString("contactPerson"),
                d.getString("paymentTerms"),
                d.getString("address"));
    }

    @Override
    public String idOf(Supplier supplier) {
        return supplier.getId();
    }
}
