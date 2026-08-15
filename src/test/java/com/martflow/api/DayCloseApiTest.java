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
 * The Z-report endpoints over HTTP: manager-only on all three routes, and the close round trip
 * returns the server-computed variance.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DayCloseApiTest {

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
    void dayCloseIsManagerOnly() throws Exception {
        mvc.perform(auth(get("/api/reports/day-close/preview"), cashierToken))
                .andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/reports/day-close"), cashierToken))
                .andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/reports/day-close").contentType("application/json")
                        .content("{\"countedCash\":100}"), cashierToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerPreviewShowsTheDrawerMath() throws Exception {
        mvc.perform(auth(get("/api/reports/day-close/preview"), managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedDrawerCash").exists())
                .andExpect(jsonPath("$.tenders").exists())
                .andExpect(jsonPath("$.from").exists());
    }

    @Test
    void closeReturnsServerComputedVariance() throws Exception {
        mvc.perform(auth(post("/api/reports/day-close").contentType("application/json")
                        .content("{\"countedCash\":0,\"note\":\"api test close\"}"), managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variance").exists())
                .andExpect(jsonPath("$.closedBy").value("manager"))
                .andExpect(jsonPath("$.countedCash").value(0));
    }
}
