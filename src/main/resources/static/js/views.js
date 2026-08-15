/**
 * MartFlow views. Each view is a function (host) => cleanup?, rendering into the given host.
 * POS first — it is the money screen — then the management views.
 */
const Views = (() => {

    // ============================== POS ==============================

    async function pos(host) {
        let products = [];
        let categories = [];
        let bill = null;
        let searchTimeout = null;

        host.innerHTML = `
        <div class="row g-3 mt-1">
          <div class="col-lg-7">
            <div class="mf-panel p-3">
              <div class="d-flex gap-2 mb-3">
                <div class="input-group">
                  <span class="input-group-text"><i class="bi bi-upc-scan"></i></span>
                  <input id="posScan" class="form-control form-control-lg" placeholder="Scan barcode or search item… (F2)" autocomplete="off">
                </div>
              </div>
              <div id="posResults" class="mf-scan-results"></div>
              <div class="d-flex gap-2 flex-wrap mb-2" id="posCategories"></div>
              <div id="posQuick" class="row g-2 mf-quick-grid"></div>
            </div>
          </div>
          <div class="col-lg-5">
            <div class="mf-panel p-3" id="posBillPanel"></div>
          </div>
        </div>`;

        const scan = UI.$("#posScan", host);
        scan.focus();
        document.addEventListener("keydown", posKeys);
        function posKeys(e) {
            if (e.key === "F2") { e.preventDefault(); scan.focus(); }
        }

        try {
            [products, categories] = await Promise.all([API.products({ view: "in_stock" }), API.categories()]);
            renderQuick("all");
        } catch (err) { UI.fail(err.message); }

        renderCategories();
        refreshBill();

        scan.addEventListener("keydown", (e) => {
            if (e.key !== "Enter") return;
            e.preventDefault();
            const value = scan.value.trim();
            if (!value) return;
            // barcode scanners type digits + Enter; keyboards type names
            if (/^\d{6,}$/.test(value)) {
                scanByBarcode(value);
            } else {
                const hit = products.find((p) =>
                    p.name.toLowerCase().includes(value.toLowerCase()));
                if (hit) addLine(hit.id);
                else UI.fail("No item matches '" + value + "'");
            }
            scan.value = "";
        });
        scan.addEventListener("input", () => {
            clearTimeout(searchTimeout);
            searchTimeout = setTimeout(() => renderResults(scan.value.trim()), 180);
        });

        function renderCategories() {
            const el = UI.$("#posCategories", host);
            el.innerHTML = `<button class="btn btn-sm mf-chip mf-chip-active" data-cat="all">All</button>` +
                categories.map((c) => `<button class="btn btn-sm mf-chip" data-cat="${UI.esc(c.id)}">${UI.esc(c.name.split(" —")[0])}</button>`).join("");
            UI.$$(".mf-chip", el).forEach((chip) => chip.addEventListener("click", () => {
                UI.$$(".mf-chip", el).forEach((c) => c.classList.remove("mf-chip-active"));
                chip.classList.add("mf-chip-active");
                renderQuick(chip.dataset.cat);
            }));
        }

        function renderResults(query) {
            const box = UI.$("#posResults", host);
            if (!query) { box.innerHTML = ""; return; }
            const hits = products.filter((p) =>
                p.name.toLowerCase().includes(query.toLowerCase())
                || (p.sku || "").toLowerCase().includes(query.toLowerCase())).slice(0, 8);
            box.innerHTML = hits.map((p) => `
                <button class="mf-scan-hit" data-id="${UI.esc(p.id)}">
                  <span>${UI.esc(p.name)}</span>
                  <b>${UI.money(p.price)}</b>
                </button>`).join("");
            UI.$$(".mf-scan-hit", box).forEach((hit) => hit.addEventListener("click", () => {
                addLine(hit.dataset.id);
                scan.value = "";
                box.innerHTML = "";
                scan.focus();
            }));
        }

        function renderQuick(cat) {
            const grid = UI.$("#posQuick", host);
            const list = cat === "all" ? products
                : products.filter((p) => p.categoryId === cat);
            grid.innerHTML = list.map((p) => `
                <div class="col-4 col-md-3">
                  <button class="mf-quick-tile" data-id="${UI.esc(p.id)}">
                    <div class="mf-quick-name">${UI.esc(p.name)}</div>
                    <div class="mf-quick-price">${UI.money(p.price)}</div>
                    <div class="mf-quick-unit">${p.type === "WEIGHED" ? "per kg" : p.unit || ""}</div>
                  </button>
                </div>`).join("");
            UI.$$(".mf-quick-tile", grid).forEach((tile) =>
                tile.addEventListener("click", () => addLine(tile.dataset.id)));
        }

        async function scanByBarcode(code) {
            try {
                const product = await API.byBarcode(code);
                addLine(product.id);
            } catch (err) { UI.fail(err.message); }
        }

        async function addLine(id, weightKg) {
            try {
                const product = products.find((p) => p.id === id) || await API.product(id);
                const body = product.type === "WEIGHED"
                    ? { productId: id, weightKg: weightKg || weightPrompt(product) }
                    : { productId: id, quantity: 1 };
                if (body.weightKg === null) return;
                await API.addLine(body);
                refreshBill();
            } catch (err) { UI.fail(err.message); }
        }

        function weightPrompt(product) {
            const raw = window.prompt("Weight for " + product.name + " (kg):", "1.00");
            if (raw === null) return null;
            const value = parseFloat(raw);
            return isNaN(value) || value <= 0 ? 0.01 : value;
        }

        async function refreshBill() {
            try { bill = await API.bill(); renderBill(); }
            catch (err) { UI.fail(err.message); }
        }

        function renderBill() {
            const panel = UI.$("#posBillPanel", host);
            const t = bill.totals;
            // Weighed lines always edit by weight; piece goods step in whole units.
            const lineControls = (l, i) => {
                if (l.kind === "WEIGHED") {
                    return `<button class="btn btn-outline-secondary" data-act="edit" data-i="${i}" title="Change weight (kg)"><i class="bi bi-pencil"></i></button>`;
                }
                if (l.kind === "UNIT" || l.kind === "COMBO") {
                    return `<button class="btn btn-outline-secondary" data-act="minus" data-i="${i}" ${l.quantity <= 1 ? "disabled" : ""}>–</button>`;
                }
                return "";
            };
            panel.innerHTML = `
              <div class="d-flex justify-content-between align-items-center mb-2">
                <h5 class="mb-0"><i class="bi bi-receipt"></i> Current bill</h5>
                <div class="d-flex gap-2">
                  <button class="btn btn-sm mf-btn-ghost" id="btnUndo" ${bill.undoDepth === 0 ? "disabled" : ""}>
                    <i class="bi bi-arrow-counterclockwise"></i> Undo (${bill.undoDepth})
                  </button>
                  <button class="btn btn-sm mf-btn-ghost" id="btnClear"><i class="bi bi-x-lg"></i></button>
                </div>
              </div>
              <div class="mf-bill-lines">
                ${bill.lines.map((l, i) => `
                  <div class="mf-bill-line">
                    <div class="flex-grow-1">
                      <div>${UI.esc(l.describe)}</div>
                      <div class="text-secondary small">${l.vatRate > 0 ? "VAT " + l.vatRate + "% incl." : "VAT-free"} ${l.discount > 0 ? '· <span class="text-success">saved ' + UI.money(l.discount) + "</span>" : ""}</div>
                    </div>
                    <div class="text-end">
                      <div class="fw-bold">${UI.money(l.net)}</div>
                      <div class="btn-group btn-group-sm">
                        ${lineControls(l, i)}
                        <button class="btn btn-outline-secondary" data-act="plus" data-i="${i}">+</button>
                        <button class="btn btn-outline-danger" data-act="del" data-i="${i}"><i class="bi bi-trash"></i></button>
                      </div>
                    </div>
                  </div>`).join("") || `<div class="text-center text-secondary py-4">Scan an item to start the bill</div>`}
              </div>
              <div class="mf-customer-row d-flex gap-2 align-items-center my-2">
                <i class="bi bi-person-badge"></i>
                <span class="small">${bill.customer ? UI.esc(bill.customer.name) + " · " + bill.customer.points + " pts" : "Walk-in customer"}</span>
                <button class="btn btn-sm mf-btn-ghost ms-auto" id="btnCustomer">Attach</button>
                ${bill.couponCode
                  ? `<span class="badge text-bg-success d-inline-flex align-items-center gap-1"><i class="bi bi-ticket-perforated"></i> ${UI.esc(bill.couponCode)} <i class="bi bi-x-circle text-white mf-cursor-pointer" id="btnRemoveCoupon" title="Remove coupon"></i></span>`
                  : `<button class="btn btn-sm mf-btn-ghost" id="btnCoupon">Coupon</button>`}
                <button class="btn btn-sm mf-btn-ghost" id="btnCharges">Bags/Delivery</button>
              </div>
              <div class="mf-bill-totals">
                <div><span>Gross</span><span>${UI.money(t.gross)}</span></div>
                ${t.discount > 0 ? `<div class="text-success"><span>Promotions</span><span>-${UI.money(t.discount)}</span></div>` : ""}
                ${t.coupon > 0 ? `<div class="text-success"><span>Coupon (${UI.esc(bill.couponCode || "")})</span><span>-${UI.money(t.coupon)}</span></div>` : ""}
                ${t.fees > 0 ? `<div><span>Carry bag / delivery</span><span>${UI.money(t.fees)}</span></div>` : ""}
                <div class="mf-bill-net"><span>PAYABLE</span><span>${UI.money(t.net)}</span></div>
                <div class="text-secondary small"><span>incl. VAT</span><span>${UI.money(t.vat)}</span></div>
              </div>
              <button class="btn mf-btn-primary btn-lg w-100 mt-2" id="btnTender" ${bill.lines.length === 0 ? "disabled" : ""}>
                <i class="bi bi-cash-coin"></i> Take payment
              </button>`;

            UI.$$("#btnUndo", panel).forEach((b) => b.addEventListener("click", async () => {
                await API.undo(); refreshBill();
            }));
            UI.$$("#btnClear", panel).forEach((b) => b.addEventListener("click", async () => {
                await API.clearBill(); refreshBill();
            }));
            UI.$$(".mf-bill-line button", panel).forEach((b) => b.addEventListener("click", async () => {
                const i = Number(b.dataset.i);
                const line = bill.lines[i];
                try {
                    if (b.dataset.act === "del") await API.removeLine(i);
                    if (b.dataset.act === "plus") {
                        const step = line.kind === "WEIGHED" ? 0.25 : 1;
                        await API.updateLine(i, { quantity: Number((line.quantity + step).toFixed(3)) });
                    }
                    if (b.dataset.act === "minus") await API.updateLine(i, { quantity: Math.max(1, line.quantity - 1) });
                    if (b.dataset.act === "edit") {
                        const raw = window.prompt("New weight for " + line.name + " (kg):", line.quantity);
                        if (raw !== null) {
                            const value = parseFloat(raw);
                            if (!isNaN(value) && value > 0) await API.updateLine(i, { quantity: value });
                            else UI.fail("Weight must be a positive number");
                        }
                    }
                    refreshBill();
                } catch (err) { UI.fail(err.message); refreshBill(); }
            }));
            UI.$$("#btnCustomer", panel).forEach((b) => b.addEventListener("click", pickCustomer));
            UI.$$("#btnCoupon", panel).forEach((b) => b.addEventListener("click", askCoupon));
            UI.$$("#btnRemoveCoupon", panel).forEach((b) => b.addEventListener("click", async (e) => {
                e.stopPropagation();
                try {
                    await API.setBillCoupon({ code: "" });
                    refreshBill();
                    UI.ok("Coupon removed");
                } catch (err) { UI.fail(err.message); }
            }));
            const btnCharges = UI.$("#btnCharges", panel);
            if (btnCharges) btnCharges.addEventListener("click", askCharges);
            const btnTender = UI.$("#btnTender", panel);
            if (btnTender) btnTender.addEventListener("click", () => openTender(t.net));
        }

        async function pickCustomer() {
            try {
                const customers = await API.customers();
                const inst = UI.modal("Attach loyalty customer", `
                    <div class="list-group">
                      ${customers.map((c) => `
                        <button class="list-group-item list-group-item-action d-flex justify-content-between" data-id="${UI.esc(c.id)}">
                          <span>${UI.esc(c.name)} <span class="text-secondary">· ${UI.esc(c.phone)}</span></span>
                          <b>${c.pointsBalance} pts</b>
                        </button>`).join("")}
                    </div>`);
                UI.$$(".list-group-item", UI.$("#modalHost")).forEach((el) =>
                    el.addEventListener("click", async () => {
                        await API.setBillCustomer({ customerIdOrPhone: el.dataset.id });
                        inst.hide();
                        refreshBill();
                    }));
            } catch (err) { UI.fail(err.message); }
        }

        async function askCoupon() {
            const raw = window.prompt("Coupon code (empty to remove):", (bill && bill.couponCode) || "");
            if (raw === null) return;
            const code = raw.trim();
            try {
                if (code) {
                    const gross = (bill && bill.totals) ? bill.totals.gross : 0;
                    await API.checkCoupon({ code, netTotal: gross });
                }
                await API.setBillCoupon({ code });
                refreshBill();
                UI.ok(code ? `Coupon '${code.toUpperCase()}' applied` : "Coupon removed");
            } catch (err) { UI.fail(err.message); }
        }

        function askCharges() {
            const inst = UI.modal("Carry bags & delivery", `
                <div class="row g-3">
                  <div class="col-6"><label class="form-label">Carry bags (BDT ${bill.carryBagUnitFee || "5.00"} each)</label>
                    <input type="number" min="0" class="form-control" id="bagsInput" value="${bill.carryBags}"></div>
                  <div class="col-6"><label class="form-label">Delivery fee (BDT)</label>
                    <input type="number" min="0" step="0.01" class="form-control" id="deliveryInput" value="${bill.deliveryFee || 0}"></div>
                </div>`,
                `<button class="btn mf-btn-primary" id="saveCharges">Apply</button>`);
            UI.$("#saveCharges").addEventListener("click", async () => {
                await API.setBillCharges({
                    carryBags: parseInt(UI.$("#bagsInput").value || "0", 10),
                    deliveryFee: parseFloat(UI.$("#deliveryInput").value || "0")
                });
                inst.hide();
                refreshBill();
            });
        }

        function openTender(net) {
            const rows = [{ type: "CASH", amount: net }];
            const inst = UI.modal("Take payment — payable " + UI.money(net), `
                <div id="tenderRows"></div>
                <button class="btn btn-sm mf-btn-ghost mt-2" id="addTenderRow"><i class="bi bi-plus-lg"></i> Split payment</button>
                <div class="mf-tender-summary mt-3" id="tenderSummary"></div>`,
                `<button class="btn mf-btn-primary" id="confirmTender"><i class="bi bi-check2-circle"></i> Complete sale</button>`);

            const render = () => {
                UI.$("#tenderRows").innerHTML = rows.map((r, i) => `
                    <div class="row g-2 mb-2">
                      <div class="col-5">
                        <select class="form-select" data-i="${i}">
                          ${["CASH", "CARD", "BKASH", "NAGAD", "POINTS"].map((t) =>
                            `<option ${t === r.type ? "selected" : ""}>${t}</option>`).join("")}
                        </select>
                      </div>
                      <div class="col-5">
                        <input type="number" min="0" step="0.01" class="form-control" value="${r.amount}" data-amt="${i}">
                      </div>
                      <div class="col-2">
                        ${rows.length > 1 ? `<button class="btn btn-outline-danger" data-del="${i}"><i class="bi bi-x"></i></button>` : ""}
                      </div>
                    </div>`).join("");
                const total = rows.reduce((s, r) => s + Number(r.amount || 0), 0);
                UI.$("#tenderSummary").innerHTML = `
                    <div><span>Tendered</span><b>${UI.money(total)}</b></div>
                    <div><span>Change</span><b class="${total - net >= 0 ? "text-success" : "text-danger"}">${UI.money(Math.max(0, total - net))}</b></div>`;
                UI.$$("#tenderRows select").forEach((sel) => sel.addEventListener("change", () => {
                    rows[Number(sel.dataset.i)].type = sel.value;
                }));
                UI.$$("#tenderRows input").forEach((inp) => inp.addEventListener("input", () => {
                    rows[Number(inp.dataset.amt)].amount = parseFloat(inp.value || "0");
                    const total = rows.reduce((s, r) => s + Number(r.amount || 0), 0);
                    UI.$("#tenderSummary").innerHTML = `
                        <div><span>Tendered</span><b>${UI.money(total)}</b></div>
                        <div><span>Change</span><b class="${total - net >= 0 ? "text-success" : "text-danger"}">${UI.money(Math.max(0, total - net))}</b></div>`;
                }));
                UI.$$("#tenderRows button[data-del]").forEach((btn) => btn.addEventListener("click", () => {
                    rows.splice(Number(btn.dataset.del), 1);
                    render();
                }));
            };
            render();
            UI.$("#addTenderRow").addEventListener("click", () => { rows.push({ type: "BKASH", amount: 0 }); render(); });
            UI.$("#confirmTender").addEventListener("click", async () => {
                try {
                    const sale = await API.tender({ tenders: rows.filter((r) => r.amount > 0) });
                    inst.hide();
                    Receipt.show(sale, { onNext: () => scan.focus() });
                    refreshBill();
                } catch (err) { UI.fail(err.message); }
            });
        }

        return () => document.removeEventListener("keydown", posKeys);
    }

    // ============================== DASHBOARD ==============================

    async function dashboard(host) {
        let kpis;
        try { kpis = await API.dashboard(); }
        catch (err) { host.innerHTML = `<div class="mf-panel p-4 mt-3 text-danger">${UI.esc(err.message)}</div>`; return; }

        host.innerHTML = `
        <h4 class="mf-page-title mt-2"><i class="bi bi-speedometer2"></i> Dashboard — ${kpis.date}</h4>
        <div class="row g-3 mt-1">
          ${UI.kpi("Net sales (today)", UI.money(kpis.netSales), kpis.bills + " bills", "bi-cash-stack")}
          ${UI.kpi("Average basket", UI.money(kpis.avgBasket), UI.qty(kpis.unitsSold) + " units", "bi-basket")}
          ${UI.kpi("Output VAT (today)", UI.money(kpis.vat), "NBR filing basis", "bi-receipt-cutoff")}
          ${UI.kpi("Cash in drawer", UI.money(kpis.cashTendered), "cash tenders", "bi-cash-coin")}
          ${UI.kpi("Low stock items", kpis.lowStockCount, "at/below reorder level", "bi-exclamation-triangle")}
          ${UI.kpi("Expiring ≤14 days", kpis.expiringCount, "markdown or return", "bi-clock-history")}
        </div>
        <div class="row g-3 mt-1">
          <div class="col-lg-6"><div class="mf-panel p-3 h-100">
            <h6><i class="bi bi-file-earmark-bar-graph"></i> Quick reports</h6>
            <div class="d-flex gap-2 flex-wrap">
              <a class="btn mf-btn-ghost" href="#/reports">Open report suite</a>
              <a class="btn mf-btn-ghost" href="#/inventory?view=low_stock">Reorder worksheet</a>
              <a class="btn mf-btn-ghost" href="#/purchases">Purchasing board</a>
            </div>
          </div></div>
          <div class="col-lg-6"><div class="mf-panel p-3 h-100" id="dashAlerts">
            <h6><i class="bi bi-bell"></i> Latest alerts</h6><div class="text-secondary">loading…</div>
          </div></div>
        </div>`;

        try {
            const alerts = (await API.alerts()).slice(-6).reverse();
            UI.$("#dashAlerts").innerHTML = "<h6><i class='bi bi-bell'></i> Latest alerts</h6>" +
                (alerts.map((a) => `<div class="mf-alert-line mf-alert-${a.type}">${UI.esc(a.message)}</div>`).join("")
                    || "<div class='text-secondary'>No alerts — quiet day.</div>");
        } catch (ignored) { /* alerts are optional garnish */ }
    }

    return { pos, dashboard };
})();
