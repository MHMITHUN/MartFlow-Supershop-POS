package com.martflow.persistence.proxy;

import com.martflow.catalog.InventoryCatalog;
import com.martflow.catalog.Product;
import com.martflow.catalog.ProductInput;
import com.martflow.catalog.ProductUnit;
import com.martflow.catalog.ProductWritePolicy;
import com.martflow.catalog.UnitProductFactory;
import com.martflow.persistence.InMemoryProductRepository;
import com.martflow.persistence.Repository;
import com.martflow.security.AccessDeniedException;
import com.martflow.security.Caller;
import com.martflow.security.Role;
import com.martflow.security.RoleContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Protection Proxy at the data boundary: creates need MANAGER+, deletes need ADMIN,
 *  reads and stock-only saves pass for every authenticated role. */
class RoleGuardProxyTest {

    private static Repository<Product> repo;

    @BeforeAll
    static void init() {
        InventoryCatalog.resetForTesting();
        repo = new RoleGuardProxy<>(new InMemoryProductRepository(), Product::getId,
                new ProductWritePolicy());
    }

    @AfterAll
    static void tearDown() {
        InventoryCatalog.resetForTesting();
        RoleContext.clear();
    }

    @AfterEach
    void logout() {
        RoleContext.clear();
    }

    private static void login(Role role) {
        RoleContext.set(new Caller("u-" + role, role.name().toLowerCase(), role));
    }

    private static Product sample(String id) {
        return new UnitProductFactory().create(id, new ProductInput("SKU-" + id, null,
                "Item " + id, null, "staples", null, ProductUnit.PIECE,
                new BigDecimal("10"), new BigDecimal("15"), new BigDecimal("5"), 2));
    }

    @Test
    void cashierCannotCreateCatalogItems() {
        login(Role.CASHIER);
        assertThrows(AccessDeniedException.class, () -> repo.save(sample("px1")));
    }

    @Test
    void managerCanCreateAndCashierCanThenSaveStockOnlyChanges() {
        login(Role.MANAGER);
        Product created = repo.save(sample("px2"));

        login(Role.CASHIER);
        created.consume(BigDecimal.ONE); // a sale
        Product saved = assertDoesNotThrow(() -> repo.save(created)); // stock-only save passes
        assertEquals(0, saved.getStock().compareTo(BigDecimal.valueOf(4)));
    }

    @Test
    void managerCannotDeleteButAdminCan() {
        login(Role.MANAGER);
        repo.save(sample("px3"));
        assertThrows(AccessDeniedException.class, () -> repo.delete("px3"));

        login(Role.ADMIN);
        repo.delete("px3");
        assertTrue(repo.findById("px3").isEmpty());
    }

    @Test
    void readsAreTransparentForEveryRoleIncludingUnauthenticated() {
        login(Role.MANAGER);
        repo.save(sample("px4"));
        login(Role.CASHIER);
        assertEquals("px4", repo.findById("px4").orElseThrow().getId());
        RoleContext.clear();
        assertEquals(1, repo.findAll().stream().filter(p -> p.getId().equals("px4")).count());
    }

    @Test
    void unauthenticatedCallerNeverPassesAGuard() {
        // stock-only saves of EXISTING items are allowed for authenticated roles; the
        // unauthenticated thread must still be stopped from creating anything
        assertThrows(AccessDeniedException.class, () -> repo.save(sample("px5")));
    }
}
