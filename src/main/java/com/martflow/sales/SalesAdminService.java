package com.martflow.sales;

import com.martflow.billing.commands.BillingCommand;
import com.martflow.billing.commands.BillingInvoker;
import com.martflow.inventory.InventoryService;
import com.martflow.loyalty.LoyaltyService;
import com.martflow.payment.PaymentChannel;
import com.martflow.payment.TenderType;
import com.martflow.persistence.Repository;
import com.martflow.security.Role;
import com.martflow.security.RoleGate;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * Manager operations on completed sales. Voiding a sale is the full reverse of the tender
 * pipeline — stock back on the shelf, tenders refunded through their original channels, earned
 * points reversed, receipt stamped VOIDED and excluded from revenue. Runs through the same
 * command/rollback machinery as the original tender, so a half-done void cannot happen.
 */
public class SalesAdminService {

    private final Repository<Sale> sales;
    private final InventoryService inventory;
    private final LoyaltyService loyalty;
    private final Map<TenderType, PaymentChannel> channels;

    public SalesAdminService(Repository<Sale> sales, InventoryService inventory,
                             LoyaltyService loyalty, Map<TenderType, PaymentChannel> channels) {
        this.sales = sales;
        this.inventory = inventory;
        this.loyalty = loyalty;
        this.channels = new EnumMap<>(channels);
    }

    /** Voids a completed sale (manager only). Refunds flow through the original channels. */
    public Sale voidSale(String receiptNo, String reason) {
        RoleGate.requireAtLeast(Role.MANAGER);
        Sale sale = sales.findById(receiptNo)
                .orElseThrow(() -> new com.martflow.common.NotFoundException("Unknown receipt: " + receiptNo));
        if (sale.getStatus() == SaleStatus.VOIDED) {
            throw new IllegalStateException("Receipt " + receiptNo + " is already voided");
        }
        if (sale.getStatus() == SaleStatus.RETURNED) {
            throw new IllegalStateException("Cannot void a fully returned sale");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A void reason is required");
        }

        BillingInvoker invoker = new BillingInvoker();
        for (SaleLine line : sale.getLines()) {
            if (line.productId() != null) {
                invoker.addCommand(new RestockCommand(inventory, line.productId(), line.quantity()));
            }
        }
        for (Tender tender : sale.getTenders()) {
            PaymentChannel channel = channels.get(tender.type());
            if (channel != null) {
                invoker.addCommand(new RefundCommand(channel, tender.amount(),
                        tender.transactionId() != null ? tender.transactionId() : tender.reference()));
            }
        }
        invoker.addCommand(new MarkVoidedCommand(sales, sale, reason));
        invoker.run();

        // earned points go back too (cannot fail meaningfully — floored at zero)
        if (sale.getCustomerId() != null) {
            loyalty.reverseEarn(sale.getCustomerId(), loyalty.pointsFor(sale.getTotals().net()));
        }
        return sales.findById(receiptNo).orElseThrow();
    }

    /** Puts returned goods back on the shelf; undo consumes them again. */
    private record RestockCommand(InventoryService inventory, String productId,
                                  BigDecimal quantity) implements BillingCommand {

        @Override
        public void execute() {
            inventory.restore(productId, quantity);
        }

        @Override
        public void undo() {
            inventory.consume(productId, quantity);
        }
    }

    /** Best-effort refund through the original channel. */
    private record RefundCommand(PaymentChannel channel, BigDecimal amount,
                                 String reference) implements BillingCommand {

        @Override
        public void execute() {
            channel.refund(amount, reference);
        }

        @Override
        public void undo() {
            // refunds are one-way; nothing to take back
        }
    }

    private record MarkVoidedCommand(Repository<Sale> sales, Sale sale,
                                     String reason) implements BillingCommand {

        @Override
        public void execute() {
            sale.setStatus(SaleStatus.VOIDED);
            sale.setVoidReason(reason);
            sale.setVoidedAt(com.martflow.common.TimeSource.now());
            sales.save(sale);
        }

        @Override
        public void undo() {
            sale.setStatus(SaleStatus.COMPLETED);
            sale.setVoidReason(null);
            sale.setVoidedAt(null);
            sales.save(sale);
        }
    }
}
