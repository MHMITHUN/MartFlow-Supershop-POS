package com.martflow.billing.commands;

import com.martflow.persistence.Repository;
import com.martflow.sales.Sale;

import java.util.function.Supplier;

/**
 * Persists the completed sale (line snapshots, totals, tenders). The sale is built lazily by a
 * supplier so it can include the transaction ids the charge commands have collected by the time
 * this step runs. Undo deletes the sale document.
 */
public final class CreateSaleCommand implements BillingCommand {

    private final Repository<Sale> sales;
    private final Supplier<Sale> saleSupplier;
    private Sale created;

    public CreateSaleCommand(Repository<Sale> sales, Supplier<Sale> saleSupplier) {
        this.sales = sales;
        this.saleSupplier = saleSupplier;
    }

    @Override
    public void execute() {
        created = saleSupplier.get();
        sales.save(created);
    }

    @Override
    public void undo() {
        if (created != null) {
            sales.delete(created.getReceiptNo());
        }
    }

    public Sale created() {
        return created;
    }
}
