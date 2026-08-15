/**
 * Hash router with an auth guard and role-aware navigation. Views register themselves with
 * route(), the router renders into #viewHost on every hash change.
 */
const Router = (() => {
    const routes = new Map();
    let currentCleanup = null;

    function define(path, { render, title, roles, icon }) {
        routes.set(path, { render, title, roles, icon });
    }

    function navFor(role) {
        const items = [];
        for (const [path, route] of routes) {
            if (route.icon && (!route.roles || route.roles.includes(role))) {
                items.push({ path, title: route.title, icon: route.icon });
            }
        }
        return items;
    }

    /** Query params of the current hash route (e.g. #/inventory?view=low_stock → {view:"low_stock"}). */
    function query() {
        const qs = (window.location.hash || "").split("?")[1] || "";
        const out = {};
        new URLSearchParams(qs).forEach((v, k) => { out[k] = v; });
        return out;
    }

    async function run() {
        if (currentCleanup) {
            try { currentCleanup(); } catch (ignored) { /* view already gone */ }
            currentCleanup = null;
        }
        const hash = window.location.hash || "#/pos";
        const bare = hash.split("?")[0];
        const path = bare.startsWith("#/") ? bare.slice(1) : "/" + bare.slice(1);
        if (path === "/login") {
            return; // the shell bootstrap owns the login screen
        }
        const route = routes.get(path) || routes.get("/pos");

        if (!API.authed()) {
            window.location.hash = "#/login";
            return;
        }
        if (route.roles && !route.roles.includes(API.role())) {
            UI.fail("Your role cannot open that page");
            window.location.hash = "#/pos";
            return;
        }

        document.querySelectorAll(".mf-nav-item").forEach((el) =>
            el.classList.toggle("active", el.dataset.route === path));
        const host = UI.$("#viewHost");
        host.innerHTML = "";
        try {
            currentCleanup = (await route.render(host)) || null;
        } catch (err) {
            host.innerHTML = `<div class="mf-panel p-4 mt-3 text-danger">${UI.esc(err.message)}</div>`;
        }
    }

    window.addEventListener("hashchange", run);
    return { define, run, navFor, query };
})();
