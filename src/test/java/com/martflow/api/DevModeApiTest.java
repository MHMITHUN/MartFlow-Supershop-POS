package com.martflow.api;

import com.martflow.dev.ApiCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Developer Mode over HTTP: the developer account gets all three surfaces, every business
 * role (cashier, manager, even the owner) is locked out, and the ApiCatalog matches Spring's
 * actually-registered /api/** routes exactly — both directions, so a new endpoint cannot ship
 * undocumented and a deleted one cannot linger in the explorer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DevModeApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    private String devToken;
    private String cashierToken;
    private String managerToken;
    private String adminToken;

    @BeforeAll
    void login() throws Exception {
        devToken = login("developer", "developer123");
        cashierToken = login("cashier", "cashier123");
        managerToken = login("manager", "manager123");
        adminToken = login("admin", "admin123");
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
    void developerSeesAllThreeSurfaces() throws Exception {
        mvc.perform(auth(get("/api/dev/patterns"), devToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(18))
                .andExpect(jsonPath("$.patterns[0].snippet.code").exists())
                .andExpect(jsonPath("$.patterns[0].live.href").exists());
        mvc.perform(auth(get("/api/dev/endpoints"), devToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].endpoints[0].path").exists());
        mvc.perform(auth(get("/api/dev/system"), devToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persistence.mode").exists())
                .andExpect(jsonPath("$.counts.products").exists())
                .andExpect(jsonPath("$.clock.zone").value("Asia/Dhaka"));
    }

    @Test
    void businessRolesAreLockedOut() throws Exception {
        for (String token : new String[]{cashierToken, managerToken, adminToken}) {
            mvc.perform(auth(get("/api/dev/patterns"), token))
                    .andExpect(status().isForbidden());
            mvc.perform(auth(get("/api/dev/endpoints"), token))
                    .andExpect(status().isForbidden());
            mvc.perform(auth(get("/api/dev/system"), token))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void apiCatalogMatchesSpringRegistrationsExactly() {
        Set<String> registered = new TreeSet<>();
        for (var entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod method = entry.getValue();
            if (method.getBeanType().getPackageName().startsWith("org.springframework")) {
                continue; // framework internals (error controller etc.)
            }
            Set<org.springframework.web.bind.annotation.RequestMethod> verbs =
                    info.getMethodsCondition().getMethods();
            var pathPatterns = info.getPathPatternsCondition();
            if (pathPatterns == null) {
                continue;
            }
            for (org.springframework.web.util.pattern.PathPattern pattern
                    : pathPatterns.getPatterns()) {
                String path = pattern.getPatternString();
                if (path.startsWith("/api")) {
                    for (org.springframework.web.bind.annotation.RequestMethod verb : verbs) {
                        registered.add(verb.name() + " " + path);
                    }
                }
            }
        }
        Set<String> cataloged = ApiCatalog.all().stream()
                .flatMap(group -> group.endpoints().stream())
                .map(e -> e.method() + " " + e.path())
                .collect(Collectors.toCollection(TreeSet::new));

        Set<String> missingFromCatalog = registered.stream()
                .filter(r -> !cataloged.contains(r)).collect(Collectors.toSet());
        Set<String> staleInCatalog = cataloged.stream()
                .filter(c -> !registered.contains(c)).collect(Collectors.toSet());

        org.junit.jupiter.api.Assertions.assertTrue(missingFromCatalog.isEmpty(),
                "Endpoints registered but missing from ApiCatalog: " + missingFromCatalog
                        + " — add them so the explorer (and this guard) stay honest");
        org.junit.jupiter.api.Assertions.assertTrue(staleInCatalog.isEmpty(),
                "Endpoints cataloged but no longer registered: " + staleInCatalog);
        org.junit.jupiter.api.Assertions.assertFalse(registered.isEmpty());
    }
}
