/** Tiny DOM + formatting helpers shared by every view. */
const UI = (() => {
    const $ = (s, root) => (root || document).querySelector(s);
    const $$ = (s, root) => Array.from((root || document).querySelectorAll(s));
    const esc = (s) => String(s == null ? "" : s).replace(/[&<>"']/g,
        (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
    const money = (n) => "BDT " + Number(n || 0).toLocaleString("en-BD", {
        minimumFractionDigits: 2, maximumFractionDigits: 2
    });
    const qty = (n) => Number(n || 0).toLocaleString("en-BD", { maximumFractionDigits: 3 });

    const ROLE_LABEL = { ADMIN: "Owner", MANAGER: "Manager", CASHIER: "Cashier", DEVELOPER: "Developer" };
    const roleLabel = (r) => ROLE_LABEL[r] || r || "";

    const poBadge = (status) => {
        const cls = {
            DRAFT: "secondary", ORDERED: "primary", PARTIALLY_RECEIVED: "info",
            RECEIVED: "success", CLOSED: "dark", CANCELLED: "danger"
        }[status] || "secondary";
        return `<span class="badge text-bg-${cls}">${esc(status)}</span>`;
    };
    const saleBadge = (status) => {
        const cls = {
            COMPLETED: "success", VOIDED: "danger",
            PARTIALLY_RETURNED: "warning", RETURNED: "info"
        }[status] || "secondary";
        return `<span class="badge text-bg-${cls}">${esc(status)}</span>`;
    };

    function toast(message, kind) {
        const host = $("#toasts");
        if (!host) return;
        const el = document.createElement("div");
        el.className = `toast align-items-center text-bg-${kind || "primary"} border-0`;
        el.innerHTML = `<div class="d-flex"><div class="toast-body">${esc(message)}</div>
            <button class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button></div>`;
        host.appendChild(el);
        const t = new bootstrap.Toast(el, { delay: 3200 });
        t.show();
        el.addEventListener("hidden.bs.toast", () => el.remove());
    }

    const ok = (m) => toast(m, "success");
    const fail = (m) => toast(m, "danger");

    function modal(title, bodyHtml, footerHtml) {
        const host = $("#modalHost");
        host.innerHTML = `
        <div class="modal fade" tabindex="-1">
          <div class="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable">
            <div class="modal-content mf-panel">
              <div class="modal-header">
                <h5 class="modal-title">${title}</h5>
                <button class="btn-close" data-bs-dismiss="modal"></button>
              </div>
              <div class="modal-body">${bodyHtml}</div>
              <div class="modal-footer">${footerHtml || ""}</div>
            </div>
          </div>
        </div>`;
        const el = host.firstElementChild;
        const inst = new bootstrap.Modal(el);
        el.addEventListener("hidden.bs.modal", () => inst.dispose());
        inst.show();
        return inst;
    }

    /** One KPI tile for the dashboard. */
    const kpi = (label, value, hint, icon) => `
        <div class="col-6 col-lg-3">
          <div class="mf-kpi mf-panel h-100">
            <div class="mf-kpi-icon"><i class="bi ${icon || "bi-graph-up"}"></i></div>
            <div>
              <div class="mf-kpi-value">${value}</div>
              <div class="mf-kpi-label">${esc(label)}</div>
              ${hint ? `<div class="mf-kpi-hint">${esc(hint)}</div>` : ""}
            </div>
          </div>
        </div>`;

    /** Plain table renderer: headers + rows of strings/HTML. */
    const table = (headers, rowsHtml, empty) => `
        <div class="table-responsive"><table class="table mf-table align-middle">
          <thead><tr>${headers.map((h) => `<th>${h}</th>`).join("")}</tr></thead>
          <tbody>${rowsHtml || `<tr><td colspan="${headers.length}" class="text-center text-secondary py-4">${esc(empty || "Nothing here yet")}</td></tr>`}</tbody>
        </table></div>`;

    function getTheme() {
        return localStorage.getItem("mf_theme") || "dark";
    }

    function setTheme(theme) {
        localStorage.setItem("mf_theme", theme);
        document.documentElement.setAttribute("data-theme", theme);
        document.documentElement.setAttribute("data-bs-theme", theme);
        if (document.body) {
            document.body.setAttribute("data-theme", theme);
            document.body.setAttribute("data-bs-theme", theme);
        }
        const themeColor = $("meta[name=theme-color]");
        if (themeColor) themeColor.setAttribute("content", theme === "dark" ? "#0b1315" : "#f2f7f5");
        $$(".mf-theme-toggle i").forEach((icon) => {
            if (theme === "dark") {
                icon.className = "bi bi-moon-stars-fill text-info";
            } else {
                icon.className = "bi bi-sun-fill text-warning";
            }
        });
        $$(".mf-theme-toggle").forEach((btn) => {
            btn.setAttribute("title", theme === "dark" ? "Switch to light mode" : "Switch to dark mode");
            btn.setAttribute("aria-label", theme === "dark" ? "Switch to light mode" : "Switch to dark mode");
        });
    }

    function toggleTheme() {
        const current = getTheme();
        const next = current === "dark" ? "light" : "dark";
        setTheme(next);
        return next;
    }

    function initTheme() {
        setTheme(getTheme());
    }

    return { $, $$, esc, money, qty, roleLabel, poBadge, saleBadge, toast, ok, fail, modal, kpi, table,
        getTheme, setTheme, toggleTheme, initTheme };
})();
