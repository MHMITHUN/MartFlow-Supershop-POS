package com.martflow.api;

import com.martflow.audit.AuditLog;
import com.martflow.audit.AuditService;
import com.martflow.security.Role;
import com.martflow.security.RoleGate;
import com.martflow.suppliers.PurchaseOrder;
import com.martflow.suppliers.PurchasingService;
import com.martflow.suppliers.StandingOrderTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The full purchase-order cycle: create draft, submit, receive (GRN), pay, close — plus the
 * standing-order templates (Prototype) that turn the weekly restock into two clicks.
 * Manager-only.
 */
@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrdersController {

    private final PurchasingService purchasing;
    private final AuditService audit;

    public PurchaseOrdersController(PurchasingService purchasing, AuditService audit) {
        this.purchasing = purchasing;
        this.audit = audit;
    }

    public record PoLineRequest(String productId, BigDecimal quantity, BigDecimal unitCost) {
    }

    public record CreatePoRequest(String supplierId, List<PoLineRequest> lines) {
    }

    public record GrnLineRequest(String productId, BigDecimal quantity, String batchNo,
                                 LocalDate expiry, BigDecimal unitCost) {
    }

    public record ReceiveRequest(List<GrnLineRequest> lines) {
    }

    public record PayRequest(BigDecimal amount, String method, String note) {
    }

    public record ReasonRequest(String reason) {
    }

    public record FromTemplateRequest(String templateId, String sourcePoNo) {
    }

    public record SaveTemplateRequest(String id, String name, String supplierId,
                                      List<PoLineRequest> lines) {
    }

    public record PoLineResponse(String productId, String sku, String name,
                                 BigDecimal orderedQty, BigDecimal receivedQty,
                                 BigDecimal unitCost, BigDecimal lineTotal) {
    }

    public record PaymentResponse(BigDecimal amount, String method, String note, String at) {
    }

    public record PoResponse(
            String poNo,
            String supplierId,
            String status,
            List<PoLineResponse> lines,
            List<PaymentResponse> payments,
            String createdAt,
            String submittedAt,
            String receivedAt,
            String closedAt,
            String cancelReason,
            BigDecimal orderedTotal,
            BigDecimal payables,
            boolean fullyReceived) {
    }

    @GetMapping
    public List<PoResponse> list(@RequestParam(required = false) String status) {
        RoleGate.requireAtLeast(Role.MANAGER);
        return purchasing.orders(status).stream().map(PurchaseOrdersController::toResponse).toList();
    }

    @GetMapping("/{poNo}")
    public PoResponse get(@PathVariable String poNo) {
        RoleGate.requireAtLeast(Role.MANAGER);
        return toResponse(purchasing.order(poNo));
    }

    @PostMapping
    public PoResponse create(@RequestBody CreatePoRequest req) {
        return toResponse(purchasing.createDraft(req.supplierId(),
                req.lines().stream().map(l -> new PurchasingService.LineRequest(
                        l.productId(), l.quantity(), l.unitCost())).toList()));
    }

    @PostMapping("/{poNo}/submit")
    public PoResponse submit(@PathVariable String poNo) {
        PoResponse submitted = toResponse(purchasing.submit(poNo));
        audit.record(AuditLog.Action.PO_SUBMITTED, "PO", poNo, "→ " + submitted.status());
        return submitted;
    }

    @PostMapping("/{poNo}/cancel")
    public PoResponse cancel(@PathVariable String poNo, @RequestBody ReasonRequest req) {
        PoResponse cancelled = toResponse(purchasing.cancel(poNo, req.reason()));
        audit.record(AuditLog.Action.PO_CANCELLED, "PO", poNo, "reason: " + req.reason());
        return cancelled;
    }

    @PostMapping("/{poNo}/receive")
    public PoResponse receive(@PathVariable String poNo, @RequestBody ReceiveRequest req) {
        PoResponse received = toResponse(purchasing.receive(poNo, req.lines().stream()
                .map(l -> new PurchasingService.GrnLine(l.productId(), l.quantity(),
                        l.batchNo(), l.expiry(), l.unitCost())).toList()));
        audit.record(AuditLog.Action.PO_RECEIVED, "PO", poNo,
                req.lines().size() + " GRN line(s) → " + received.status());
        return received;
    }

    @PostMapping("/{poNo}/payments")
    public PoResponse pay(@PathVariable String poNo, @RequestBody PayRequest req) {
        PoResponse paid = toResponse(purchasing.pay(poNo, req.amount(), req.method(), req.note()));
        audit.record(AuditLog.Action.PO_PAID, "PO", poNo,
                req.amount() + " via " + (req.method() == null ? "CASH" : req.method()));
        return paid;
    }

    @PostMapping("/{poNo}/close")
    public PoResponse close(@PathVariable String poNo) {
        PoResponse closed = toResponse(purchasing.close(poNo));
        audit.record(AuditLog.Action.PO_CLOSED, "PO", poNo, "purchasing cycle complete");
        return closed;
    }

    /** The weekly restock in two clicks: clone a template (or a past order) into a new draft. */
    @PostMapping("/from-template")
    public PoResponse fromTemplate(@RequestBody FromTemplateRequest req) {
        if (req.templateId() != null && !req.templateId().isBlank()) {
            PoResponse cloned = toResponse(purchasing.fromTemplate(req.templateId()));
            audit.record(AuditLog.Action.TEMPLATE_CLONED, "TEMPLATE", req.templateId(),
                    "cloned into " + cloned.poNo());
            return cloned;
        }
        return toResponse(purchasing.reorder(req.sourcePoNo()));
    }

    @org.springframework.web.bind.annotation.GetMapping("/templates")
    public List<Object> templates() {
        RoleGate.requireAtLeast(Role.MANAGER);
        return purchasing.templates().stream()
                .map(t -> (Object) new TemplateResponse(t.getId(), t.getName(),
                        t.getSupplierId(),
                        t.getLines().stream().map(l -> l.productId() + " x"
                                + l.quantity().stripTrailingZeros().toPlainString()).toList()))
                .toList();
    }

    @org.springframework.web.bind.annotation.PostMapping("/templates")
    public TemplateResponse saveTemplate(@RequestBody SaveTemplateRequest req) {
        RoleGate.requireAtLeast(Role.MANAGER);
        StandingOrderTemplate template = purchasing.saveTemplate(new StandingOrderTemplate(
                req.id() == null || req.id().isBlank()
                        ? "tpl-" + System.nanoTime() : req.id(),
                req.name(), req.supplierId(),
                req.lines().stream().map(l -> new StandingOrderTemplate.TemplateLine(
                        l.productId(), null, l.quantity())).toList()));
        audit.record(AuditLog.Action.TEMPLATE_SAVED, "TEMPLATE", template.getId(),
                "template " + template.getName() + " (" + req.lines().size() + " lines)");
        return new TemplateResponse(template.getId(), template.getName(),
                template.getSupplierId(), List.of());
    }

    public record TemplateResponse(String id, String name, String supplierId,
                                   List<String> lines) {
    }

    public static PoResponse toResponse(PurchaseOrder po) {
        return new PoResponse(
                po.getPoNo(),
                po.getSupplierId(),
                po.getStatus(),
                po.getLines().stream().map(l -> new PoLineResponse(
                        l.getProductId(), l.getSku(), l.getName(), l.getOrderedQty(),
                        l.getReceivedQty(), l.getUnitCost(), l.lineTotal())).toList(),
                po.getPayments().stream().map(p -> new PaymentResponse(
                        p.amount(), p.method(), p.note(), p.at().toString())).toList(),
                po.getCreatedAt().toString(),
                po.getSubmittedAt() == null ? null : po.getSubmittedAt().toString(),
                po.getReceivedAt() == null ? null : po.getReceivedAt().toString(),
                po.getClosedAt() == null ? null : po.getClosedAt().toString(),
                po.getCancelReason(),
                po.orderedTotal(),
                po.payables(),
                po.fullyReceived());
    }
}
