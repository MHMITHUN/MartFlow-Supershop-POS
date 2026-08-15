package com.martflow.api;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The audit trail over HTTP: failed logins land in the trail, a manager's void is recorded
 * with its reason, and the trail itself is manager-only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuditApiTest {

    @Autowired
    private MockMvc mvc;

    private String cashierToken;
    private String managerToken;

    @BeforeAll
    void login() throws Exception {
        cashierToken = login("cashier", "cashier123");
        managerToken = login("manager", "manager123");
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder auth(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
            String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    @Test
    void failedLoginLandsInTheTrail() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"username\":\"intruder\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(auth(get("/api/audit").param("action", "LOGIN_FAILED"), managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actor").value("intruder"))
                .andExpect(jsonPath("$[0].action").value("LOGIN_FAILED"));
    }

    @Test
    void voidedSaleIsRecordedWithItsReason() throws Exception {
        // cashier rings up one bill
        mvc.perform(auth(post("/api/bill/lines").contentType("application/json")
                        .content("{\"productId\":\"p04\",\"quantity\":1}"), cashierToken))
                .andExpect(status().isOk());
        MvcResult tender = mvc.perform(auth(post("/api/bill/tender").contentType("application/json")
                        .content("{\"tenders\":[{\"type\":\"CASH\",\"amount\":30}]}"), cashierToken))
                .andExpect(status().isOk())
                .andReturn();
        String receiptNo = com.jayway.jsonpath.JsonPath.read(
                tender.getResponse().getContentAsString(), "$.receiptNo");

        // manager voids it with a reason
        mvc.perform(auth(post("/api/sales/" + receiptNo + "/void").contentType("application/json")
                        .content("{\"reason\":\"card chargeback\"}"), managerToken))
                .andExpect(status().isOk());

        mvc.perform(auth(get("/api/audit").param("action", "SALE_VOIDED"), managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actor").value("manager"))
                .andExpect(jsonPath("$[0].targetId").value(receiptNo))
                .andExpect(jsonPath("$[0].detail").value("reason: card chargeback"));
    }

    @Test
    void trailIsManagerOnly() throws Exception {
        mvc.perform(auth(get("/api/audit"), cashierToken))
                .andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/audit").param("from", LocalDate.now().toString()), managerToken))
                .andExpect(status().isOk());
    }
}
