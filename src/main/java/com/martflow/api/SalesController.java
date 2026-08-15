package com.martflow.api;

import com.martflow.api.dto.BillingDtos.SaleResponse;
import com.martflow.api.dto.BillingDtos.SaleSummaryResponse;
import com.martflow.api.dto.BillingMappers;
import com.martflow.audit.AuditLog;
import com.martflow.audit.AuditService;
import com.martflow.common.NotFoundException;
import com.martflow.persistence.Repositories;
import com.martflow.sales.Sale;
import com.martflow.sales.SalesAdminService;
import com.martflow.sales.SaleStatus;
import com.martflow.security.Role;
import com.martflow.security.RoleGate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** Sale history: browse for managers, reprint for everyone at the till, void for managers. */
@RestController
@RequestMapping("/api/sales")
public class SalesController {

    private final SalesAdminService salesAdmin;
    private final AuditService audit;

    public SalesController(SalesAdminService salesAdmin, AuditService audit) {
        this.salesAdmin = salesAdmin;
        this.audit = audit;
    }

    public record VoidRequest(String reason) {
    }

    @GetMapping
    public List<SaleSummaryResponse> sales(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cashier) {
        RoleGate.requireAtLeast(Role.MANAGER);
        LocalDateTime start = from == null || from.isBlank() ? null
                : LocalDate.parse(from).atStartOfDay();
        LocalDateTime endExclusive = to == null || to.isBlank() ? null
                : LocalDate.parse(to).plusDays(1).atStartOfDay();
        SaleStatus statusFilter = status == null || status.isBlank() ? null
                : SaleStatus.parse(status);
        return Repositories.sales().findAll().stream()
                .filter(s -> start == null || !s.getAt().isBefore(start))
                .filter(s -> endExclusive == null || s.getAt().isBefore(endExclusive))
                .filter(s -> statusFilter == null || s.getStatus() == statusFilter)
                .filter(s -> cashier == null || cashier.isBlank()
                        || cashier.equalsIgnoreCase(s.getCashierUsername()))
                .sorted(Comparator.comparing(Sale::getAt).reversed())
                .map(SalesController::toSummary)
                .toList();
    }

    @GetMapping("/{receiptNo}")
    public SaleResponse sale(@PathVariable String receiptNo) {
        Sale sale = Repositories.sales().findById(receiptNo)
                .orElseThrow(() -> new NotFoundException("Unknown receipt: " + receiptNo));
        return BillingMappers.toSaleResponse(sale);
    }

    /** Voids a sale: stock restored, tenders refunded, points reversed (manager only). */
    @PostMapping("/{receiptNo}/void")
    public SaleResponse voidSale(@PathVariable String receiptNo, @RequestBody VoidRequest req) {
        SaleResponse voided = BillingMappers.toSaleResponse(salesAdmin.voidSale(receiptNo, req.reason()));
        audit.record(AuditLog.Action.SALE_VOIDED, "SALE", receiptNo, "reason: " + req.reason());
        return voided;
    }

    private static SaleSummaryResponse toSummary(Sale s) {
        return new SaleSummaryResponse(s.getReceiptNo(), s.getAt(), s.getCashierUsername(),
                s.getCustomerId(), s.getStatus().name(), s.getTotals().net(), s.getTotals().vat());
    }
}
