package com.martflow.api;

import com.martflow.auth.AuthService;
import com.martflow.auth.AuthSession;
import com.martflow.security.Caller;
import com.martflow.security.RoleContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Bearer-token authentication for {@code /api/**}. Everything except the login endpoint, CORS
 * preflights and static assets requires a valid token; the resolved caller lands in the
 * ThreadLocal {@code RoleContext} for the request's duration and is ALWAYS cleared afterwards —
 * Tomcat reuses worker threads, so a leaked caller would leak one user's authority into the
 * next request.
 */
@Component
public class AuthFilter extends OncePerRequestFilter {

    private final AuthService auth;

    public AuthFilter(AuthService auth) {
        this.auth = auth;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            return true; // static SPA assets and friends
        }
        return path.equals("/api/auth/login");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response); // CORS preflight
            return;
        }
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ")
                ? header.substring("Bearer ".length()).trim()
                : null;

        AuthSession session = auth.resolve(token).orElse(null);
        if (session == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"Valid token required\"}");
            return;
        }

        RoleContext.set(new Caller(session.userId(), session.username(), session.role()));
        try {
            chain.doFilter(request, response);
        } finally {
            RoleContext.clear();
        }
    }
}
