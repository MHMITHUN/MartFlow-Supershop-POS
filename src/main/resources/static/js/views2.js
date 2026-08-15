/** MartFlow views, part 2: inventory, purchases, customers, returns. */
Object.assign(Views, (() => {

    // ============================== INVENTORY ==============================

    async function inventory(host) {
        const canManage = API.role() === "MANAGER" || API.role() === "ADMIN";
        let categories = [];

        host.innerHTML = `
        <div class="d-flex flex-wrap gap-2 align-items-center mt-2 mb-3">
          <h4 class="mf-page-title mb-0 me-auto"><i class="bi bi-boxes"></i> Inventory</h4>
          <select class="form-select form-select-sm mf-w-auto" id="invView">
            <option value="">All items</option>
            <option value="in_stock">In stock</option>
            <option value="low_stock">Low stock</option>
            <option value="expiring">Expiring ≤14d</option>
          </select>
          <select class="form-select form-select-sm mf-w-auto" id="invCategory"></select>
          <input class="form-control form-control-sm mf-w-auto" id="invSearch" placeholder="Name / SKU / barcode">
          ${canManage ? `<button class="btn mf-btn-primary btn-sm" id="btnAddItem"><i class="bi bi-plus-lg"></i> Add item</button>` : ""}
        </div>
        <div class="mf-panel p-3" id="invTable">loading…</div>`;

        try { categories = await API.categories(); } catch (ignored) { }
        UI.$("#invCategory").innerHTML = `<option value="">All categories</option>` +
            categories.map((c) => `<option value="${UI.esc(c.id)}">${UI.esc(c.name.split(" —")[0])} (VAT ${c.vatRatePercent}%)</option>`).join("");

        let timer = null;
        const load = async () => {
            const query = {
                view: UI.$("#invView").value || undefined,
                categoryId: UI.$("#invCategory").value || undefined,
                q: UI.$("#invSearch").value || undefined
            };
            try {
                const products = await API.products(query);
                UI.$("#invTable").innerHTML = UI.table(
                    ["Item", "Type", "Category", "Cost", "Price", "Stock", "Reorder", "Batches", "Actions"],
                    products.map((p) => `
                      <tr>
                        <td><b>${UI.esc(p.name)}</b><div class="text-secondary small">${UI.esc(p.sku || "")} ${p.barcode ? "· " + UI.esc(p.barcode) : ""}</div></td>
                        <td><span class="badge text-bg-${p.type === "WEIGHED" ? "info" : p.type === "COMBO" ? "warning" : "secondary"}">${UI.esc(p.type)}</span></td>
                        <td class="small">${UI.esc(p.categoryName || "")}</td>
                        <td>${UI.money(p.costPrice)}</td>
                        <td><b>${UI.money(p.price)}</b>${p.type === "WEIGHED" ? "<div class='small text-secondary'>per kg</div>" : ""}</td>
                        <td class="${p.lowStock ? "text-danger fw-bold" : ""}">${UI.qty(p.stock)}</td>
                        <td>${p.reorderLevel}</td>
                        <td class="small">${(p.batches || []).map((b) =>
                            `${UI.esc(b.batchNo)}${b.expiry ? " → " + b.expiry : ""}`).join("<br>")}</td>
                        <td class="text-nowrap">
                          ${canManage ? `
                            <button class="btn btn-sm mf-btn-ghost" data-restock="${UI.esc(p.id)}" title="Restock"><i class="bi bi-plus-square"></i></button>
                            <button class="btn btn-sm mf-btn-ghost" data-adjust="${UI.esc(p.id)}" title="Damage/loss"><i class="bi bi-dash-square"></i></button>
                            <button class="btn btn-sm mf-btn-ghost" data-edit='${UI.esc(JSON.stringify(p))}' title="Edit"><i class="bi bi-pencil"></i></button>
                            ${API.role() === "ADMIN" ? `<button class="btn btn-sm mf-btn-ghost text-danger" data-del="${UI.esc(p.id)}" title="Delete"><i class="bi bi-trash"></i></button>` : ""}` : "—"}
                        </td>
                      </tr>`).join(""), "No items match these filters");
                UI.$$("[data-restock]").forEach((b) => b.addEventListener("click", () => restockModal(b.dataset.restock)));
                UI.$$("[data-adjust]").forEach((b) => b.addEventListener("click", () => adjustModal(b.dataset.adjust)));
                UI.$$("[data-del]").forEach((b) => b.addEventListener("click", async () => {
                    if (!window.confirm("Delete this item from the catalog?")) return;
                    try { await API.deleteProduct(b.dataset.del); UI.ok("Deleted"); load(); }
                    catch (err) { UI.fail(err.message); }
                }));
                UI.$$("[data-edit]").forEach((b) => b.addEventListener("click", () => editModal(JSON.parse(b.dataset.edit))));
            } catch (err) { UI.fail(err.message); }
        };

        ["#invView", "#invCategory"].forEach((sel) => {
            const el = UI.$(sel);
            if (el) el.addEventListener("change", load);
        });
        const invSearch = UI.$("#invSearch");
        if (invSearch) invSearch.addEventListener("input", () => { clearTimeout(timer); timer = setTimeout(load, 250); });
        const btnAddItem = UI.$("#btnAddItem");
        if (btnAddItem) btnAddItem.addEventListener("click", () => editModal(null));
        // Deep links like #/inventory?view=low_stock (dashboard "Reorder worksheet") preselect the view.
        const preset = Router.query().view;
        if (preset && UI.$(`#invView option[value="${preset}"]`)) UI.$("#invView").value = preset;
        load();

        function restockModal(id) {
            const inst = UI.modal("Restock (manual)", `
                <div class="mb-3"><label class="form-label">Quantity</label>
                  <input type="number" min="0.001" step="0.001" class="form-control" id="rsQty" value="10"></div>
                <div class="row g-2">
                  <div class="col"><label class="form-label">Batch no (optional)</label><input class="form-control" id="rsBatch"></div>
                  <div class="col"><label class="form-label">Expiry (optional)</label><input type="date" class="form-control" id="rsExpiry"></div>
                </div>`,
                `<button class="btn mf-btn-primary" id="rsSave">Restock</button>`);
            UI.$("#rsSave").addEventListener("click", async () => {
                try {
                    await API.restock(id, {
                        quantity: UI.$("#rsQty").value,
                        batchNo: UI.$("#rsBatch").value || null,
                        expiry: UI.$("#rsExpiry").value || null
                    });
                    inst.hide(); UI.ok("Stock added"); load();
                } catch (err) { UI.fail(err.message); }
            });
        }

        function adjustModal(id) {
            const inst = UI.modal("Shrinkage / count correction", `
                <div class="mb-3"><label class="form-label">Reason</label>
                  <select class="form-select" id="adjReason">
                    <option>DAMAGE</option><option>LOSS</option><option>THEFT</option><option>COUNT</option>
                  </select></div>
                <div class="mb-3"><label class="form-label">Quantity (negative adds back)</label>
                  <input type="number" step="0.001" class="form-control" id="adjQty" value="1"></div>
                <div class="mb-3"><label class="form-label">Note</label><input class="form-control" id="adjNote"></div>`,
                `<button class="btn mf-btn-primary" id="adjSave">Record</button>`);
            UI.$("#adjSave").addEventListener("click", async () => {
                try {
                    await API.adjustStock(id, {
                        reason: UI.$("#adjReason").value,
                        quantity: UI.$("#adjQty").value,
                        note: UI.$("#adjNote").value
                    });
                    inst.hide(); UI.ok("Adjustment recorded"); load();
                } catch (err) { UI.fail(err.message); }
            });
        }

        function editModal(p) {
            const isNew = !p;
            const inst = UI.modal(isNew ? "Add item" : "Edit " + p.name, `
                <div class="row g-3">
                  <div class="col-md-6"><label class="form-label">Name</label>
                    <input class="form-control" id="eName" value="${p ? UI.esc(p.name) : ""}"></div>
                  <div class="col-md-3"><label class="form-label">Type</label>
                    <select class="form-select" id="eType" ${isNew ? "" : "disabled"}>
                      <option>UNIT</option><option>WEIGHED</option>${isNew ? "<option>COMBO</option>" : ""}
                    </select></div>
                  <div class="col-md-3 mf-not-combo"><label class="form-label">Unit</label>
                    <select class="form-select" id="eUnit">
                      <option>PIECE</option><option>PACK</option><option>KG</option><option>LITRE</option>
                    </select></div>
                  <div class="col-md-4"><label class="form-label">Category</label>
                    <select class="form-select" id="eCat">${categories.map((c) =>
                        `<option value="${UI.esc(c.id)}" ${p && p.categoryId === c.id ? "selected" : ""}>${UI.esc(c.name.split(" —")[0])}</option>`).join("")}</select></div>
                  <div class="col-md-4 mf-not-combo"><label class="form-label">Cost (BDT)</label>
                    <input type="number" step="0.01" class="form-control" id="eCost" value="${p ? p.costPrice : ""}"></div>
                  <div class="col-md-4 mf-not-combo"><label class="form-label">${p && p.type === "WEIGHED" ? "Price / kg" : "MRP"}</label>
                    <input type="number" step="0.01" class="form-control" id="ePrice" value="${p ? p.price : ""}"></div>
                  <div class="col-md-4 mf-not-combo"><label class="form-label">Stock</label>
                    <input type="number" step="0.001" class="form-control" id="eStock" value="${p ? p.stock : 0}" ${isNew ? "" : "disabled"}></div>
                  <div class="col-md-4 mf-not-combo"><label class="form-label">Reorder level</label>
                    <input type="number" class="form-control" id="eReorder" value="${p ? p.reorderLevel : 5}"></div>
                  <div class="col-md-4"><label class="form-label">Barcode</label>
                    <input class="form-control" id="eBarcode" value="${p && p.barcode ? UI.esc(p.barcode) : ""}"></div>
                  <div class="col-md-4"><label class="form-label">SKU</label>
                    <input class="form-control" id="eSku" value="${p && p.sku ? UI.esc(p.sku) : ""}"></div>
                  <div class="col-12 d-none" id="eComboBlock">
                    <label class="form-label">Bundle components (pick at least one — Composite)</label>
                    <div class="border rounded p-2 mb-2 mf-combo-picker" id="eComboList">
                      <span class="text-secondary small">loading products…</span>
                    </div>
                    <div class="row g-2">
                      <div class="col-md-4"><label class="form-label">Fixed bundle price (BDT)</label>
                        <input type="number" step="0.01" min="0" class="form-control" id="eComboPrice"></div>
                      <div class="col-md-8"><label class="form-label">Description (optional)</label>
                        <input class="form-control" id="eComboDesc" placeholder="e.g. Eid hamper — oil, rice, sugar"></div>
                    </div>
                  </div>
                </div>`,
                `<button class="btn mf-btn-primary" id="eSave">${isNew ? "Create" : "Save"}</button>`);
            const syncCombo = () => {
                const combo = UI.$("#eType").value === "COMBO";
                UI.$$(".mf-not-combo", UI.$("#modalHost")).forEach((el) => el.classList.toggle("d-none", combo));
                UI.$("#eComboBlock").classList.toggle("d-none", !combo);
            };
            UI.$("#eType").addEventListener("change", () => {
                const weighed = UI.$("#eType").value === "WEIGHED";
                UI.$("#eUnit").value = weighed ? "KG" : "PIECE";
                syncCombo();
            });
            syncCombo();
            if (isNew) {
                API.products().then((list) => {
                    UI.$("#eComboList").innerHTML = list.filter((x) => x.type !== "COMBO")
                        .map((x) => `
                          <label class="d-flex justify-content-between align-items-center gap-2 py-1">
                            <span><input type="checkbox" class="form-check-input me-2 e-combo-part" value="${UI.esc(x.id)}">
                              ${UI.esc(x.name)}</span>
                            <span class="text-secondary small">${UI.money(x.price)}</span>
                          </label>`).join("");
                }).catch((err) => {
                    UI.$("#eComboList").innerHTML = `<span class="text-danger small">${UI.esc(err.message)}</span>`;
                });
            }
            UI.$("#eSave").addEventListener("click", async () => {
                try {
                    if (isNew && UI.$("#eType").value === "COMBO") {
                        const componentIds = UI.$$(".e-combo-part", UI.$("#modalHost"))
                            .filter((box) => box.checked).map((box) => box.value);
                        const fixedPrice = parseFloat(UI.$("#eComboPrice").value || "0");
                        if (!componentIds.length) { UI.fail("Pick at least one component for the combo"); return; }
                        if (!(fixedPrice > 0)) { UI.fail("Fixed bundle price must be greater than zero"); return; }
                        await API.createProduct({
                            type: "COMBO",
                            name: UI.$("#eName").value,
                            categoryId: UI.$("#eCat").value,
                            componentIds,
                            fixedPrice,
                            description: UI.$("#eComboDesc").value || null,
                            barcode: UI.$("#eBarcode").value || null,
                            sku: UI.$("#eSku").value || null
                        });
                    } else if (isNew) {
                        await API.createProduct({
                            type: UI.$("#eType").value,
                            name: UI.$("#eName").value,
                            categoryId: UI.$("#eCat").value,
                            unit: UI.$("#eUnit").value,
                            costPrice: UI.$("#eCost").value,
                            price: UI.$("#ePrice").value,
                            stock: UI.$("#eStock").value || "0",
                            reorderLevel: parseInt(UI.$("#eReorder").value || "0", 10),
                            barcode: UI.$("#eBarcode").value || null,
                            sku: UI.$("#eSku").value || null
                        });
                    } else {
                        await API.updateProduct(p.id, {
                            name: UI.$("#eName").value,
                            costPrice: UI.$("#eCost").value,
                            price: UI.$("#ePrice").value,
                            reorderLevel: parseInt(UI.$("#eReorder").value || "0", 10)
                        });
                    }
                    inst.hide();
                    UI.ok(isNew ? "Item created (Factory Method → " + UI.$("#eType").value + ")" : "Saved");
                    load();
                } catch (err) { UI.fail(err.message); }
            });
        }
    }

    // ============================== PURCHASES ==============================

    async function purchases(host) {
        host.innerHTML = `
        <h4 class="mf-page-title mt-2"><i class="bi bi-truck"></i> Purchases</h4>
        <ul class="nav nav-tabs mt-2">
          <li class="nav-item"><a class="nav-link active" data-bs-toggle="tab" href="#poTab">Purchase orders</a></li>
          <li class="nav-item"><a class="nav-link" data-bs-toggle="tab" href="#supTab">Suppliers</a></li>
          <li class="nav-item"><a class="nav-link" data-bs-toggle="tab" href="#tplTab">Standing templates</a></li>
        </ul>
        <div class="tab-content mf-panel p-3 border-top-0">
          <div class="tab-pane fade show active" id="poTab">
            <button class="btn mf-btn-primary btn-sm mb-3" id="btnNewPo"><i class="bi bi-plus-lg"></i> New draft PO</button>
            <div id="poList">loading…</div>
          </div>
          <div class="tab-pane fade" id="supTab">
            <button class="btn mf-btn-primary btn-sm mb-3" id="btnNewSupplier"><i class="bi bi-person-plus"></i> Add supplier</button>
            <div id="supList"></div>
          </div>
          <div class="tab-pane fade" id="tplTab"><div id="tplList"></div></div>
        </div>`;

        loadPos(); loadSuppliers(); loadTemplates();

        async function loadPos() {
            try {
                const orders = await API.purchaseOrders();
                UI.$("#poList").innerHTML = UI.table(
                    ["PO", "Supplier", "Status", "Lines", "Ordered", "Payables", "Actions"],
                    orders.map((po) => `
                      <tr>
                        <td><b>${UI.esc(po.poNo)}</b><div class="small text-secondary">${Views._escDate(po.createdAt)}</div></td>
                        <td>${UI.esc(po.supplierId)}</td>
                        <td>${UI.poBadge(po.status)}</td>
                        <td>${po.lines.length}</td>
                        <td>${UI.money(po.orderedTotal)}</td>
                        <td class="${Number(po.payables) > 0 ? "text-danger fw-bold" : ""}">${UI.money(po.payables)}</td>
                        <td class="text-nowrap">
                          <button class="btn btn-sm mf-btn-ghost" data-open="${UI.esc(po.poNo)}"><i class="bi bi-eye"></i></button>
                        </td>
                      </tr>`).join(""));
                UI.$$("[data-open]").forEach((b) => b.addEventListener("click", () => poModal(b.dataset.open)));
            } catch (err) { UI.fail(err.message); }
        }

        async function poModal(poNo) {
            try {
                const po = await API.purchaseOrder(poNo);
                const inst = UI.modal(`${UI.esc(po.poNo)} ${UI.poBadge(po.status)}`, `
                    <div class="mb-2 text-secondary small">
                      Supplier <b>${UI.esc(po.supplierId)}</b> · created ${Views._escDate(po.createdAt)}
                      ${po.cancelReason ? "· cancelled: " + UI.esc(po.cancelReason) : ""}
                    </div>
                    ${UI.table(["Item", "Ordered", "Received", "Unit cost", "Line total"],
                        po.lines.map((l) => `
                          <tr><td>${UI.esc(l.name)}</td><td>${UI.qty(l.orderedQty)}</td>
                          <td>${UI.qty(l.receivedQty)}</td><td>${UI.money(l.unitCost)}</td>
                          <td>${UI.money(l.lineTotal)}</td></tr>`).join(""))}
                    <div class="d-flex justify-content-between mt-2">
                      <span>Ordered total</span><b>${UI.money(po.orderedTotal)}</b></div>
                    <div class="d-flex justify-content-between">
                      <span>Paid</span><b>${UI.money(Number(po.orderedTotal) - Number(po.payables))}</b></div>
                    <div class="d-flex justify-content-between">
                      <span>Payables</span><b class="${Number(po.payables) > 0 ? "text-danger" : ""}">${UI.money(po.payables)}</b></div>
                    ${po.payments.length ? "<h6 class='mt-3'>Payments</h6>" + UI.table(["Amount", "Method", "Note", "At"],
                        po.payments.map((p) => `<tr><td>${UI.money(p.amount)}</td><td>${UI.esc(p.method)}</td><td>${UI.esc(p.note || "")}</td><td class="small">${Views._escDate(p.at)}</td></tr>`).join("")) : ""}`,
                    poActionsHtml(po));
                bindPoActions(inst, po, loadPos);
            } catch (err) { UI.fail(err.message); }
        }

        function poActionsHtml(po) {
            const buttons = [];
            if (po.status === "DRAFT") buttons.push(`<button class="btn mf-btn-primary" data-act="submit"><i class="bi bi-send"></i> Submit to supplier</button>`);
            if (po.status === "ORDERED" || po.status === "PARTIALLY_RECEIVED")
                buttons.push(`<button class="btn mf-btn-primary" data-act="receive"><i class="bi bi-truck"></i> Receive goods (GRN)</button>`);
            if (!["CLOSED", "CANCELLED"].includes(po.status))
                buttons.push(`<button class="btn mf-btn-ghost" data-act="pay"><i class="bi bi-cash"></i> Record payment</button>`);
            if (po.status === "RECEIVED") buttons.push(`<button class="btn mf-btn-primary" data-act="close"><i class="bi bi-check2-all"></i> Close PO</button>`);
            if (["DRAFT", "ORDERED", "PARTIALLY_RECEIVED"].includes(po.status))
                buttons.push(`<button class="btn btn-outline-danger" data-act="cancel"><i class="bi bi-x-circle"></i> Cancel</button>`);
            if (!["CLOSED", "CANCELLED"].includes(po.status) && po.lines.length)
                buttons.push(`<button class="btn mf-btn-ghost" data-act="template"><i class="bi bi-bookmark-plus"></i> Save as standing template</button>`);
            return buttons.join(" ");
        }

        function bindPoActions(inst, po, reload) {
            UI.$$("[data-act]", UI.$("#modalHost")).forEach((b) => b.addEventListener("click", async () => {
                const act = b.dataset.act;
                try {
                    if (act === "submit") { await API.poAction(po.poNo, "submit"); UI.ok("Submitted — State: DRAFT → ORDERED"); inst.hide(); reload(); }
                    if (act === "close") { await API.poAction(po.poNo, "close"); UI.ok("Closed"); inst.hide(); reload(); }
                    if (act === "cancel") {
                        const reason = window.prompt("Cancellation reason:");
                        if (!reason) return;
                        await API.poAction(po.poNo, "cancel", { reason });
                        UI.ok("Cancelled"); inst.hide(); reload();
                    }
                    if (act === "pay") {
                        const amount = window.prompt("Payment amount (BDT):", String(Math.max(0, Number(po.payables))));
                        if (!amount) return;
                        const method = window.prompt("Method (CASH/BKASH/BANK):", "CASH") || "CASH";
                        await API.poAction(po.poNo, "payments", { amount, method, note: "paid via purchases board" });
                        UI.ok("Payment recorded"); inst.hide(); reload();
                    }
                    if (act === "receive") {
                        inst.hide();
                        receiveModal(po, reload);
                    }
                    if (act === "template") {
                        const name = window.prompt("Standing template name:", po.poNo + " weekly restock");
                        if (!name) return;
                        await API.savePoTemplate({
                            name,
                            supplierId: po.supplierId,
                            lines: po.lines.map((l) => ({ productId: l.productId, quantity: Number(l.orderedQty) }))
                        });
                        UI.ok("Template saved — clone it from the Standing templates tab");
                        inst.hide();
                    }
                } catch (err) { UI.fail(err.message); }
            }));
        }

        function receiveModal(po, reload) {
            const inst = UI.modal("Receive goods — " + UI.esc(po.poNo), `
                <p class="text-secondary small">Enter what physically arrived. Batch + expiry land on the item; a new unit cost updates the profit baseline.</p>
                ${po.lines.map((l, i) => `
                  <div class="row g-2 mb-2 align-items-end" data-line="${i}">
                    <div class="col-md-4"><b>${UI.esc(l.name)}</b><div class="small text-secondary">outstanding ${UI.qty(Number(l.orderedQty) - Number(l.receivedQty))}</div></div>
                    <div class="col-md-2"><label class="form-label small">Qty</label><input type="number" step="0.001" min="0" class="form-control form-control-sm" data-grn-qty="${l.productId}" value="0"></div>
                    <div class="col-md-2"><label class="form-label small">Batch</label><input class="form-control form-control-sm" data-grn-batch="${l.productId}"></div>
                    <div class="col-md-2"><label class="form-label small">Expiry</label><input type="date" class="form-control form-control-sm" data-grn-expiry="${l.productId}"></div>
                    <div class="col-md-2"><label class="form-label small">Unit cost</label><input type="number" step="0.01" class="form-control form-control-sm" data-grn-cost="${l.productId}" value="${l.unitCost}"></div>
                  </div>`).join("")}`,
                `<button class="btn mf-btn-primary" id="grnSave"><i class="bi bi-truck"></i> Put on shelf</button>`);
            UI.$("#grnSave").addEventListener("click", async () => {
                const lines = [];
                po.lines.forEach((l) => {
                    const qty = parseFloat(UI.$(`[data-grn-qty="${l.productId}"]`).value || "0");
                    if (qty > 0) {
                        lines.push({
                            productId: l.productId,
                            quantity: qty,
                            batchNo: UI.$(`[data-grn-batch="${l.productId}"]`).value || null,
                            expiry: UI.$(`[data-grn-expiry="${l.productId}"]`).value || null,
                            unitCost: UI.$(`[data-grn-cost="${l.productId}"]`).value || null
                        });
                    }
                });
                try {
                    const updated = await API.poAction(po.poNo, "receive", { lines });
                    inst.hide();
                    UI.ok("Goods received — State: → " + updated.status);
                    reload();
                } catch (err) { UI.fail(err.message); }
            });
        }

        UI.$("#btnNewPo").addEventListener("click", async () => {
            try {
                const suppliers = await API.suppliers();
                const products = await API.products();
                const inst = UI.modal("New draft purchase order", `
                    <div class="mb-3"><label class="form-label">Supplier</label>
                      <select class="form-select" id="poSupplier">${suppliers.map((s) =>
                        `<option value="${UI.esc(s.id)}">${UI.esc(s.name)} (${UI.esc(s.paymentTerms || "")})</option>`).join("")}</select></div>
                    <div id="poLineRows"></div>
                    <button class="btn btn-sm mf-btn-ghost" id="poAddLine"><i class="bi bi-plus"></i> Add line</button>`,
                    `<button class="btn mf-btn-primary" id="poCreate">Create draft</button>`);
                const rows = [];
                const renderRows = () => {
                    UI.$("#poLineRows").innerHTML = rows.map((r, i) => `
                        <div class="row g-2 mb-2">
                          <div class="col-7"><select class="form-select form-select-sm" data-item="${i}">
                            ${products.map((p) => `<option value="${UI.esc(p.id)}" ${r.productId === p.id ? "selected" : ""}>${UI.esc(p.name)}</option>`).join("")}
                          </select></div>
                          <div class="col-3"><input type="number" step="0.001" min="0" class="form-control form-control-sm" placeholder="qty" value="${r.quantity || ""}" data-qty="${i}"></div>
                          <div class="col-2"><button class="btn btn-outline-danger btn-sm" data-rm="${i}"><i class="bi bi-x"></i></button></div>
                        </div>`).join("");
                    UI.$$("[data-item]").forEach((sel) => sel.addEventListener("change", () => { rows[Number(sel.dataset.item)].productId = sel.value; }));
                    UI.$$("[data-qty]").forEach((inp) => inp.addEventListener("input", () => { rows[Number(inp.dataset.qty)].quantity = inp.value; }));
                    UI.$$("[data-rm]").forEach((btn) => btn.addEventListener("click", () => { rows.splice(Number(btn.dataset.rm), 1); renderRows(); }));
                };
                rows.push({ productId: products.length ? products[0].id : null, quantity: "" });
                renderRows();
                UI.$("#poAddLine").addEventListener("click", () => { rows.push({ productId: products.length ? products[0].id : null, quantity: "" }); renderRows(); });
                UI.$("#poCreate").addEventListener("click", async () => {
                    try {
                        const po = await API.createPo({
                            supplierId: UI.$("#poSupplier").value,
                            lines: rows.filter((r) => r.productId && parseFloat(r.quantity) > 0)
                                .map((r) => ({ productId: r.productId, quantity: parseFloat(r.quantity) }))
                        });
                        inst.hide(); UI.ok("Draft created: " + po.poNo); loadPos();
                    } catch (err) { UI.fail(err.message); }
                });
            } catch (err) { UI.fail(err.message); }
        });

        async function loadSuppliers() {
            try {
                const suppliers = await API.suppliers();
                UI.$("#supList").innerHTML = UI.table(["Supplier", "Contact", "Phone", "Terms", "Address"],
                    suppliers.map((s) => `<tr><td><b>${UI.esc(s.name)}</b><div class="small text-secondary">${UI.esc(s.id)}</div></td>
                        <td>${UI.esc(s.contactPerson || "")}</td><td>${UI.esc(s.phone || "")}</td>
                        <td>${UI.esc(s.paymentTerms || "")}</td><td class="small">${UI.esc(s.address || "")}</td></tr>`).join(""));
            } catch (err) { UI.fail(err.message); }
        }
        UI.$("#btnNewSupplier").addEventListener("click", () => {
            const inst = UI.modal("Add supplier", `
                <div class="row g-3">
                  <div class="col-md-6"><label class="form-label">Name</label><input class="form-control" id="supName"></div>
                  <div class="col-md-6"><label class="form-label">Contact person</label><input class="form-control" id="supContact"></div>
                  <div class="col-md-4"><label class="form-label">Phone</label><input class="form-control" id="supPhone"></div>
                  <div class="col-md-4"><label class="form-label">Payment terms</label><input class="form-control" id="supTerms" placeholder="Net 15"></div>
                  <div class="col-md-4"><label class="form-label">Address</label><input class="form-control" id="supAddress"></div>
                </div>`, `<button class="btn mf-btn-primary" id="supSave">Save</button>`);
            UI.$("#supSave").addEventListener("click", async () => {
                try {
                    await API.registerSupplier({
                        name: UI.$("#supName").value, phone: UI.$("#supPhone").value,
                        contactPerson: UI.$("#supContact").value, paymentTerms: UI.$("#supTerms").value,
                        address: UI.$("#supAddress").value
                    });
                    inst.hide(); UI.ok("Supplier added"); loadSuppliers();
                } catch (err) { UI.fail(err.message); }
            });
        });

        async function loadTemplates() {
            try {
                const templates = await API.poTemplates();
                UI.$("#tplList").innerHTML = UI.table(["Template", "Supplier", "Lines", "Action"],
                    templates.map((t) => `
                      <tr><td><b>${UI.esc(t.name)}</b></td><td>${UI.esc(t.supplierId)}</td>
                      <td class="small">${(t.lines || []).join(", ")}</td>
                      <td><button class="btn mf-btn-primary btn-sm" data-clone="${UI.esc(t.id)}">
                        <i class="bi bi-copy"></i> Clone into draft (Prototype)</button></td></tr>`).join(""),
                    "No standing templates yet — ask your manager to create the weekly restock list");
                UI.$$("[data-clone]").forEach((b) => b.addEventListener("click", async () => {
                    try {
                        const po = await API.poFromTemplate({ templateId: b.dataset.clone });
                        UI.ok("Cloned into " + po.poNo + " — adjust and submit");
                        loadPos();
                    } catch (err) { UI.fail(err.message); }
                }));
            } catch (err) { UI.fail(err.message); }
        }

    }

    // ============================== CUSTOMERS ==============================

    async function customers(host) {
        const canManage = API.role() === "MANAGER" || API.role() === "ADMIN";
        host.innerHTML = `
        <div class="d-flex gap-2 align-items-center mt-2 mb-3">
          <h4 class="mf-page-title mb-0 me-auto"><i class="bi bi-people"></i> Loyalty members</h4>
          <input class="form-control form-control-sm mf-w-auto" id="custSearch" placeholder="Search name / phone / card">
          <button class="btn mf-btn-primary btn-sm" id="btnNewCustomer"><i class="bi bi-person-plus"></i> Register</button>
        </div>
        <div class="mf-panel p-3" id="custTable"></div>`;

        const load = async () => {
            try {
                const list = await API.customers({ q: UI.$("#custSearch").value || undefined });
                UI.$("#custTable").innerHTML = UI.table(
                    ["Member", "Phone", "Card", "Points", "Member since", "Actions"],
                    list.map((c) => `
                      <tr>
                        <td><b>${UI.esc(c.name)}</b></td>
                        <td>${UI.esc(c.phone)}</td>
                        <td>${UI.esc(c.cardNo || "—")}</td>
                        <td><span class="badge text-bg-success">${c.pointsBalance} pts</span></td>
                        <td class="small">${UI.esc(c.memberSince || "")}</td>
                        <td>${canManage ? `<button class="btn btn-sm mf-btn-ghost" data-pts="${UI.esc(c.id)}" data-name="${UI.esc(c.name)}" data-cur="${c.pointsBalance}"><i class="bi bi-sliders"></i> Adjust points</button>` : "—"}</td>
                      </tr>`).join(""));
                UI.$$("[data-pts]").forEach((b) => b.addEventListener("click", () => {
                    const raw = window.prompt("New points balance for " + b.dataset.name + ":", b.dataset.cur);
                    if (raw === null) return;
                    API.adjustPoints(b.dataset.pts, parseInt(raw, 10))
                        .then(() => { UI.ok("Points adjusted"); load(); })
                        .catch((err) => UI.fail(err.message));
                }));
            } catch (err) { UI.fail(err.message); }
        };
        let t = null;
        UI.$("#custSearch").addEventListener("input", () => { clearTimeout(t); t = setTimeout(load, 250); });
        UI.$("#btnNewCustomer").addEventListener("click", () => {
            const inst = UI.modal("Register loyalty member", `
                <div class="row g-3">
                  <div class="col-md-4"><label class="form-label">Name</label><input class="form-control" id="cName"></div>
                  <div class="col-md-4"><label class="form-label">Phone (unique)</label><input class="form-control" id="cPhone"></div>
                  <div class="col-md-4"><label class="form-label">Card no</label><input class="form-control" id="cCard"></div>
                </div>`, `<button class="btn mf-btn-primary" id="cSave">Register</button>`);
            UI.$("#cSave").addEventListener("click", async () => {
                try {
                    await API.registerCustomer({
                        name: UI.$("#cName").value, phone: UI.$("#cPhone").value, cardNo: UI.$("#cCard").value
                    });
                    inst.hide(); UI.ok("Member registered"); load();
                } catch (err) { UI.fail(err.message); }
            });
        });
        load();
    }

    // ============================== RETURNS ==============================

    async function returns(host) {
        const canSeeHistory = API.role() === "MANAGER" || API.role() === "ADMIN";
        host.innerHTML = `
        <h4 class="mf-page-title mt-2"><i class="bi bi-arrow-counterclockwise"></i> Returns & exchange</h4>
        <div class="mf-panel p-3 mb-3">
          <div class="row g-2 align-items-end">
            <div class="col-md-4">
              <label class="form-label">Receipt number</label>
              <input class="form-control" id="retReceipt" placeholder="MF-20260815-001">
            </div>
            <div class="col-md-3">
              <label class="form-label">Refund channel</label>
              <select class="form-select" id="retChannel"><option>CASH</option><option>CARD</option><option>BKASH</option><option>NAGAD</option></select>
            </div>
            <div class="col-md-3"><button class="btn mf-btn-primary" id="btnLookup"><i class="bi bi-search"></i> Look up</button></div>
          </div>
          <div id="retLines" class="mt-3"></div>
        </div>
        ${canSeeHistory ? `<div class="mf-panel p-3"><h6>Return history</h6><div id="retHistory">loading…</div></div>` : ""}`;

        let sale = null;
        UI.$("#btnLookup").addEventListener("click", async () => {
            try {
                sale = await API.sale(UI.$("#retReceipt").value.trim());
                UI.$("#retLines").innerHTML = `
                  <div class="mb-2">${UI.saleBadge(sale.status)} <b>${UI.esc(sale.receiptNo)}</b>
                    · ${UI.money(sale.totals.net)} · cashier ${UI.esc(sale.cashier)}</div>
                  ${UI.table(["Line", "Item", "Qty sold", "Net", "Return qty", "Reason", ""],
                      sale.lines.filter((l) => l.productId).map((l, idx) => `
                        <tr data-retrow="${idx}">
                          <td>${l.lineNo}</td><td>${UI.esc(l.name)}</td><td>${UI.qty(l.quantity)}</td>
                          <td>${UI.money(l.net)}</td>
                          <td><input type="number" min="0" max="${l.quantity}" step="0.001" class="form-control form-control-sm mf-w-90" value="0" data-rqty="${l.lineNo}"></td>
                          <td><input class="form-control form-control-sm" data-rreason="${l.lineNo}" placeholder="e.g. dented"></td>
                        </tr>`).join(""))}
                  <button class="btn mf-btn-primary mt-2" id="btnReturn"><i class="bi bi-arrow-return-left"></i> Process refund</button>`;
                UI.$("#btnReturn").addEventListener("click", processReturn);
            } catch (err) { UI.fail(err.message); }
        });

        async function processReturn() {
            const lines = sale.lines.filter((l) => l.productId)
                .map((l) => ({
                    lineNo: l.lineNo,
                    quantity: parseFloat(UI.$(`[data-rqty="${l.lineNo}"]`).value || "0"),
                    reason: UI.$(`[data-rreason="${l.lineNo}"]`).value || ""
                }))
                .filter((l) => l.quantity > 0);
            if (!lines.length) { UI.fail("Enter a return quantity on at least one line"); return; }
            try {
                const result = await API.createReturn(sale.receiptNo,
                    { lines, refundChannel: UI.$("#retChannel").value });
                UI.ok("Refunded " + UI.money(result.refundAmount) + " via " + result.refundChannel);
                UI.$("#btnLookup").click();
                loadHistory();
            } catch (err) { UI.fail(err.message); }
        }

        async function loadHistory() {
            if (!canSeeHistory) return;
            try {
                const list = await API.returns();
                UI.$("#retHistory").innerHTML = UI.table(["Date", "Receipt", "Items", "Refund", "Channel"],
                    list.slice().reverse().map((r) => `
                      <tr><td class="small">${String(r.at).replace("T", " ").slice(0, 16)}</td>
                      <td>${UI.esc(r.receiptNo)}</td>
                      <td class="small">${r.lines.map((l) => UI.esc(l.name) + " ×" + UI.qty(l.quantity)).join(", ")}</td>
                      <td>${UI.money(r.refundAmount)}</td><td>${UI.esc(r.refundChannel)}</td></tr>`).join(""),
                    "No returns yet");
            } catch (err) { UI.fail(err.message); }
        }
        loadHistory();
    }

    return { inventory, purchases, customers, returns };
})());

/** Shared date pretty-printer. */
Views._escDate = (iso) => (iso ? iso.replace("T", " ").slice(0, 16) : "");
