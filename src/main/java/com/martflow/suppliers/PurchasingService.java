package com.martflow.suppliers;

import com.martflow.common.NotFoundException;
import com.martflow.common.TimeSource;
import com.martflow.inventory.ExpiryWatcher;
import com.martflow.inventory.InventoryService;
import com.martflow.persistence.Repository;
import com.martflow.security.Role;
import com.martflow.security.RoleGate;
import com.martflow.suppliers.postate.PurchaseOrderState;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Purchasing operations, all manager-gated: draft POs, submit, receive goods (the GRN that
 * puts stock on the shelf with batch and cost), supplier payments, close, and payables.
 * Every state change goes through the PO's {@link PurchaseOrderState} guard.
 */
public class PurchasingService {

    private final Repository<PurchaseOrder> orders;
    private final Repository<Supplier> suppliers;
    private final Repository<StandingOrderTemplate> templates;
    private final InventoryService inventory;
    private final ExpiryWatcher expiryWatcher;

    public PurchasingService(Repository<PurchaseOrder> orders, Repository<Supplier> suppliers,
                             Repository<StandingOrderTemplate> templates, InventoryService inventory,
                             ExpiryWatcher expiryWatcher) {
        this.orders = orders;
        this.suppliers = suppliers;
        this.templates = templates;
        this.inventory = inventory;
        this.expiryWatcher = expiryWatcher;
    }

    // ---------------- suppliers ----------------

    public List<Supplier> suppliers() {
        return suppliers.findAll();
    }

    public Supplier supplier(String id) {
        return suppliers.findById(id)
                .orElseThrow(() -> new NotFoundException("Unknown supplier: " + id));
    }

    public Supplier registerSupplier(String name, String phone, String contactPerson,
                                      String paymentTerms, String address) {
        RoleGate.requireAtLeast(Role.MANAGER);
        Supplier supplier = new Supplier("sup-" + System.nanoTime(), name, phone,
                contactPerson, paymentTerms, address);
        return suppliers.save(supplier);
    }

    // ---------------- purchase orders ----------------

    public List<PurchaseOrder> orders(String statusFilter) {
        List<PurchaseOrder> all = orders.findAll();
        if (statusFilter == null || statusFilter.isBlank()) {
            return all;
        }
        PurchaseOrderState wanted = PurchaseOrderState.fromName(statusFilter);
        List<PurchaseOrder> result = new ArrayList<>();
        for (PurchaseOrder order : all) {
            if (PurchaseOrderState.fromName(order.getStatus()) == wanted) {
                result.add(order);
            }
        }
        return result;
    }

    public PurchaseOrder order(String poNo) {
        return orders.findById(poNo)
                .orElseThrow(() -> new NotFoundException("Unknown purchase order: " + poNo));
    }

    /** Creates a DRAFT purchase order. Manager-only (buying is not a cashier decision). */
    public PurchaseOrder createDraft(String supplierId, List<LineRequest> lines) {
        RoleGate.requireAtLeast(Role.MANAGER);
        supplier(supplierId); // 404 on unknown supplier
        List<String> existing = orders.findAll().stream().map(PurchaseOrder::getPoNo).toList();
        PurchaseOrderBuilder builder = new PurchaseOrderBuilder(supplierId, existing);
        for (LineRequest line : lines) {
            builder.line(line.productId(), line.quantity(), line.unitCost());
        }
        PurchaseOrder order = builder.build();
        return orders.save(order);
    }

    /** One requested PO line. */
    public record LineRequest(String productId, BigDecimal quantity, BigDecimal unitCost) {
    }

    /** Submits a draft to the supplier. */
    public PurchaseOrder submit(String poNo) {
        RoleGate.requireAtLeast(Role.MANAGER);
        PurchaseOrder order = order(poNo);
        require(order.state().canSubmit(), "Only DRAFT orders can be submitted (currently "
                + order.getStatus() + ")");
        order.transitionTo(PurchaseOrderState.ORDERED);
        order.setSubmittedAt(TimeSource.now());
        return orders.save(order);
    }

