package com.martflow.api;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reports over HTTP: role gating, JSON and CSV formats, dashboard KPIs. */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportsApiTest {

    @Autowired
    private MockMvc mvc;

    private String cashierToken;
    private String managerToken;

    @BeforeAll
    void login() throws Exception {
        cashierToken = login("cashier", "cashier123");
        managerToken = login("manager", "manager123");
        // one clean sale so the reports have data regardless of sibling test classes
        mvc.perform(post("/api/bill/lines").header("Authorization", "Bearer " + cashierToken)
                        .contentType("application/json")
                        .content("{\"productId\":\"p15\",\"quantity\":2}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/bill/tender").header("Authorization", "Bearer " + cashierToken)
                        .contentType("application/json")
                        .content("{\"tenders\":[{\"type\":\"CASH\",\"amount\":500}]}"))
                .andExpect(status().isOk());
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    @Test
    void operationalReportsAreReadableByCashiers() throws Exception {
        mvc.perform(get("/api/reports/low-stock").header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Low Stock (Reorder Worksheet)"))
                .andExpect(jsonPath("$.headers[0]").value("Item"));
        mvc.perform(get("/api/reports/expiry").header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk());
    }

    @Test
    void financialReportsAreManagerOnly() throws Exception {
        mvc.perform(get("/api/reports/profit").header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/vat").header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/profit").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/reports/vat").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalOutputVat").exists());
    }

    @Test
    void csvFormatDownloads() throws Exception {
        mvc.perform(get("/api/reports/daily-sales")
                        .header("Authorization", "Bearer " + managerToken)
                        .param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    if (!body.startsWith("Date,Bills")) {
                        throw new AssertionError("CSV header missing: " + body.substring(0, 40));
                    }
                });
    }

    @Test
    void unknownReportIsA400() throws Exception {
        mvc.perform(get("/api/reports/nope").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dashboardServesTodayKpis() throws Exception {
        mvc.perform(get("/api/reports/dashboard").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bills").exists())
                .andExpect(jsonPath("$.netSales").exists())
                .andExpect(jsonPath("$.avgBasket").exists())
                .andExpect(jsonPath("$.lowStockCount").exists())
                .andExpect(jsonPath("$.expiringCount").exists());
        mvc.perform(get("/api/reports/dashboard").header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden());
    }
}
