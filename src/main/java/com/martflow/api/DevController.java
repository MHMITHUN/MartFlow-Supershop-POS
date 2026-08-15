package com.martflow.api;

import com.martflow.app.MartFlowFacade;
import com.martflow.auth.TokenStore;
import com.martflow.common.TimeSource;
import com.martflow.dev.ApiCatalog;
import com.martflow.dev.PatternCatalog;
import com.martflow.persistence.Config;
import com.martflow.persistence.DatabaseConnection;
import com.martflow.persistence.Repositories;
import com.martflow.security.Role;
import com.martflow.security.RoleGate;
import org.springframework.boot.SpringBootVersion;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Developer Mode: the pattern catalog, the API explorer's endpoint data and live system
 * diagnostics. Every route demands the DEVELOPER role by exact match — business staff never
 * see these, and the developer never sees business management screens.
 */
@RestController
@RequestMapping("/api/dev")
public class DevController {

    private final TokenStore tokens;
    private final MartFlowFacade shop;

    public DevController(TokenStore tokens, MartFlowFacade shop) {
        this.tokens = tokens;
        this.shop = shop;
    }

    @GetMapping("/patterns")
    public Map<String, Object> patterns() {
        RoleGate.requireRole(Role.DEVELOPER);
        List<PatternCatalog.Pattern> patterns = PatternCatalog.all();
        return Map.of("count", patterns.size(), "patterns", patterns);
    }

    @GetMapping("/endpoints")
    public Map<String, Object> endpoints() {
        RoleGate.requireRole(Role.DEVELOPER);
        return Map.of("groups", ApiCatalog.all());
    }

    @GetMapping("/system")
    public Map<String, Object> system() {
        RoleGate.requireRole(Role.DEVELOPER);

        boolean mongo = Repositories.isMongo();
        Map<String, Object> persistence = new LinkedHashMap<>();
        persistence.put("mode", mongo ? "MONGO" : "IN_MEMORY");
        persistence.put("database", mongo ? Config.getMongoDb() : "(in-memory fallback)");
        persistence.put("connectedAt", mongo && DatabaseConnection.getConnectedAt() != null
                ? DatabaseConnection.getConnectedAt().toString() : null);
        persistence.put("warning", mongo ? null : "restart loses all data — set MONGODB_URI to persist");

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("products", Repositories.products().findAll().size());
        counts.put("sales", Repositories.sales().findAll().size());
        counts.put("returns", Repositories.returns().findAll().size());
        counts.put("customers", Repositories.customers().findAll().size());
        counts.put("promotions", Repositories.promotions().findAll().size());
        counts.put("suppliers", Repositories.suppliers().findAll().size());
        counts.put("purchaseOrders", Repositories.purchaseOrders().findAll().size());
        counts.put("templates", Repositories.orderTemplates().findAll().size());
        counts.put("auditEntries", Repositories.auditLogs().findAll().size());
        counts.put("dayCloses", Repositories.dayCloses().findAll().size());
        counts.put("users", Repositories.users().findAll().size());

        Map<String, Object> sessions = new LinkedHashMap<>();
        sessions.put("activeTokens", tokens.activeCount());

        Map<String, Object> alerts = new LinkedHashMap<>();
        alerts.put("total", shop.alerts(false).size());
        alerts.put("unread", shop.alerts(true).size());

        Map<String, Object> clock = new LinkedHashMap<>();
        clock.put("now", TimeSource.now().toString());
        clock.put("zone", TimeSource.ZONE.toString());

        Map<String, Object> app = new LinkedHashMap<>();
        app.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        app.put("javaVersion", System.getProperty("java.version"));
        app.put("springBootVersion", SpringBootVersion.getVersion());

        Map<String, Object> system = new LinkedHashMap<>();
        system.put("persistence", persistence);
        system.put("counts", counts);
        system.put("sessions", sessions);
        system.put("alerts", alerts);
        system.put("clock", clock);
        system.put("app", app);
        return system;
    }
}
