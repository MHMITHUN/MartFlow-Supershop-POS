package com.martflow.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The full auth flow through MockMvc (in-memory fallback): real login, real role enforcement —
 * the storefront's client-supplied-role era is gone.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTest {

    @Autowired
    private MockMvc mvc;

    private String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return jsonResponse(result, "token");
    }

    private static String jsonResponse(MvcResult result, String field) throws Exception {
        String body = result.getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$." + field);
    }

    @Test
    void badCredentialsAreRejectedWith401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"WRONG\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"username\":\"ghost\",\"password\":\"whatever\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiRequiresAToken() throws Exception {
        mvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/products").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsTheLoggedInCaller() throws Exception {
        String token = login("cashier", "cashier123");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cashier"))
                .andExpect(jsonPath("$.role").value("CASHIER"));
    }

    @Test
    void threadLocalCallerNeverLeaksBetweenRequests() throws Exception {
        // two sequential requests with different roles on (potentially) the same worker thread
        String cashier = login("cashier", "cashier123");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + cashier))
                .andExpect(jsonPath("$.username").value("cashier"));
        String admin = login("admin", "admin123");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.username").value("admin"));
        // and the first token still resolves to the first user afterwards
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + cashier))
                .andExpect(jsonPath("$.username").value("cashier"));
    }

    @Test
    void cashierCannotDeleteProductsButAdminCan() throws Exception {
        String cashier = login("cashier", "cashier123");
        mvc.perform(delete("/api/products/p30").header("Authorization", "Bearer " + cashier))
                .andExpect(status().isForbidden());

        String admin = login("admin", "admin123");
        mvc.perform(delete("/api/products/p30").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/products/p30").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void cashierCannotCreateProducts() throws Exception {
        String cashier = login("cashier", "cashier123");
        mvc.perform(post("/api/products").header("Authorization", "Bearer " + cashier)
                        .contentType("application/json")
                        .content("{\"type\":\"UNIT\",\"name\":\"Nope\",\"categoryId\":\"staples\","
                                + "\"price\":\"10\",\"stock\":\"1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffManagementIsAdminOnly() throws Exception {
        String manager = login("manager", "manager123");
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + manager))
                .andExpect(status().isForbidden());

        String admin = login("admin", "admin123");
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username=='cashier')].role").value("CASHIER"));
    }

    @Test
    void logoutKillsTheSession() throws Exception {
        String token = login("manager", "manager123");
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
