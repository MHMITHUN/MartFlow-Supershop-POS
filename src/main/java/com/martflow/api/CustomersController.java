package com.martflow.api;

import com.martflow.api.dto.BillingDtos;
import com.martflow.api.dto.BillingMappers;
import com.martflow.audit.AuditLog;
import com.martflow.audit.AuditService;
import com.martflow.common.NotFoundException;
import com.martflow.loyalty.LoyaltyService;
import com.martflow.security.Role;
import com.martflow.security.RoleGate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Loyalty membership: register, look up, adjust points (managers only). */
@RestController
@RequestMapping("/api/customers")
public class CustomersController {

    private final LoyaltyService loyalty;
    private final AuditService audit;

    public CustomersController(LoyaltyService loyalty, AuditService audit) {
        this.loyalty = loyalty;
        this.audit = audit;
    }

    @GetMapping
    public List<BillingDtos.CustomerResponse> list(@RequestParam(required = false) String q) {
        return loyalty.all().stream()
                .filter(c -> q == null || q.isBlank()
                        || c.getName().toLowerCase().contains(q.toLowerCase())
                        || (c.getPhone() != null && c.getPhone().contains(q))
                        || (c.getCardNo() != null && c.getCardNo().toLowerCase().contains(q.toLowerCase())))
                .map(BillingMappers::toCustomerResponse)
                .toList();
    }

    @PostMapping
    public BillingDtos.CustomerResponse register(@RequestBody BillingDtos.RegisterCustomerRequest req) {
        return BillingMappers.toCustomerResponse(
                loyalty.register(req.name(), req.phone(), req.cardNo()));
    }

    @GetMapping("/{id}")
    public BillingDtos.CustomerResponse get(@PathVariable String id) {
        return loyalty.findById(id)
                .map(BillingMappers::toCustomerResponse)
                .orElseThrow(() -> new NotFoundException("Unknown customer: " + id));
    }

    @PostMapping("/{id}/points/adjust")
    public BillingDtos.CustomerResponse adjust(@PathVariable String id,
                                               @RequestBody BillingDtos.PointsAdjustRequest req) {
        RoleGate.requireAtLeast(Role.MANAGER);
        if (req.points() == null || req.points() < 0) {
            throw new IllegalArgumentException("Points must be 0 or more");
        }
        BillingDtos.CustomerResponse adjusted =
                BillingMappers.toCustomerResponse(loyalty.adjust(id, req.points()));
        audit.record(AuditLog.Action.POINTS_ADJUSTED, "CUSTOMER", id,
                "points → " + adjusted.pointsBalance());
        return adjusted;
    }
}