    /** Cancels an order that has not fully arrived. */
    public PurchaseOrder cancel(String poNo, String reason) {
        RoleGate.requireAtLeast(Role.MANAGER);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A cancellation reason is required");
        }
        PurchaseOrder order = order(poNo);
        require(order.state().canCancel(), "Order in state " + order.getStatus()
                + " cannot be cancelled");
        order.transitionTo(PurchaseOrderState.CANCELLED);
        order.setCancelReason(reason);
        return orders.save(order);
    }

    /**
     * Receives goods (GRN): per line, quantity + batch + expiry + optional updated unit cost.
     * Stock goes on the shelf through the inventory chokepoint (persisted, RESTOCK event, batch
     * recorded), received quantities accumulate, and the state advances automatically to
     * PARTIALLY_RECEIVED / RECEIVED.
     */
    public PurchaseOrder receive(String poNo, List<GrnLine> grnLines) {
        RoleGate.requireAtLeast(Role.MANAGER);
        PurchaseOrder order = order(poNo);
        require(order.state().canReceive(), "Order in state " + order.getStatus()
                + " cannot receive goods — submit it first");
        if (grnLines == null || grnLines.isEmpty()) {
            throw new IllegalArgumentException("A goods receipt needs at least one line");
        }
        for (GrnLine grn : grnLines) {
            PurchaseOrderLine line = order.mutableLine(grn.productId());
            BigDecimal outstanding = line.getOrderedQty().subtract(line.getReceivedQty());
            if (grn.quantity().compareTo(outstanding) > 0) {
                throw new IllegalArgumentException("Cannot receive " + grn.quantity() + " of "
                        + line.getName() + " — only " + outstanding.stripTrailingZeros().toPlainString()
                        + " outstanding on " + poNo);
            }
        }
        for (GrnLine grn : grnLines) {
            PurchaseOrderLine line = order.mutableLine(grn.productId());
            line.addReceived(grn.quantity());
            if (grn.unitCost() != null) {
                updateCost(grn.productId(), grn.unitCost());
            }
            inventory.restock(grn.productId(), grn.quantity(), grn.batchNo(), grn.expiry());
        }
        // freshly landed dated batches get an immediate expiry check, scoped to this GRN
        // so old stock never spams duplicate alerts on every receipt
        List<String> datedProducts = new ArrayList<>();
        for (GrnLine grn : grnLines) {
            if (grn.expiry() != null && !datedProducts.contains(grn.productId())) {
                datedProducts.add(grn.productId());
            }
        }
        if (!datedProducts.isEmpty()) {
            expiryWatcher.watch(14, datedProducts);
        }
        order.setReceivedAt(TimeSource.now());
        order.transitionTo(order.fullyReceived()
                ? PurchaseOrderState.RECEIVED
                : PurchaseOrderState.PARTIALLY_RECEIVED);
        return orders.save(order);
    }

    /** One goods-receipt line: how much arrived, in which batch, expiring when, at what cost. */
    public record GrnLine(String productId, BigDecimal quantity, String batchNo,
                          LocalDate expiry, BigDecimal unitCost) {
    }

    /** Records a supplier payment against the PO. */
    public PurchaseOrder pay(String poNo, BigDecimal amount, String method, String note) {
        RoleGate.requireAtLeast(Role.MANAGER);
        PurchaseOrder order = order(poNo);
        require(!order.state().isTerminal(), "Cannot pay a " + order.getStatus() + " order");
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        order.addPayment(new PurchaseOrder.Payment(amount,
                method == null || method.isBlank() ? "CASH" : method.toUpperCase(),
                note, TimeSource.now()));
        return orders.save(order);
    }

    /** Closes a fully received order — the purchasing cycle ends here. */
    public PurchaseOrder close(String poNo) {
        RoleGate.requireAtLeast(Role.MANAGER);
        PurchaseOrder order = order(poNo);
        require(order.state().canClose(), "Only fully RECEIVED orders can be closed (currently "
                + order.getStatus() + ")");
        order.transitionTo(PurchaseOrderState.CLOSED);
        order.setClosedAt(TimeSource.now());
        return orders.save(order);
    }

    /** Total outstanding payables across all open orders — the "who do we owe" number. */
    public BigDecimal totalPayables() {
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrder order : orders.findAll()) {
            if (!order.state().isTerminal()) {
                total = total.add(order.payables().max(BigDecimal.ZERO));
            }
        }
        return total;
    }

    // ---------------- standing order templates (Prototype) ----------------

    public List<StandingOrderTemplate> templates() {
        return templates.findAll();
    }

    public StandingOrderTemplate saveTemplate(StandingOrderTemplate template) {
        RoleGate.requireAtLeast(Role.MANAGER);
        return templates.save(template);
    }

    /** Clones a template (or a past order) into a fresh DRAFT — the weekly restock click. */
    public PurchaseOrder fromTemplate(String templateId) {
        RoleGate.requireAtLeast(Role.MANAGER);
        StandingOrderTemplate template = templates.findById(templateId)
                .orElseThrow(() -> new NotFoundException("Unknown template: " + templateId));
        List<String> existing = orders.findAll().stream().map(PurchaseOrder::getPoNo).toList();
        return orders.save(template.instantiate(new PurchaseOrderBuilder(template.getSupplierId(), existing)));
    }

    /** Re-orders everything from a past PO — same idea, sourced from history. */
    public PurchaseOrder reorder(String sourcePoNo) {
        RoleGate.requireAtLeast(Role.MANAGER);
        PurchaseOrder source = order(sourcePoNo);
        List<String> existing = orders.findAll().stream().map(PurchaseOrder::getPoNo).toList();
        PurchaseOrderBuilder builder = new PurchaseOrderBuilder(source.getSupplierId(), existing);
        for (PurchaseOrderLine line : source.getLines()) {
            builder.line(line.getProductId(), line.getOrderedQty(), line.getUnitCost());
        }
        return orders.save(builder.build());
    }

    private void updateCost(String productId, BigDecimal unitCost) {
        // last-cost update: the profit report's baseline moves with the market
        inventory.updateCost(productId, unitCost);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
