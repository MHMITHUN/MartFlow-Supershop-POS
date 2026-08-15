package com.martflow.api;

import com.martflow.audit.AuditLog;
import com.martflow.audit.AuditService;
import com.martflow.security.Role;
import com.martflow.security.RoleGate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** The owner's question answered: who did what, and when. Manager and above. */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    public record AuditResponse(String id, String at, String actor, String role, String action,
                                String targetType, String targetId, String detail) {
    }

    @GetMapping
    public List<AuditResponse> list(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Integer limit) {
        RoleGate.requireAtLeast(Role.MANAGER);
        return audit.query(parseDate(from), parseDate(to), actor, parseAction(action),
                        limit == null ? 200 : limit).stream()
                .map(AuditController::toResponse)
                .toList();
    }

    private static LocalDate parseDate(String raw) {
        return raw == null || raw.isBlank() ? null : LocalDate.parse(raw);
    }

    private static AuditLog.Action parseAction(String raw) {
        return raw == null || raw.isBlank() ? null : AuditLog.Action.valueOf(raw.trim().toUpperCase());
    }

    private static AuditResponse toResponse(AuditLog e) {
        return new AuditResponse(e.getId(), e.getAt() == null ? null : e.getAt().toString(),
                e.getActorUsername(), e.getRole(), e.getAction().name(),
                e.getTargetType(), e.getTargetId(), e.getDetail());
    }
}
