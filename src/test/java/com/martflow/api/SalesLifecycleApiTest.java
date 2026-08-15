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

/**
 * Void, returns and shrinkage over HTTP: the manager voids a receipt (stock restored), a
 * cashier processes a partial return (pro-rata refund), and a damage write-off raises a
 * SHRINKAGE alert.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SalesLifecycleApiTest {

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

    private String sellForCashier(String productId, int quantity) throws Exception {
        mvc.perform(auth(post("/api/bill/lines").contentType("application/json")
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":" + quantity + "}"),
                        cashierToken))
                .andExpect(status().isOk());
        MvcResult tender = mvc.perform(auth(post("/api/bill/tender").contentType("application/json")
                        .content("{\"tenders\":[{\"type\":\"CASH\",\"amount\":5000}]}"), cashierToken))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(tender.getResponse().getContentAsString(), "$.receiptNo");
    }

    @Test
    void voidFlowOverHttp() throws Exception {
        String receipt = sellForCashier("p28", 2); // 2 x Vim bar 40
        mvc.perform(auth(get("/api/products/p28"), managerToken))
                .andExpect(jsonPath("$.stock").value(34)); // 36 - 2

        // cashier cannot void; manager can
        mvc.perform(auth(post("/api/sales/" + receipt + "/void").contentType("application/json")
                        .content("{\"reason\":\"till error\"}"), cashierToken))
                .andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/sales/" + receipt + "/void").contentType("application/json")
                        .content("{\"reason\":\"till error\"}"), managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOIDED"))
                .andExpect(jsonPath("$.voidReason").value("till error"));

        mvc.perform(auth(get("/api/products/p28"), managerToken))
                .andExpect(jsonPath("$.stock").value(36)); // restored
    }

    @Test
    void returnFlowOverHttp() throws Exception {
        String receipt = sellForCashier("p23", 4); // 4 x Lux 70
        mvc.perform(auth(post("/api/sales/" + receipt + "/returns").contentType("application/json")
                        .content("{\"lines\":[{\"lineNo\":1,\"quantity\":2,\"reason\":\"melted\"}],"
                                + "\"refundChannel\":\"CASH\"}"), cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundAmount").value(140.0))
                .andExpect(jsonPath("$.lines[0].quantity").value(2));

        mvc.perform(auth(get("/api/sales/" + receipt), managerToken))
                .andExpect(jsonPath("$.status").value("PARTIALLY_RETURNED"));
        mvc.perform(auth(get("/api/products/p23"), managerToken))
                .andExpect(jsonPath("$.stock").value(42)); // 44 - 4 + 2

        mvc.perform(auth(get("/api/returns"), cashierToken))
                .andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/returns"), managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.receiptNo=='" + receipt + "')]").exists());
    }

    @Test
    void shrinkageWriteOffRaisesAlertAndReducesStock() throws Exception {
        mvc.perform(auth(post("/api/products/p29/adjust").contentType("application/json")
                        .content("{\"reason\":\"DAMAGE\",\"quantity\":2,\"note\":\"bottle leaked in transit\"}"),
                        managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(12)); // 14 - 2

        mvc.perform(auth(get("/api/alerts").param("unreadOnly", "true"), managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type=='SHRINKAGE')]").exists());
    }

    @Test
    void expiringBatchesAreFlaggedOnBoot() throws Exception {
        // seeded chanachur has a batch inside the 14-day window
        mvc.perform(auth(get("/api/alerts"), managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type=='EXPIRY_SOON')]").exists());
    }
}
