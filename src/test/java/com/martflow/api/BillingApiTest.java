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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The full POS flow over HTTP: login as cashier, scan a unit item and 1.25 kg of potatoes,
 * attach the loyalty customer (member pricing), apply SAVE50, take cash, check the receipt
 * numbers, then confirm the stock actually moved.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BillingApiTest {

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
    void fullPosFlowWithHandCheckedNumbers() throws Exception {
        // scan: 2x Teer oil 5L (850x2 = 1700) + 1.25 kg potato (35x1.25 = 43.75)
        mvc.perform(auth(post("/api/bill/lines").contentType("application/json")
                        .content("{\"productId\":\"p01\",\"quantity\":2}"), cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.gross").value(1700.00))
                .andExpect(jsonPath("$.lines[0].kind").value("UNIT"));

        mvc.perform(auth(post("/api/bill/lines").contentType("application/json")
                        .content("{\"productId\":\"w01\",\"weightKg\":1.25}"), cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.gross").value(1743.75))
                .andExpect(jsonPath("$.totals.vat").value(0.0)) // staples + fresh = 0% VAT
                .andExpect(jsonPath("$.lines[1].kind").value("WEIGHED"))
                .andExpect(jsonPath("$.carryBagUnitFee").value(5.00));

        // attach member: 5% off every line -> 1743.75 * 0.95 = 1656.5625 -> 1656.56
        mvc.perform(auth(put("/api/bill/customer").contentType("application/json")
                        .content("{\"customerIdOrPhone\":\"01711111111\"}"), cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.name").value("Nusrat Jahan"))
                .andExpect(jsonPath("$.totals.net").value(1656.56))
                .andExpect(jsonPath("$.totals.discount").value(87.19));

        // coupon SAVE50 takes another 50 off
        mvc.perform(auth(put("/api/bill/coupon").contentType("application/json")
                        .content("{\"code\":\"SAVE50\"}"), cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.coupon").value(50.0))
                .andExpect(jsonPath("$.totals.net").value(1606.56));

        // undo works over HTTP too
        mvc.perform(auth(post("/api/bill/undo"), cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.net").value(1656.56));
        mvc.perform(auth(put("/api/bill/coupon").contentType("application/json")
                        .content("{\"code\":\"SAVE50\"}"), cashierToken))
                .andExpect(status().isOk());

        // tender 2000 cash -> rounded to 1607 (round-off +0.44), change 393
        MvcResult tender = mvc.perform(auth(post("/api/bill/tender").contentType("application/json")
                        .content("{\"tenders\":[{\"type\":\"CASH\",\"amount\":2000}]}"), cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptNo").isNotEmpty())
                .andExpect(jsonPath("$.totals.net").value(1607))
                .andExpect(jsonPath("$.totals.roundOff").value(0.44))
                .andExpect(jsonPath("$.totals.change").value(393))
                .andExpect(jsonPath("$.lines[2].name").value("Round Off"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn();
        String receiptNo = com.jayway.jsonpath.JsonPath.read(
                tender.getResponse().getContentAsString(), "$.receiptNo");

        // reprint is identical
        mvc.perform(auth(get("/api/sales/" + receiptNo), cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptNo").value(receiptNo))
                .andExpect(jsonPath("$.totals.net").value(1607));

        // stock actually moved and persisted
        mvc.perform(auth(get("/api/products/p01"), managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(22));
        mvc.perform(auth(get("/api/products/w01"), managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(78.750));

        // bill cleared for the next customer
        mvc.perform(auth(get("/api/bill"), cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(0));
    }

    @Test
    void twoTillsDoNotShareABill() throws Exception {
        mvc.perform(auth(post("/api/bill/lines").contentType("application/json")
                        .content("{\"productId\":\"p04\",\"quantity\":1}"), cashierToken))
                .andExpect(status().isOk());
        // the manager's token sees a different, empty bill
        mvc.perform(auth(get("/api/bill"), managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(0));
        mvc.perform(auth(post("/api/bill/tender").contentType("application/json")
                        .content("{\"tenders\":[{\"type\":\"CASH\",\"amount\":30}]}"), cashierToken))
                .andExpect(status().isOk());
    }

    @Test
    void salesHistoryIsManagerOnly() throws Exception {
        mvc.perform(auth(get("/api/sales"), cashierToken))
                .andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/sales"), managerToken))
                .andExpect(status().isOk());
    }

    @Test
    void insufficientStockIsRejectedCleanly() throws Exception {
        mvc.perform(auth(post("/api/bill/lines").contentType("application/json")
                        .content("{\"productId\":\"p22\",\"quantity\":1}"), cashierToken)) // stocked 0
                .andExpect(status().isOk()); // scanning is allowed
        mvc.perform(auth(post("/api/bill/tender").contentType("application/json")
                        .content("{\"tenders\":[{\"type\":\"CASH\",\"amount\":100}]}"), cashierToken))
                .andExpect(status().isBadRequest());
        mvc.perform(auth(post("/api/bill/undo"), cashierToken)).andExpect(status().isOk());
    }

    @Test
    void couponValidationEndpoint() throws Exception {
        mvc.perform(auth(post("/api/promotions/validate").contentType("application/json")
                        .content("{\"code\":\"SAVE50\",\"netTotal\":1000}"), cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(50.0));
        mvc.perform(auth(post("/api/promotions/validate").contentType("application/json")
                        .content("{\"code\":\"NOPE\",\"netTotal\":1000}"), cashierToken))
                .andExpect(status().isBadRequest());
    }
}
