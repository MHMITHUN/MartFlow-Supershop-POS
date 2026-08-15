package com.martflow.api;

import com.martflow.api.dto.BillingDtos;
import com.martflow.api.dto.BillingMappers;
import com.martflow.billing.Bill;
import com.martflow.billing.BillingFacade;
import com.martflow.billing.validation.ValidationDtos.TenderRequest;
import com.martflow.pricing.PromotionEngine;
import com.martflow.security.RoleContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The till: every bill operation the cashier performs. The bearer token identifies WHICH till
 * (each login gets its own bill — no more shared global cart).
 */
@RestController
@RequestMapping("/api/bill")
public class BillingController {

    private final BillingFacade billing;
    private final PromotionEngine engine;

    public BillingController(BillingFacade billing, PromotionEngine engine) {
        this.billing = billing;
        this.engine = engine;
    }

    @GetMapping
    public BillingDtos.BillResponse view(@RequestHeader("Authorization") String auth) {
        String token = Tokens.from(auth);
        return response(token);
    }

    @PostMapping("/lines")
    public BillingDtos.BillResponse addLine(@RequestHeader("Authorization") String auth,
                                            @RequestBody BillingDtos.AddLineRequest req) {
        String token = Tokens.from(auth);
        String key = req.productId() != null && !req.productId().isBlank()
                ? req.productId() : req.barcode();
        billing.addLine(token, key, req.quantity(), req.weightKg());
        return response(token);
    }

    @PutMapping("/lines/{index}")
    public BillingDtos.BillResponse updateLine(@RequestHeader("Authorization") String auth,
                                               @PathVariable int index,
                                               @RequestBody BillingDtos.UpdateLineRequest req) {
        String token = Tokens.from(auth);
        billing.updateLine(token, index, req.quantity());
        return response(token);
    }

    @DeleteMapping("/lines/{index}")
    public BillingDtos.BillResponse removeLine(@RequestHeader("Authorization") String auth,
                                               @PathVariable int index) {
        String token = Tokens.from(auth);
        billing.removeLine(token, index);
        return response(token);
    }

    @DeleteMapping
    public BillingDtos.BillResponse clear(@RequestHeader("Authorization") String auth) {
        String token = Tokens.from(auth);
        billing.clearBill(token);
        return response(token);
    }

    @PostMapping("/undo")
    public BillingDtos.BillResponse undo(@RequestHeader("Authorization") String auth) {
        String token = Tokens.from(auth);
        billing.undo(token);
        return response(token);
    }

    @PutMapping("/coupon")
    public BillingDtos.BillResponse coupon(@RequestHeader("Authorization") String auth,
                                           @RequestBody BillingDtos.CouponRequest req) {
        String token = Tokens.from(auth);
        billing.setCoupon(token, req.code());
        return response(token);
    }

    @PutMapping("/customer")
    public BillingDtos.BillResponse customer(@RequestHeader("Authorization") String auth,
                                             @RequestBody BillingDtos.CustomerRequest req) {
        String token = Tokens.from(auth);
        billing.setCustomer(token, req.customerIdOrPhone());
        return response(token);
    }

    @PutMapping("/charges")
    public BillingDtos.BillResponse charges(@RequestHeader("Authorization") String auth,
                                            @RequestBody BillingDtos.ChargesRequest req) {
        String token = Tokens.from(auth);
        billing.setCharges(token, req.carryBags(), req.deliveryFee());
        return response(token);
    }

    /** Takes the money: validation chain + command pipeline, returns the completed sale. */
    @PostMapping("/tender")
    public BillingDtos.SaleResponse tender(@RequestHeader("Authorization") String auth,
                                           @RequestBody BillingDtos.TenderRequest req) {
        String token = Tokens.from(auth);
        var caller = RoleContext.current();
        List<TenderRequest> tenders = req.tenders().stream()
                .map(t -> new TenderRequest(t.type(), t.amount(), t.reference()))
                .toList();
        return BillingMappers.toSaleResponse(billing.tender(token, caller.username(), tenders));
    }

    private BillingDtos.BillResponse response(String token) {
        Bill bill = billing.billOf(token);
        return BillingMappers.toBillResponse(bill, bill.totals(engine), billing.undoDepth(token));
    }
}
