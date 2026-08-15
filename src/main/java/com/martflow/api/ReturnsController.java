package com.martflow.api;

import com.martflow.api.dto.ReturnDtos;
import com.martflow.api.dto.ReturnMappers;
import com.martflow.audit.AuditLog;
import com.martflow.audit.AuditService;
import com.martflow.returns.ReturnService;
import com.martflow.security.Role;
import com.martflow.security.RoleContext;
import com.martflow.security.RoleGate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Returns and exchanges: the cashier processes them at the till, managers review history. */
@RestController
@RequestMapping("/api")
public class ReturnsController {

    private final ReturnService returns;
    private final AuditService audit;

    public ReturnsController(ReturnService returns, AuditService audit) {
        this.returns = returns;
        this.audit = audit;
    }

    @PostMapping("/sales/{receiptNo}/returns")
    public ReturnDtos.ReturnResponse create(@PathVariable String receiptNo,
                                            @RequestBody ReturnDtos.ReturnRequest req) {
        List<ReturnService.RequestedLine> lines = req.lines().stream()
                .map(l -> new ReturnService.RequestedLine(l.lineNo(), l.quantity(), l.reason()))
                .toList();
        ReturnDtos.ReturnResponse response = ReturnMappers.toResponse(returns.returnItems(
                receiptNo, lines, req.refundChannel(), RoleContext.current().username()));
        audit.record(AuditLog.Action.RETURN_PROCESSED, "SALE", receiptNo,
                "refund " + response.refundAmount() + " via " + response.refundChannel());
        return response;
    }

    @GetMapping("/returns")
    public List<ReturnDtos.ReturnResponse> list(@RequestParam(required = false) String receiptNo) {
        RoleGate.requireAtLeast(Role.MANAGER);
        var stream = returns.all().stream();
        if (receiptNo != null && !receiptNo.isBlank()) {
            stream = stream.filter(r -> receiptNo.equals(r.getReceiptNo()));
        }
        return stream.map(ReturnMappers::toResponse).toList();
    }
}
