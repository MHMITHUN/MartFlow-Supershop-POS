package com.martflow.api;

import com.martflow.audit.AuditLog;
import com.martflow.audit.AuditService;
import com.martflow.auth.AuthService;
import com.martflow.auth.User;
import com.martflow.security.Role;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/** Staff account administration (owner only). */
@RestController
@RequestMapping("/api/users")
public class UsersController {

    private final AuthService auth;
    private final AuditService audit;

    public UsersController(AuthService auth, AuditService audit) {
        this.auth = auth;
        this.audit = audit;
    }

    public record UserResponse(String id, String username, String fullName, String role,
                               boolean active, LocalDateTime createdAt) {
    }

    public record CreateUserRequest(String username, String password, String fullName, String role) {
    }

    public record UpdateUserRequest(String fullName, String role, Boolean active, String password) {
    }

    @GetMapping
    public List<UserResponse> list() {
        return auth.listUsers().stream().map(UsersController::toResponse).toList();
    }

    @PostMapping
    public UserResponse create(@RequestBody CreateUserRequest req) {
        Role role = req.role() == null || req.role().isBlank()
                ? Role.CASHIER
                : Role.valueOf(req.role().toUpperCase());
        UserResponse created = toResponse(
                auth.createUser(req.username(), req.password(), req.fullName(), role));
        audit.record(AuditLog.Action.USER_CREATED, "USER", created.id(),
                created.username() + " as " + created.role());
        return created;
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable String id, @RequestBody UpdateUserRequest req) {
        Role role = req.role() == null || req.role().isBlank()
                ? null
                : Role.valueOf(req.role().toUpperCase());
        UserResponse updated = toResponse(
                auth.updateUser(id, req.fullName(), role, req.active(), req.password()));
        String detail = "updated " + updated.username();
        if (req.active() != null) {
            detail += req.active() ? " · enabled" : " · disabled";
        }
        if (req.password() != null && !req.password().isBlank()) {
            detail += " · password reset";
        }
        audit.record(AuditLog.Action.USER_UPDATED, "USER", id, detail);
        return updated;
    }

    private static UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getFullName(),
                u.getRole().name(), u.isActive(), u.getCreatedAt());
    }
}
