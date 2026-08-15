package com.martflow.api;

import com.martflow.audit.AuditLog;
import com.martflow.audit.AuditService;
import com.martflow.auth.AuthService;
import com.martflow.auth.AuthSession;
import com.martflow.security.RoleContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Login, logout and "who am I" — the only publicly reachable endpoint is login. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final AuditService audit;

    public AuthController(AuthService auth, AuditService audit) {
        this.auth = auth;
        this.audit = audit;
    }

    public record LoginRequest(String username, String password) {
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        try {
            AuthSession session = auth.login(req.username(), req.password());
            audit.recordAttempt(session.username(), AuditLog.Action.LOGIN, "signed in");
            return Map.of(
                    "token", session.token(),
                    "userId", session.userId(),
                    "username", session.username(),
                    "fullName", session.fullName() == null ? "" : session.fullName(),
                    "role", session.role().name());
        } catch (RuntimeException failure) {
            audit.recordAttempt(req.username(), AuditLog.Action.LOGIN_FAILED, failure.getMessage());
            throw failure;
        }
    }

    @PostMapping("/logout")
    public void logout(@org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String header) {
        if (header != null && header.startsWith("Bearer ")) {
            auth.logout(header.substring("Bearer ".length()).trim());
        }
        audit.record(AuditLog.Action.LOGOUT, "AUTH", null, "signed out");
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        var caller = RoleContext.current();
        return Map.of(
                "userId", caller.userId(),
                "username", caller.username(),
                "role", caller.role().name());
    }
}
