package com.martflow.catalog;

import com.martflow.persistence.proxy.RoleGuardProxy;
import com.martflow.security.Role;
import com.martflow.security.RoleGate;

/**
 * The product repository's write rules (used by {@link RoleGuardProxy}):
 * <ul>
 *   <li><b>creating</b> an item requires MANAGER+ — adding to the assortment is a buying
 *       decision, and this is enforced at the data boundary itself;</li>
 *   <li><b>deleting</b> an item requires ADMIN (the owner);</li>
 *   <li><b>saves of existing items</b> pass — at runtime those are stock movements from
 *       {@code InventoryService} (sales, receipts, adjustments), which are legitimate for every
 *       authenticated role. Catalog edits of existing items are gated one layer up, in the
 *       facade, before any field is touched.</li>
 * </ul>
 */
public final class ProductWritePolicy implements RoleGuardProxy.WritePolicy<Product> {

    @Override
    public void checkSave(Product existing, Product candidate) {
        if (existing == null) {
            RoleGate.requireAtLeast(Role.MANAGER);
        }
    }

    @Override
    public void checkDelete(String id) {
        RoleGate.requireAtLeast(Role.ADMIN);
    }
}
