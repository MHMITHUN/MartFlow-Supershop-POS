/** MartFlow views, part 3: reports, alerts, staff, Pattern Studio. */
Object.assign(Views, (() => {

    // ============================== REPORTS ==============================

    const REPORTS = [
        { key: "daily-sales", label: "Daily Sales", min: "CASHIER", icon: "bi-calendar-range" },
        { key: "best-sellers", label: "Best Sellers", min: "CASHIER", icon: "bi-star" },
        { key: "low-stock", label: "Low Stock / Reorder", min: "CASHIER", icon: "bi-exclamation-triangle" },
        { key: "expiry", label: "Expiring Batches", min: "CASHIER", icon: "bi-clock-history" },
        { key: "returns", label: "Returns & Refunds", min: "MANAGER", icon: "bi-arrow-counterclockwise" },
        { key: "staff", label: "Staff Performance", min: "MANAGER", icon: "bi-people" },
        { key: "profit", label: "Profit", min: "MANAGER", icon: "bi-graph-up-arrow" },
        { key: "vat", label: "VAT Summary (NBR)", min: "MANAGER", icon: "bi-receipt-cutoff" }
    ];

    async function reports(host) {
        const rank = { CASHIER: 0, DEVELOPER: 0, MANAGER: 1, ADMIN: 2 };
        const mine = REPORTS.filter((r) => rank[r.min] <= rank[API.role()]);

        host.innerHTML = `
        <h4 class="mf-page-title mt-2"><i class="bi bi-file-earmark-bar-graph"></i> Reports</h4>
        <div class="d-flex flex-wrap gap-2 align-items-end mt-2 mb-3">
          <div class="btn-group flex-wrap" id="reportPicker">
            ${mine.map((r, i) => `<button class="btn btn-sm ${i === 0 ? "mf-btn-primary" : "mf-btn-ghost"}" data-report="${r.key}"><i class="bi ${r.icon}"></i> ${r.label}</button>`).join("")}
          </div>
          <div class="ms-auto d-flex gap-2">
            <div><label class="form-label small mb-0">From</label><input type="date" class="form-control form-control-sm" id="repFrom"></div>
            <div><label class="form-label small mb-0">To</label><input type="date" class="form-control form-control-sm" id="repTo"></div>
            <button class="btn mf-btn-primary btn-sm" id="repRun">Run</button>
            <button class="btn mf-btn-ghost btn-sm" id="repCsv"><i class="bi bi-download"></i> CSV</button>
          </div>
        </div>
        <div class="mf-panel p-3" id="repResult">Pick a report and press Run.</div>`;

        let current = mine.length ? mine[0].key : null;
        UI.$$("[data-report]").forEach((b) => b.addEventListener("click", () => {
            current = b.dataset.report;
            UI.$$("[data-report]").forEach((x) => x.className = "btn btn-sm mf-btn-ghost");
            b.className = "btn btn-sm mf-btn-primary";
            load();
        }));
        UI.$("#repRun").addEventListener("click", load);
        UI.$("#repCsv").addEventListener("click", () => {
            if (!current) return;
            API.downloadCsv("/reports/" + current, query())
                .catch((err) => UI.fail(err.message));
        });
        const query = () => {
            const q = {};
            if (UI.$("#repFrom").value) q.from = UI.$("#repFrom").value;
            if (UI.$("#repTo").value) q.to = UI.$("#repTo").value;
            return q;
        };

        async function load() {
            if (!current) return;
            UI.$("#repResult").innerHTML = "running…";
            try {
                const result = await API.report(current, query());
                const meta = Object.entries(result.meta || {})
                    .map(([k, v]) => `<span class="mf-meta-chip">${UI.esc(k)}: <b>${UI.esc(v)}</b></span>`).join("");
                UI.$("#repResult").innerHTML = `
                  <div class="d-flex justify-content-between align-items-center mb-2">
                    <h6 class="mb-0">${UI.esc(result.title)}</h6>
                    <div>${meta}</div>
                  </div>
                  ${UI.table(result.headers, result.rows.map((r) =>
                      r.map((cell) => `<td>${UI.esc(cell)}</td>`).join("")).map((cells) => `<tr>${cells}</tr>`).join(""))}`;
            } catch (err) { UI.fail(err.message); UI.$("#repResult").innerHTML = ""; }
        }
        if (current) load();
    }

    // ============================== ALERTS ==============================

    async function alerts(host) {
        host.innerHTML = `
        <div class="d-flex gap-2 align-items-center mt-2 mb-3">
          <h4 class="mf-page-title mb-0 me-auto"><i class="bi bi-bell"></i> Alert center</h4>
          <label class="form-check"><input type="checkbox" class="form-check-input" id="onlyUnread"> unread only</label>
          <button class="btn mf-btn-ghost btn-sm" id="btnRefresh"><i class="bi bi-arrow-clockwise"></i></button>
        </div>
        <div class="mf-panel p-3" id="alertFeed">loading…</div>`;

        const load = async () => {
            try {
                const list = (await API.alerts(UI.$("#onlyUnread").checked ? { unreadOnly: true } : {}))
                    .slice().reverse();
                UI.$("#alertFeed").innerHTML = list.map((a) => `
                  <div class="mf-alert-line mf-alert-${UI.esc(a.type)} ${a.read ? "mf-alert-read" : ""}">
                    <i class="bi ${alertIcon(a.type)}"></i>
                    <span>${UI.esc(a.message)}</span>
                    ${a.read ? "" : `<button class="btn btn-sm mf-btn-ghost ms-auto" data-read="${UI.esc(a.id)}">Mark read</button>`}
                  </div>`).join("") || "<div class='text-center text-secondary py-4'>No alerts — quiet day.</div>";
                UI.$$("[data-read]").forEach((b) => b.addEventListener("click", async () => {
                    await API.markAlertRead(b.dataset.read);
                    load();
                }));
            } catch (err) { UI.fail(err.message); }
        };
        const alertIcon = (type) => ({
            LOW_STOCK: "bi-exclamation-triangle", RESTOCK: "bi-plus-square",
            PRICE_CHANGE: "bi-tag", EXPIRY_SOON: "bi-clock-history", SHRINKAGE: "bi-dash-square"
        }[type] || "bi-bell");
        UI.$("#onlyUnread").addEventListener("change", load);
        UI.$("#btnRefresh").addEventListener("click", load);
        load();
    }

    // ============================== SALES EXPLORER ==============================

    // Business "today" is the Dhaka day — toISOString() would drift a day behind
    // between midnight and 6am (UTC), hiding tonight's entries from the default filter.
    const dhakaToday = () => new Date().toLocaleDateString("en-CA", { timeZone: "Asia/Dhaka" });

    async function sales(host) {
        const today = dhakaToday();
        host.innerHTML = `
        <div class="d-flex flex-wrap gap-2 align-items-center mt-2 mb-3">
          <h4 class="mf-page-title mb-0 me-auto"><i class="bi bi-journal-text"></i> Sales explorer</h4>
          <input type="date" class="form-control form-control-sm mf-w-auto" id="salesFrom" value="${today}">
          <input type="date" class="form-control form-control-sm mf-w-auto" id="salesTo" value="${today}">
          <select class="form-select form-select-sm mf-w-auto" id="salesStatus">
            <option value="">All statuses</option>
            <option>COMPLETED</option><option>PARTIALLY_RETURNED</option>
            <option>RETURNED</option><option>VOIDED</option>
          </select>
          <select class="form-select form-select-sm mf-w-auto" id="salesCashier"><option value="">All cashiers</option></select>
          <button class="btn mf-btn-primary btn-sm" id="salesRefresh"><i class="bi bi-arrow-clockwise"></i> Refresh</button>
        </div>
        <div class="mf-panel p-3" id="salesTable">loading…</div>`;

        const load = async () => {
            try {
                const q = {};
                if (UI.$("#salesFrom").value) q.from = UI.$("#salesFrom").value;
                if (UI.$("#salesTo").value) q.to = UI.$("#salesTo").value;
                if (UI.$("#salesStatus").value) q.status = UI.$("#salesStatus").value;
                if (UI.$("#salesCashier").value) q.cashier = UI.$("#salesCashier").value;
                const list = await API.sales(q);
                const cashiers = [...new Set(list.map((s) => s.cashier))].sort();
                const sel = UI.$("#salesCashier");
                const current = sel.value;
                sel.innerHTML = `<option value="">All cashiers</option>` +
                    cashiers.map((c) => `<option ${c === current ? "selected" : ""}>${UI.esc(c)}</option>`).join("");
                UI.$("#salesTable").innerHTML = UI.table(
                    ["Receipt", "Date", "Cashier", "Status", "Net", "VAT", "Actions"],
                    list.slice().reverse().map((s) => `
                      <tr>
                        <td><b>${UI.esc(s.receiptNo)}</b></td>
                        <td class="small">${Views._escDate(s.at)}</td>
                        <td>${UI.esc(s.cashier)}</td>
                        <td>${UI.saleBadge(s.status)}</td>
                        <td><b>${UI.money(s.net)}</b></td>
                        <td>${UI.money(s.vat)}</td>
                        <td class="text-nowrap">
                          <button class="btn btn-sm mf-btn-ghost" data-view="${UI.esc(s.receiptNo)}" title="View / reprint"><i class="bi bi-receipt"></i></button>
                          ${s.status !== "VOIDED" ? `<button class="btn btn-sm mf-btn-ghost text-danger" data-void="${UI.esc(s.receiptNo)}" title="Void"><i class="bi bi-x-octagon"></i></button>` : ""}
                        </td>
                      </tr>`).join(""),
                    "No sales in this window");
                UI.$$("[data-view]").forEach((b) => b.addEventListener("click", () => openReceipt(b.dataset.view)));
                UI.$$("[data-void]").forEach((b) => b.addEventListener("click", () => voidModal(b.dataset.void)));
            } catch (err) { UI.fail(err.message); }
        };

        async function openReceipt(receiptNo) {
            try {
                const sale = await API.sale(receiptNo);
                Receipt.show(sale, {
                    actions: sale.status !== "VOIDED"
                        ? `<button class="btn btn-outline-danger" id="btnVoidFromReceipt"><i class="bi bi-x-octagon"></i> Void</button>`
                        : ""
                });
                const voidBtn = UI.$("#btnVoidFromReceipt");
                if (voidBtn) voidBtn.addEventListener("click", () => voidModal(sale.receiptNo));
            } catch (err) { UI.fail(err.message); }
        }

        function voidModal(receiptNo) {
            const inst = UI.modal("Void " + UI.esc(receiptNo), `
                <div class="alert alert-warning py-2 small mb-3">
                  <i class="bi bi-exclamation-triangle"></i> Voiding reverses everything:
                  stock is restored, tenders are refunded, loyalty points are reversed.
                  This cannot be undone.
                </div>
                <label class="form-label">Reason (required)</label>
                <textarea class="form-control" id="voidReason" rows="2" placeholder="e.g. wrong price sticker, card chargeback"></textarea>`,
                `<button class="btn btn-outline-danger" data-bs-dismiss="modal">Cancel</button>
                 <button class="btn mf-btn-primary" id="voidConfirm"><i class="bi bi-x-octagon"></i> Void sale</button>`);
            UI.$("#voidConfirm").addEventListener("click", async () => {
                const reason = UI.$("#voidReason").value.trim();
                if (!reason) { UI.fail("A void reason is required"); return; }
                try {
                    await API.voidSale(receiptNo, reason);
                    inst.hide();
                    UI.ok(receiptNo + " voided — stock and points reversed");
                    load();
                } catch (err) { UI.fail(err.message); }
            });
        }

        UI.$("#salesRefresh").addEventListener("click", load);
        ["#salesFrom", "#salesTo", "#salesStatus", "#salesCashier"].forEach((sel) => {
            UI.$(sel).addEventListener("change", load);
        });
        load();
    }

    // ============================== PROMOTIONS ==============================

    async function promotions(host) {
        let categories = [];
        try { categories = await API.categories(); }
        catch (err) { UI.fail(err.message); return; }

        host.innerHTML = `
        <div class="d-flex flex-wrap gap-2 align-items-center mt-2 mb-3">
          <h4 class="mf-page-title mb-0 me-auto"><i class="bi bi-tags"></i> Promotions & coupons</h4>
          <button class="btn mf-btn-primary btn-sm" id="btnNewPromo"><i class="bi bi-plus-lg"></i> New promotion</button>
        </div>
        <div class="mf-panel p-3 mb-3" id="promoTable">loading…</div>
        <div class="mf-panel p-3">
          <h6><i class="bi bi-ticket-perforated"></i> Coupon tester <span class="text-secondary small">(Strategy / PromotionEngine live)</span></h6>
          <div class="row g-2 align-items-end">
            <div class="col-md-3"><label class="form-label small mb-0">Code</label>
              <input class="form-control form-control-sm" id="ctCode" placeholder="SAVE50" style="text-transform:uppercase"></div>
            <div class="col-md-3"><label class="form-label small mb-0">Bill net total</label>
              <input type="number" step="0.01" class="form-control form-control-sm" id="ctNet" value="1000"></div>
            <div class="col-md-3"><button class="btn mf-btn-primary btn-sm" id="ctRun"><i class="bi bi-play"></i> Check</button></div>
            <div class="col-md-3 fw-bold" id="ctResult"></div>
          </div>
        </div>`;

        const typeBadge = (t) => ({
            CATEGORY_SALE: "warning", MEMBER_PRICE: "info",
            COUPON_FLAT: "success", COUPON_PERCENT: "success"
        }[t] || "secondary");

        const detail = (p) => {
            const catName = ((categories.find((c) => c.id === p.categoryId) || {}).name || p.categoryId || "").split(" —")[0];
            if (p.type === "CATEGORY_SALE") return UI.esc(catName) + " · " + p.percentOff + "% off";
            if (p.type === "MEMBER_PRICE") return "loyalty members · " + p.percentOff + "% off";
            if (p.type === "COUPON_FLAT") return "<code>" + UI.esc(p.code || "") + "</code> · BDT " + p.flatAmount + " off";
            if (p.type === "COUPON_PERCENT") return "<code>" + UI.esc(p.code || "") + "</code> · " + p.percentOff + "% off";
            return "";
        };

        const load = async () => {
            try {
                const list = await API.promotions();
                UI.$("#promoTable").innerHTML = UI.table(
                    ["Promotion", "Type", "Detail", "Window", "State", "Actions"],
                    list.map((p) => `
                      <tr>
                        <td><b>${UI.esc(p.name)}</b></td>
                        <td><span class="badge text-bg-${typeBadge(p.type)}">${UI.esc(p.type)}</span></td>
                        <td class="small">${detail(p)}</td>
                        <td class="small text-secondary">${p.startsOn || "…"} → ${p.endsOn || "…"}</td>
                        <td><span class="badge text-bg-${p.active ? "success" : "secondary"}">${p.active ? "ACTIVE" : "OFF"}</span></td>
                        <td class="text-nowrap">
                          <button class="btn btn-sm mf-btn-ghost" data-toggle="${UI.esc(p.id)}" data-active="${p.active}" title="${p.active ? "Deactivate" : "Activate"}"><i class="bi bi-power"></i></button>
                          <button class="btn btn-sm mf-btn-ghost" data-edit="${UI.esc(p.id)}" title="Edit"><i class="bi bi-pencil"></i></button>
                          <button class="btn btn-sm mf-btn-ghost text-danger" data-del="${UI.esc(p.id)}" title="Delete"><i class="bi bi-trash"></i></button>
                        </td>
                      </tr>`).join(""), "No promotions yet");
                UI.$$("[data-toggle]").forEach((b) => b.addEventListener("click", async () => {
                    try {
                        await API.updatePromotion(b.dataset.toggle, { active: b.dataset.active !== "true" });
                        UI.ok("Promotion toggled"); load();
                    } catch (err) { UI.fail(err.message); }
                }));
                UI.$$("[data-del]").forEach((b) => b.addEventListener("click", async () => {
                    if (!window.confirm("Delete this promotion?")) return;
                    try { await API.deletePromotion(b.dataset.del); UI.ok("Deleted"); load(); }
                    catch (err) { UI.fail(err.message); }
                }));
                UI.$$("[data-edit]").forEach((b) => b.addEventListener("click", () => {
                    editModal(list.find((x) => x.id === b.dataset.edit));
                }));
            } catch (err) { UI.fail(err.message); }
        };

        function editModal(p) {
            const isNew = !p;
            const inst = UI.modal(isNew ? "New promotion" : "Edit " + UI.esc(p.name), `
                <div class="row g-3">
                  <div class="col-md-6"><label class="form-label">Name</label>
                    <input class="form-control" id="pmName" value="${p ? UI.esc(p.name) : ""}"></div>
                  <div class="col-md-3"><label class="form-label">Type</label>
                    <select class="form-select" id="pmType" ${isNew ? "" : "disabled"}>
                      <option value="CATEGORY_SALE">Category sale (%)</option>
                      <option value="MEMBER_PRICE">Member price (%)</option>
                      <option value="COUPON_FLAT">Coupon (flat BDT)</option>
                      <option value="COUPON_PERCENT">Coupon (%)</option>
                    </select></div>
                  <div class="col-md-3 d-flex align-items-end pb-1">
                    <label class="form-check ms-2"><input type="checkbox" class="form-check-input me-1" id="pmActive" ${!p || p.active ? "checked" : ""}> Active</label>
                  </div>
                  <div class="col-md-6 pm-only" data-types="CATEGORY_SALE"><label class="form-label">Category</label>
                    <select class="form-select" id="pmCat">${categories.map((c) =>
                        `<option value="${UI.esc(c.id)}" ${p && p.categoryId === c.id ? "selected" : ""}>${UI.esc(c.name.split(" —")[0])}</option>`).join("")}</select></div>
                  <div class="col-md-3 pm-only" data-types="CATEGORY_SALE,MEMBER_PRICE,COUPON_PERCENT"><label class="form-label">Percent off (%)</label>
                    <input type="number" min="0" max="100" step="0.01" class="form-control" id="pmPercent" value="${p && p.percentOff != null ? p.percentOff : 10}"></div>
                  <div class="col-md-3 pm-only" data-types="COUPON_FLAT,COUPON_PERCENT"><label class="form-label">Coupon code</label>
                    <input class="form-control" id="pmCode" value="${p && p.code ? UI.esc(p.code) : ""}" style="text-transform:uppercase"></div>
                  <div class="col-md-3 pm-only" data-types="COUPON_FLAT"><label class="form-label">Flat amount (BDT)</label>
                    <input type="number" min="0" step="0.01" class="form-control" id="pmFlat" value="${p && p.flatAmount != null ? p.flatAmount : 50}"></div>
                  <div class="col-md-4"><label class="form-label">Starts on (optional)</label>
                    <input type="date" class="form-control" id="pmStart" value="${p && p.startsOn ? p.startsOn : ""}"></div>
                  <div class="col-md-4"><label class="form-label">Ends on (optional)</label>
                    <input type="date" class="form-control" id="pmEnd" value="${p && p.endsOn ? p.endsOn : ""}"></div>
                </div>`,
                `<button class="btn mf-btn-primary" id="pmSave">${isNew ? "Create" : "Save"}</button>`);
            UI.$("#pmType").value = p ? p.type : "CATEGORY_SALE";
            const syncType = () => {
                const t = UI.$("#pmType").value;
                UI.$$(".pm-only", UI.$("#modalHost")).forEach((el) =>
                    el.classList.toggle("d-none", !el.dataset.types.split(",").includes(t)));
            };
            UI.$("#pmType").addEventListener("change", syncType);
            syncType();
            UI.$("#pmSave").addEventListener("click", async () => {
                const t = UI.$("#pmType").value;
                const body = {
                    name: UI.$("#pmName").value,
                    type: t,
                    categoryId: t === "CATEGORY_SALE" ? UI.$("#pmCat").value : null,
                    percentOff: ["CATEGORY_SALE", "MEMBER_PRICE", "COUPON_PERCENT"].includes(t)
                        ? UI.$("#pmPercent").value : null,
                    flatAmount: t === "COUPON_FLAT" ? UI.$("#pmFlat").value : null,
                    code: t.startsWith("COUPON") ? ((UI.$("#pmCode").value || "").trim().toUpperCase() || null) : null,
                    startsOn: UI.$("#pmStart").value || null,
                    endsOn: UI.$("#pmEnd").value || null,
                    active: UI.$("#pmActive").checked
                };
                try {
                    if (isNew) await API.savePromotion(body);
                    else await API.updatePromotion(p.id, body);
                    inst.hide();
                    UI.ok(isNew ? "Promotion created — the till picks it up immediately (Strategy)" : "Saved");
                    load();
                } catch (err) { UI.fail(err.message); }
            });
        }

        UI.$("#btnNewPromo").addEventListener("click", () => editModal(null));
        UI.$("#ctRun").addEventListener("click", async () => {
            const code = (UI.$("#ctCode").value || "").trim().toUpperCase();
            if (!code) { UI.fail("Enter a coupon code first"); return; }
            try {
                const r = await API.checkCoupon({ code, netTotal: parseFloat(UI.$("#ctNet").value || "0") });
                UI.$("#ctResult").innerHTML = `<span class="text-success">− ${UI.money(r.amount)}</span>`;
                UI.ok("Coupon " + code + " is worth " + UI.money(r.amount));
            } catch (err) {
                UI.$("#ctResult").innerHTML = `<span class="text-danger">invalid</span>`;
                UI.fail(err.message);
            }
        });
        load();
    }

    // ============================== ACTIVITY LOG ==============================

    async function activity(host) {
        const today = dhakaToday();
        const ACTIONS = ["LOGIN", "LOGIN_FAILED", "LOGOUT", "SALE_VOIDED", "RETURN_PROCESSED",
            "PRODUCT_CREATED", "PRODUCT_UPDATED", "PRODUCT_DELETED", "STOCK_RESTOCKED",
            "SHRINKAGE_RECORDED", "POINTS_ADJUSTED", "PROMOTION_CREATED", "PROMOTION_UPDATED",
            "PROMOTION_DELETED", "PO_SUBMITTED", "PO_CANCELLED", "PO_RECEIVED", "PO_PAID",
            "PO_CLOSED", "TEMPLATE_SAVED", "TEMPLATE_CLONED", "USER_CREATED", "USER_UPDATED"];
        const badgeColor = (a) => {
            if (a.startsWith("LOGIN") || a === "LOGOUT") return "secondary";
            if (a === "SALE_VOIDED" || a === "SHRINKAGE_RECORDED") return "danger";
            if (a === "RETURN_PROCESSED" || a.startsWith("PROMOTION")) return "warning";
            if (a.startsWith("PRODUCT") || a.startsWith("STOCK") || a === "POINTS_ADJUSTED") return "info";
            if (a.startsWith("PO_") || a.startsWith("TEMPLATE")) return "primary";
            if (a.startsWith("USER")) return "dark";
            return "secondary";
        };

        host.innerHTML = `
        <div class="d-flex flex-wrap gap-2 align-items-center mt-2 mb-3">
          <h4 class="mf-page-title mb-0 me-auto"><i class="bi bi-clock-history"></i> Activity log</h4>
          <input type="date" class="form-control form-control-sm mf-w-auto" id="actFrom" value="${today}">
          <input type="date" class="form-control form-control-sm mf-w-auto" id="actTo" value="${today}">
          <select class="form-select form-select-sm mf-w-auto" id="actActor"><option value="">All actors</option></select>
          <select class="form-select form-select-sm mf-w-auto" id="actAction">
            <option value="">All actions</option>
            ${ACTIONS.map((a) => `<option>${a}</option>`).join("")}
          </select>
          <button class="btn mf-btn-primary btn-sm" id="actRefresh"><i class="bi bi-arrow-clockwise"></i> Refresh</button>
        </div>
        <div class="mf-panel p-3" id="actTable">loading…</div>`;

        const load = async () => {
            try {
                const q = {};
                if (UI.$("#actFrom").value) q.from = UI.$("#actFrom").value;
                if (UI.$("#actTo").value) q.to = UI.$("#actTo").value;
                if (UI.$("#actActor").value) q.actor = UI.$("#actActor").value;
                if (UI.$("#actAction").value) q.action = UI.$("#actAction").value;
                q.limit = 200;
                const list = await API.audit(q);
                const actors = [...new Set(list.map((e) => e.actor))].filter((a) => a && a !== "-").sort();
                const sel = UI.$("#actActor");
                const current = sel.value;
                sel.innerHTML = `<option value="">All actors</option>` +
                    actors.map((a) => `<option ${a === current ? "selected" : ""}>${UI.esc(a)}</option>`).join("");
                UI.$("#actTable").innerHTML = UI.table(
                    ["Time", "Actor", "Action", "Target", "Detail"],
                    list.map((e) => `
                      <tr>
                        <td class="small text-secondary text-nowrap">${Views._escDate(e.at)}</td>
                        <td><b>${UI.esc(e.actor)}</b><div class="small text-secondary">${UI.esc(e.role)}</div></td>
                        <td><span class="badge text-bg-${badgeColor(e.action)}">${UI.esc(e.action)}</span></td>
                        <td class="small">${UI.esc(e.targetType)}${e.targetId ? " · " + UI.esc(e.targetId) : ""}</td>
                        <td class="small">${UI.esc(e.detail)}</td>
                      </tr>`).join(""),
                    "No activity in this window");
            } catch (err) { UI.fail(err.message); }
        };

        UI.$("#actRefresh").addEventListener("click", load);
        ["#actFrom", "#actTo", "#actAction", "#actActor"].forEach((sel) => {
            UI.$(sel).addEventListener("change", load);
        });
        load();
    }

    // ============================== DAY CLOSE (Z-REPORT) ==============================

    async function dayClose(host) {
        const today = dhakaToday();
        host.innerHTML = `
        <div class="d-flex flex-wrap gap-2 align-items-center mt-2 mb-3">
          <h4 class="mf-page-title mb-0 me-auto"><i class="bi bi-journal-check"></i> Day close — Z-report</h4>
          <input type="date" class="form-control form-control-sm mf-w-auto" id="dcFrom" value="${today}">
          <input type="date" class="form-control form-control-sm mf-w-auto" id="dcTo" value="${today}">
          <button class="btn mf-btn-primary btn-sm" id="dcLoad"><i class="bi bi-arrow-clockwise"></i> Load preview</button>
        </div>
        <div id="dcBody"><div class="mf-panel p-4 text-center text-secondary">Load the window to see the drawer math.</div></div>
        <div class="mf-panel p-3 mt-3">
          <h6><i class="bi bi-cash-coin"></i> Past closes</h6>
          <div id="dcHistory">loading…</div>
        </div>`;

        let preview = null;

        const loadPreview = async () => {
            const q = {};
            if (UI.$("#dcFrom").value) q.from = UI.$("#dcFrom").value;
            if (UI.$("#dcTo").value) q.to = UI.$("#dcTo").value;
            try {
                preview = await API.dayClosePreview(q);
                renderPreview();
            } catch (err) { UI.fail(err.message); }
        };

        function renderPreview() {
            const p = preview;
            const tenderRows = Object.entries(p.tenders || {}).map(([type, amount]) =>
                `<tr><td><b>${UI.esc(type)}</b></td><td>${UI.money(amount)}</td></tr>`).join("");
            const refundRows = Object.entries(p.refundsByChannel || {}).map(([ch, amount]) =>
                `<tr><td>${UI.esc(ch)}</td><td>${UI.money(amount)}</td></tr>`).join("");
            UI.$("#dcBody").innerHTML = `
              <div class="row g-3">
                ${UI.kpi("Net sales", UI.money(p.net), p.bills + " bills", "bi-cash-stack")}
                ${UI.kpi("Output VAT", UI.money(p.vat), "NBR filing basis", "bi-receipt-cutoff")}
                ${UI.kpi("Expected drawer cash", UI.money(p.expectedDrawerCash), "cashIn − change − refunds − voids", "bi-cash-coin")}
                ${UI.kpi("Voids in window", p.voidsCount, UI.money(p.voidCashOut) + " cash out", "bi-x-octagon")}
              </div>
              <div class="row g-3 mt-1">
                <div class="col-lg-4"><div class="mf-panel p-3 h-100">
                  <h6>Tenders (window)</h6>
                  ${UI.table(["Channel", "Amount"], tenderRows, "No tenders")}
                </div></div>
                <div class="col-lg-4"><div class="mf-panel p-3 h-100">
                  <h6>Returns (${p.returnsCount})</h6>
                  ${UI.table(["Channel", "Refund"], refundRows, "No returns")}
                  <div class="small text-secondary mt-1">Cash refunds reduce the drawer: ${UI.money(p.cashRefunds)}</div>
                </div></div>
                <div class="col-lg-4"><div class="mf-panel p-3 h-100">
                  <h6>Close the day</h6>
                  <label class="form-label small mb-0">Counted drawer cash (BDT)</label>
                  <input type="number" min="0" step="0.01" class="form-control mb-2" id="dcCounted" value="${Number(p.expectedDrawerCash)}">
                  <label class="form-label small mb-0">Note (optional)</label>
                  <input class="form-control mb-2" id="dcNote" placeholder="e.g. till 1, evening shift">
                  <button class="btn mf-btn-primary w-100" id="dcClose"><i class="bi bi-check2-circle"></i> Close &amp; print Z-slip</button>
                  <div class="small text-secondary mt-2">Expected ${UI.money(p.expectedDrawerCash)} = cash in ${UI.money(p.cashIn)} − change ${UI.money(p.changeOut)} − cash refunds ${UI.money(p.cashRefunds)} − void cash ${UI.money(p.voidCashOut)}</div>
                </div></div>
              </div>`;
            UI.$("#dcClose").addEventListener("click", doClose);
        }

        async function doClose() {
            const counted = parseFloat(UI.$("#dcCounted").value || "0");
            try {
                const closed = await API.closeDay({
                    from: UI.$("#dcFrom").value || today,
                    to: UI.$("#dcTo").value || today,
                    countedCash: counted,
                    note: UI.$("#dcNote").value || ""
                });
                const ok = Number(closed.variance) === 0;
                UI.ok(ok ? "Drawer balanced — day closed clean"
                    : "Day closed. Variance " + UI.money(closed.variance) + (Number(closed.variance) < 0 ? " (short)" : " (over)"));
                printZSlip(closed);
                loadHistory();
            } catch (err) { UI.fail(err.message); }
        }

        function printZSlip(z) {
            const row = (label, value) =>
                `<div class="d-flex justify-content-between my-1"><span>${UI.esc(label)}</span><b>${UI.esc(value)}</b></div>`;
            Receipt.printSlip("Z-Report " + z.from, `
              <div class="slip">
                <div class="text-center mb-2">
                  <div class="fs-5 fw-bold">MartFlow Supershop</div>
                  <div class="small">Z-REPORT — DAY CLOSE</div>
                  <div class="small">${UI.esc(z.from)} → ${UI.esc(z.to)}</div>
                </div>
                <hr>
                ${row("Bills:", String(z.bills))}
                ${row("Gross:", UI.money(z.gross).replace("BDT ", ""))}
                ${row("Discounts:", "-" + UI.money(z.discount).replace("BDT ", ""))}
                ${row("Coupons:", "-" + UI.money(z.coupon).replace("BDT ", ""))}
                ${row("NET:", UI.money(z.net).replace("BDT ", ""))}
                ${row("Incl. VAT:", UI.money(z.vat).replace("BDT ", ""))}
                <hr>
                ${Object.entries(z.tenders || {}).map(([t, a]) =>
                    row(t + ":", UI.money(a).replace("BDT ", ""))).join("")}
                <hr>
                ${row("Cash in:", UI.money(z.cashIn).replace("BDT ", ""))}
                ${row("Change out:", "-" + UI.money(z.changeOut).replace("BDT ", ""))}
                ${row("Cash refunds:", "-" + UI.money(z.cashRefunds).replace("BDT ", ""))}
                ${row("Void cash out:", "-" + UI.money(z.voidCashOut).replace("BDT ", ""))}
                ${row("EXPECTED DRAWER:", UI.money(z.expectedDrawerCash).replace("BDT ", ""))}
                <hr>
                ${row("Counted:", UI.money(z.countedCash).replace("BDT ", ""))}
                ${row("VARIANCE:", UI.money(z.variance).replace("BDT ", ""))}
                <hr>
                <div class="text-center small">Closed by ${UI.esc(z.closedBy)} · ${Views._escDate(z.closedAt)}</div>
                ${z.note ? `<div class="text-center small">${UI.esc(z.note)}</div>` : ""}
                <div class="text-center small mt-2">— end of Z-report —</div>
              </div>`);
        }

        async function loadHistory() {
            try {
                const list = await API.dayCloses();
                UI.$("#dcHistory").innerHTML = UI.table(
                    ["Closed", "Window", "Bills", "Net", "Expected", "Counted", "Variance", ""],
                    list.map((z) => `
                      <tr>
                        <td class="small">${Views._escDate(z.closedAt)}</td>
                        <td class="small">${UI.esc(z.from)} → ${UI.esc(z.to)}</td>
                        <td>${z.bills}</td>
                        <td>${UI.money(z.net)}</td>
                        <td>${UI.money(z.expectedDrawerCash)}</td>
                        <td>${UI.money(z.countedCash)}</td>
                        <td class="${Number(z.variance) === 0 ? "text-success" : "text-danger fw-bold"}">${UI.money(z.variance)}</td>
                        <td><button class="btn btn-sm mf-btn-ghost" data-z="${UI.esc(z.id)}" title="Reprint Z-slip"><i class="bi bi-printer"></i></button></td>
                      </tr>`).join(""), "No day closes yet");
                UI.$$("[data-z]").forEach((b) => b.addEventListener("click", () => {
                    printZSlip(list.find((z) => z.id === b.dataset.z));
                }));
            } catch (err) { UI.fail(err.message); }
        }

        UI.$("#dcLoad").addEventListener("click", loadPreview);
        ["#dcFrom", "#dcTo"].forEach((sel) => UI.$(sel).addEventListener("change", loadPreview));
        loadPreview();
        loadHistory();
    }

    // ============================== STAFF ==============================

    async function staff(host) {
        host.innerHTML = `
        <div class="d-flex gap-2 align-items-center mt-2 mb-3">
          <h4 class="mf-page-title mb-0 me-auto"><i class="bi bi-person-gear"></i> Staff accounts</h4>
          <button class="btn mf-btn-primary btn-sm" id="btnNewUser"><i class="bi bi-person-plus"></i> Add staff</button>
        </div>
        <div class="mf-panel p-3" id="userTable"></div>`;

        const load = async () => {
            try {
                const users = await API.users();
                UI.$("#userTable").innerHTML = UI.table(["Username", "Full name", "Role", "Active", "Since", "Actions"],
                    users.map((u) => `
                      <tr>
                        <td><b>${UI.esc(u.username)}</b></td>
                        <td>${UI.esc(u.fullName || "")}</td>
                        <td><span class="badge text-bg-${u.role === "ADMIN" ? "danger" : u.role === "MANAGER" ? "primary" : u.role === "DEVELOPER" ? "info" : "secondary"}">${UI.roleLabel(u.role)}</span></td>
                        <td>${u.active ? "✔" : "✘"}</td>
                        <td class="small">${String(u.createdAt).replace("T", " ").slice(0, 10)}</td>
                        <td class="text-nowrap">
                          <button class="btn btn-sm mf-btn-ghost" data-toggle="${UI.esc(u.id)}" data-active="${u.active}" data-name="${UI.esc(u.username)}">${u.active ? "Disable" : "Enable"}</button>
                          <button class="btn btn-sm mf-btn-ghost" data-pw="${UI.esc(u.id)}" data-name="${UI.esc(u.username)}">Reset password</button>
                        </td>
                      </tr>`).join(""));
                UI.$$("[data-toggle]").forEach((b) => b.addEventListener("click", async () => {
                    try {
                        await API.updateUser(b.dataset.toggle, { active: b.dataset.active !== "true" });
                        UI.ok("Updated"); load();
                    } catch (err) { UI.fail(err.message); }
                }));
                UI.$$("[data-pw]").forEach((b) => b.addEventListener("click", async () => {
                    const pw = window.prompt("New password for " + b.dataset.name + " (min 6 chars):");
                    if (!pw) return;
                    try { await API.updateUser(b.dataset.pw, { password: pw }); UI.ok("Password reset"); }
                    catch (err) { UI.fail(err.message); }
                }));
            } catch (err) { UI.fail(err.message); }
        };
        const btnNewUser = UI.$("#btnNewUser");
        if (btnNewUser) {
            btnNewUser.addEventListener("click", () => {
                const inst = UI.modal("Add staff account", `
                    <div class="row g-3">
                      <div class="col-md-4"><label class="form-label">Username</label><input class="form-control" id="nuUser"></div>
                      <div class="col-md-4"><label class="form-label">Full name</label><input class="form-control" id="nuName"></div>
                      <div class="col-md-4"><label class="form-label">Role</label>
                        <select class="form-select" id="nuRole"><option>CASHIER</option><option>MANAGER</option><option>ADMIN</option><option>DEVELOPER</option></select></div>
                      <div class="col-md-4"><label class="form-label">Password (min 6)</label><input type="password" class="form-control" id="nuPass"></div>
                    </div>`, `<button class="btn mf-btn-primary" id="nuSave">Create</button>`);
                const nuSave = UI.$("#nuSave");
                if (nuSave) {
                    nuSave.addEventListener("click", async () => {
                        try {
                            await API.createUser({
                                username: UI.$("#nuUser").value, fullName: UI.$("#nuName").value,
                                role: UI.$("#nuRole").value, password: UI.$("#nuPass").value
                            });
                            inst.hide(); UI.ok("Account created"); load();
                        } catch (err) { UI.fail(err.message); }
                    });
                }
            });
        }
        load();
    }

    // ============================== DEVELOPER MODE ==============================

    const DEV_RANK = { CASHIER: 0, DEVELOPER: 0, MANAGER: 1, ADMIN: 2 };

    async function patterns(host) {
        host.innerHTML = `
        <h4 class="mf-page-title mt-2"><i class="bi bi-terminal"></i> Developer Mode</h4>
        <p class="text-secondary mt-2 mb-1">Pattern deep-dives with real source, a live API explorer and system diagnostics. This area belongs to the <b>developer</b> account only — business staff never see it.</p>
        <ul class="nav nav-tabs mt-2">
          <li class="nav-item"><a class="nav-link active" data-bs-toggle="tab" href="#devPatternsTab"><i class="bi bi-diagram-3"></i> Patterns</a></li>
          <li class="nav-item"><a class="nav-link" data-bs-toggle="tab" href="#devApiTab"><i class="bi bi-braces"></i> API explorer</a></li>
          <li class="nav-item"><a class="nav-link" data-bs-toggle="tab" href="#devSysTab"><i class="bi bi-activity"></i> Diagnostics</a></li>
        </ul>
        <div class="tab-content mf-panel p-3 border-top-0">
          <div class="tab-pane fade show active" id="devPatternsTab"><div id="patternGrid" class="row g-3">loading…</div></div>
          <div class="tab-pane fade" id="devApiTab">loading…</div>
          <div class="tab-pane fade" id="devSysTab">loading…</div>
        </div>`;

        loadPatterns();
        loadEndpoints();
        loadSystem();

        // ---- Tab 1: pattern catalog (server-owned, drift-guarded) ----
        async function loadPatterns() {
            try {
                const data = await API.devPatterns();
                const grid = UI.$("#patternGrid");
                grid.innerHTML = data.patterns.map((p) => `
                  <div class="col-md-6 col-xl-4">
                    <div class="mf-panel p-3 h-100 mf-pattern-card" data-pattern="${UI.esc(p.id)}">
                      <div class="d-flex align-items-center gap-2 mb-2">
                        <i class="bi ${UI.esc(p.icon)} mf-pattern-icon"></i>
                        <div><b>${UI.esc(p.name)}</b><div class="small text-secondary">${UI.esc(p.category)}</div></div>
                      </div>
                      <div class="small mb-1"><code>${UI.esc((p.classes || []).join(", "))}</code></div>
                      <div class="small text-secondary mb-2">${UI.esc(p.problem)}</div>
                      <button class="btn btn-sm mf-btn-ghost"><i class="bi bi-search"></i> Deep dive</button>
                    </div>
                  </div>`).join("") ||
                  `<div class="text-secondary">No patterns served.</div>`;
                UI.$$("[data-pattern]").forEach((card) => card.addEventListener("click", () =>
                    patternModal(data.patterns.find((x) => x.id === card.dataset.pattern))));
            } catch (err) {
                UI.$("#patternGrid").innerHTML =
                    `<div class="text-danger">${UI.esc(err.message)}</div>`;
            }
        }

        function patternModal(p) {
            const canOpenLive = DEV_RANK[API.role()] >= (DEV_RANK[p.live.minRole] ?? 0);
            const liveHtml = canOpenLive
                ? `<a class="btn mf-btn-primary" href="${UI.esc(p.live.href)}"><i class="bi bi-play"></i> ${UI.esc(p.live.label)}</a>`
                : `<div class="alert alert-warning py-2 small mb-0"><i class="bi bi-person-lock"></i> Runs on <b>${UI.esc(p.live.href)}</b> (${UI.esc(p.live.minRole)} screen) — log in as <b>${UI.esc(p.live.minRole.toLowerCase())}</b> to watch it live. ${UI.esc(p.live.label)}</div>`;
            const inst = UI.modal(`
                <span class="me-auto"><i class="bi ${UI.esc(p.icon)} mf-pattern-icon"></i> ${UI.esc(p.name)}
                  <span class="badge text-bg-secondary ms-2">${UI.esc(p.category)}</span></span>`,
                `<div class="mf-dev-deepdive">
                   <div class="mb-2"><b><i class="bi bi-question-circle"></i> The problem</b>
                     <div class="small">${UI.esc(p.problem)}</div></div>
                   <div class="mb-2"><b><i class="bi bi-lightbulb"></i> Why this pattern</b>
                     <div class="small">${UI.esc(p.whyThisPattern)}</div></div>
                   <div class="mb-2"><b><i class="bi bi-x-circle"></i> The alternative we rejected</b>
                     <div class="small text-secondary">${UI.esc(p.alternative)}</div></div>
                   <div class="mb-2"><b><i class="bi bi-file-earmark-code"></i> Real source</b>
                     <div class="small mb-1">${(p.classes || []).map((c) => `<code>${UI.esc(c)}</code>`).join(" ")}
                       <span class="text-secondary">· ${UI.esc(p.snippet.file)}</span></div>
                     <pre class="mf-code"><code>${UI.esc(p.snippet.code)}</code></pre></div>
                   <div class="mb-2 small"><i class="bi bi-check2-circle text-success"></i> Proved by
                     <code>${UI.esc(p.testClass)}</code> — the pattern's defining behaviour has a green test.</div>
                   ${liveHtml}
                 </div>`);
            return inst;
        }

        // ---- Tab 2: API explorer with try-it ----
        async function loadEndpoints() {
            try {
                const data = await API.devEndpoints();
                UI.$("#devApiTab").innerHTML = `
                  <p class="small text-secondary mb-2">Every registered route, grouped. "Try it" runs the call with your developer token — a 403 is the role gates doing their job, live.</p>
                  ${data.groups.map((g, gi) => `
                    <div class="mf-panel p-2 mb-2">
                      <div class="fw-bold mb-1">${UI.esc(g.name)}</div>
                      ${g.endpoints.map((e, ei) => `
                        <div class="d-flex flex-wrap align-items-center gap-2 py-1 mf-dev-endpoint">
                          <span class="badge mf-method-${e.method}">${e.method}</span>
                          <code>${UI.esc(e.path)}</code>
                          <span class="badge text-bg-${roleBadge(e.minRole)}">${UI.esc(e.minRole)}</span>
                          <span class="small text-secondary flex-grow-1">${UI.esc(e.description)}</span>
                          <button class="btn btn-sm mf-btn-ghost" data-try="${gi}:${ei}"><i class="bi bi-play"></i> Try it</button>
                        </div>`).join("")}
                    </div>`).join("")}`;
                UI.$$("[data-try]").forEach((b) => b.addEventListener("click", () => {
                    const [gi, ei] = b.dataset.try.split(":").map(Number);
                    tryItModal(data.groups[gi].endpoints[ei]);
                }));
            } catch (err) {
                UI.$("#devApiTab").innerHTML = `<div class="text-danger">${UI.esc(err.message)}</div>`;
            }
        }

        const roleBadge = (r) => ({
            PUBLIC: "secondary", AUTH: "secondary", CASHIER: "info",
            MANAGER: "primary", ADMIN: "danger", DEVELOPER: "success"
        }[r] || "secondary");

        function tryItModal(e) {
            const params = [...e.path.matchAll(/\{(\w+)\}/g)].map((m) => m[1]);
            const inst = UI.modal(`Try it — ${e.method} ${UI.esc(e.path)}`, `
                <div class="small text-secondary mb-2">${UI.esc(e.description)} · min role <b>${UI.esc(e.minRole)}</b></div>
                ${params.length ? `<label class="form-label small mb-1">Path parameters</label>
                  <div class="row g-2 mb-2">${params.map((p) => `
                    <div class="col-md-6"><input class="form-control form-control-sm" data-param="${UI.esc(p)}" placeholder="{${UI.esc(p)}}" value="p01"></div>`).join("")}</div>` : ""}
                ${e.method !== "GET" ? `<label class="form-label small mb-1">JSON body</label>
                  <textarea class="form-control font-monospace" rows="5" id="tryBody">{}</textarea>` : ""}`,
                `<button class="btn mf-btn-primary" id="trySend"><i class="bi bi-send"></i> Send</button>`);
            UI.$("#trySend").addEventListener("click", async () => {
                let path = e.path;
                params.forEach((p) => {
                    const input = UI.$(`[data-param="${p}"]`);
                    path = path.replace(`{${p}}`, encodeURIComponent(input ? input.value : ""));
                });
                let body;
                if (e.method !== "GET") {
                    try { body = JSON.parse(UI.$("#tryBody").value || "{}"); }
                    catch (parse) { UI.fail("Body is not valid JSON: " + parse.message); return; }
                }
                const started = performance.now();
                try {
                    const res = await API.raw(e.method, path, body);
                    showResult(200, performance.now() - started, res);
                } catch (err) {
                    showResult(err.status || 0, performance.now() - started, err.payload || { error: err.message });
                }
            });
            function showResult(status, ms, payload) {
                const good = status >= 200 && status < 300;
                const forbidden = status === 403;
                UI.$("#tryBody")?.closest(".modal-body");
                const box = UI.$("#modalHost .modal-body");
                const existing = UI.$("#tryResult", box);
                const html = `
                  <div id="tryResult" class="mt-3">
                    <div class="d-flex align-items-center gap-2 mb-1">
                      <span class="badge ${good ? "text-bg-success" : forbidden ? "text-bg-warning" : "text-bg-danger"}">${status || "ERR"}${forbidden ? " — the role gate said no" : ""}</span>
                      <span class="small text-secondary">${Math.round(ms)} ms</span>
                    </div>
                    <pre class="mf-code" style="max-height: 300px;"><code>${UI.esc(JSON.stringify(payload, null, 2))}</code></pre>
                  </div>`;
                if (existing) existing.outerHTML = html;
                else box.insertAdjacentHTML("beforeend", html);
            }
        }

        // ---- Tab 3: diagnostics ----
        async function loadSystem() {
            try {
                const s = await API.devSystem();
                const inMemory = s.persistence.mode !== "MONGO";
                UI.$("#devSysTab").innerHTML = `
                  ${inMemory ? `<div class="alert alert-warning py-2 small"><i class="bi bi-exclamation-triangle"></i> In-memory mode — a restart loses all data. Set MONGODB_URI to persist.</div>` : ""}
                  <div class="row g-3">
                    ${UI.kpi("Persistence", s.persistence.mode, s.persistence.database, inMemory ? "bi-memory" : "bi-database")}
                    ${UI.kpi("Active sessions", s.sessions.activeTokens, "bearer tokens live", "bi-person-badge")}
                    ${UI.kpi("Alerts", s.alerts.total, s.alerts.unread + " unread", "bi-bell")}
                    ${UI.kpi("Uptime", Math.floor(s.app.uptimeSeconds / 60) + "m", "Java " + s.app.javaVersion + " · Boot " + s.app.springBootVersion, "bi-stopwatch")}
                  </div>
                  <div class="row g-3 mt-1">
                    <div class="col-lg-6">
                      <h6>Repository counts</h6>
                      ${UI.table(["Collection", "Documents"], Object.entries(s.counts)
                        .map(([k, v]) => `<tr><td>${UI.esc(k)}</td><td><b>${UI.qty(v)}</b></td></tr>`).join(""))}
                    </div>
                    <div class="col-lg-6">
                      <h6>Clock &amp; connection</h6>
                      ${UI.table(["", ""], `
                        <tr><td>Now (${UI.esc(s.clock.zone)})</td><td class="small">${Views._escDate(s.clock.now)}</td></tr>
                        <tr><td>Connected at</td><td class="small">${s.persistence.connectedAt ? Views._escDate(s.persistence.connectedAt) : "—"}</td></tr>`)}
                      <div class="small text-secondary mt-2">Business time is pinned to Asia/Dhaka — every report boundary and receipt number follows it.</div>
                    </div>
                  </div>`;
            } catch (err) {
                UI.$("#devSysTab").innerHTML = `<div class="text-danger">${UI.esc(err.message)}</div>`;
            }
        }
    }

    return { reports, alerts, staff, patterns, sales, promotions, activity, dayClose };
})());
