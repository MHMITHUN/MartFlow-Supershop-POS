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
 * End-to-end REST test through MockMvc against the in-memory fallback (surefire pins
 * MONGODB_URI to an invalid value, so no real database is ever touched). Logs in as the seeded
 * admin first — everything needs a bearer token since the auth phase.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InventoryApiTest {

    @Autowired
    private MockMvc mvc;

    private String adminToken;

    @BeforeAll
    void loginAdmin() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        adminToken = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.token");
    }

    @Test
    void listsSeededShelfWithVatAndCost() throws Exception {
        mvc.perform(get("/api/products").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='p01')].name").value("Teer Soybean Oil 5L"))
                .andExpect(jsonPath("$[?(@.id=='p01')].costPrice").value(810.0))
                .andExpect(jsonPath("$[?(@.id=='p01')].mrp").value(850.0))
                .andExpect(jsonPath("$[?(@.id=='p01')].vatRatePercent").value(0))
                .andExpect(jsonPath("$[?(@.id=='p17')].vatRatePercent").value(15));
    }

    @Test
    void weighedItemsCarryFractionalStockAndPerUnitPrice() throws Exception {
        // w02 (onion) — w01's stock is mutated by the POS flow test in the shared context
        mvc.perform(get("/api/products/w02").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("WEIGHED"))
                .andExpect(jsonPath("$.unit").value("KG"))
                .andExpect(jsonPath("$.pricePerUnit").value(65.0))
                .andExpect(jsonPath("$.stock").value(45.000));
    }

    @Test
    void barcodeLookupIsTheTillKey() throws Exception {
        mvc.perform(get("/api/products/barcode/8941234500011").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("p01"));
        mvc.perform(get("/api/products/barcode/0000000000000").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void iteratorViewsAreServedServerSide() throws Exception {
        mvc.perform(get("/api/products").param("view", "low_stock")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='w03')]").exists())
                .andExpect(jsonPath("$[?(@.id=='p17')]").exists());

        mvc.perform(get("/api/products").param("view", "expiring").param("days", "14")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='p19')]").exists()); // near-expiry chanachur batch
    }

    @Test
    void combosExposeTheirComponents() throws Exception {
        mvc.perform(get("/api/products/c01").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("COMBO"))
                .andExpect(jsonPath("$.price").value(1190.0))
                .andExpect(jsonPath("$.componentIds[0]").value("p05"));
    }

    @Test
    void createsUnitItemViaFactoryEndpoint() throws Exception {
        String body = """
                {"type":"UNIT","sku":"TST-99","barcode":"8949999999991","name":"Test Spice Mix",
                 "description":"tmp","categoryId":"grocery","unit":"PACK",
                 "costPrice":"40","price":"50","stock":"10","reorderLevel":3}
                """;
        mvc.perform(post("/api/products").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("UNIT"))
                .andExpect(jsonPath("$.vatRatePercent").value(7.5));

        mvc.perform(get("/api/products/barcode/8949999999991").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(10));

        mvc.perform(post("/api/products/p-tst/restock").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content("{\"quantity\":\"5\"}"))
                .andExpect(status().isNotFound()); // generated id, not p-tst — proves 404 mapping
    }

    @Test
    void alertsAndCategoriesEndpointsWork() throws Exception {
        mvc.perform(get("/api/alerts").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/categories").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='staples')].vatRatePercent").value(0));
    }
}
