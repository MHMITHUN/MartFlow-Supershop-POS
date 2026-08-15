package com.martflow.api;

import com.martflow.security.Role;
import com.martflow.security.RoleGate;
import com.martflow.suppliers.PurchasingService;
import com.martflow.suppliers.Supplier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Supplier registry (managers run the buying side of the shop). */
@RestController
@RequestMapping("/api/suppliers")
public class SuppliersController {

    private final PurchasingService purchasing;

    public SuppliersController(PurchasingService purchasing) {
        this.purchasing = purchasing;
    }

    public record SupplierResponse(String id, String name, String phone, String contactPerson,
                                    String paymentTerms, String address) {
    }

    public record RegisterSupplierRequest(String name, String phone, String contactPerson,
                                          String paymentTerms, String address) {
    }

    @GetMapping
    public List<SupplierResponse> list() {
        RoleGate.requireAtLeast(Role.MANAGER);
        return purchasing.suppliers().stream().map(SuppliersController::toResponse).toList();
    }

    @GetMapping("/{id}")
    public SupplierResponse get(@PathVariable String id) {
        RoleGate.requireAtLeast(Role.MANAGER);
        return toResponse(purchasing.supplier(id));
    }

    @PostMapping
    public SupplierResponse register(@RequestBody RegisterSupplierRequest req) {
        return toResponse(purchasing.registerSupplier(req.name(), req.phone(),
                req.contactPerson(), req.paymentTerms(), req.address()));
    }

    private static SupplierResponse toResponse(Supplier s) {
        return new SupplierResponse(s.getId(), s.getName(), s.getPhone(), s.getContactPerson(),
                s.getPaymentTerms(), s.getAddress());
    }
}
