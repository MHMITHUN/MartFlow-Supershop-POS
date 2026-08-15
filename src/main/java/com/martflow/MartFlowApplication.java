package com.martflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;

/**
 * Spring Boot entry point.
 *
 * <p>This class (plus {@code com.martflow.api} controllers and {@code com.martflow.app.AppConfig}) is the
 * ONLY place Spring is used. Every design pattern lives in plain POJO domain code with no framework
 * annotations. See design decision D9.
 *
 * <p>Mongo auto-configuration is EXCLUDED: we own the {@code MongoClient} via the
 * {@code DatabaseConnection} singleton (raw driver), so Spring must not create its own client.
 */
@SpringBootApplication(exclude = MongoAutoConfiguration.class)
public class MartFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(MartFlowApplication.class, args);
        System.out.println("\n" +
                "==========================================================\n" +
                "  🚀 MartFlow is running — Supershop Retail Management Suite\n" +
                "  👉 Open the till: http://localhost:8080\n" +
                "  👤 Demo logins: admin/admin123 · manager/manager123 · cashier/cashier123\n" +
                "==========================================================\n");
    }
}
