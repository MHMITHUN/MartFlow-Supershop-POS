package com.martflow.api;

import com.martflow.security.Role;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The DEVELOPER role: logs in like staff, may work the till-side screens (so the pattern demos
 * run live), but never passes a MANAGER or ADMIN gate. Developer Mode endpoints are gated by
 * exact match instead of the ladder — asserted in DevModeApiTest once they exist.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeveloperRoleTest {

    @Autowired
    private MockMvc mvc;

    private String devToken;

    @BeforeAll
    void login() throws Exception {
        devToken = login("developer", "developer123");
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
    void developerLoginWorksAndMeReportsTheRole() throws Exception {
        mvc.perform(auth(get("/api/auth/me"), devToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DEVELOPER"))
                .andExpect(jsonPath("$.username").value("developer"));
    }

    @Test
    void developerWorksTheTillButCannotManage() throws Exception {
        mvc.perform(auth(get("/api/products"), devToken)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/bill"), devToken)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/reports/daily-sales"), devToken)).andExpect(status().isOk());
        mvc.perform(auth(post("/api/products").contentType("application/json")
                        .content("{}"), devToken))
                .andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/sales"), devToken)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/users"), devToken)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/purchase-orders"), devToken)).andExpect(status().isForbidden());
    }

    @Test
    void developerSitsBesideCashierNotAboveManagerOnTheLadder() {
        assertTrue(Role.DEVELOPER.atLeast(Role.CASHIER));   // till-grade business rights
        assertFalse(Role.DEVELOPER.atLeast(Role.MANAGER));  // no management power
        assertFalse(Role.DEVELOPER.atLeast(Role.ADMIN));    // and definitely not the owner's
        assertTrue(Role.MANAGER.atLeast(Role.DEVELOPER));   // staff outrank the developer account
        assertTrue(Role.ADMIN.atLeast(Role.DEVELOPER));
    }
}
