/** MartFlow bootstrap: app shell, navigation, alert polling, route registration. */
(() => {
    const NAV = [{ path: "/pos", title: "POS — New Bill", icon: "bi-cash-register" }];

    function shell() {
        const user = API.user();
        const items = Router.navFor(user ? user.role : null);
        UI.$("#app").innerHTML = `
        <nav class="navbar mf-navbar sticky-top">
          <div class="container-fluid">
            <a class="navbar-brand mf-brand" href="#/pos">
              <i class="bi bi-shop"></i> Mart<b>Flow</b>
              <span class="mf-brand-sub">Supershop Suite</span>
            </a>
            <div class="d-flex align-items-center gap-2 flex-wrap mf-nav" id="navItems">
              ${items.map((i) => `
                <a class="mf-nav-item" data-route="${i.path}" href="#${i.path}">
                  <i class="bi ${i.icon}"></i><span>${UI.esc(i.title)}</span>
                </a>`).join("")}
              <a class="mf-nav-item mf-alerts-btn" href="#/alerts" title="Alerts">
                <i class="bi bi-bell"></i><span id="alertBadge" class="badge text-bg-danger d-none">0</span>
              </a>
            </div>
            <div class="d-flex align-items-center gap-2">
              <button class="btn btn-sm mf-theme-toggle" id="themeToggleBtn" title="Toggle dark/light mode" aria-label="Toggle theme">
                <i class="bi bi-moon-stars-fill"></i>
              </button>
              <span class="mf-user-chip"><i class="bi bi-person-circle"></i>
                ${UI.esc(user ? user.username : "")} · ${UI.roleLabel(user ? user.role : "")}</span>
              <button class="btn btn-sm mf-btn-ghost" id="logoutBtn"><i class="bi bi-box-arrow-right"></i> Log out</button>
            </div>
          </div>
        </nav>
        <main class="container-fluid mf-main">
          <div id="viewHost"></div>
        </main>
        <div id="modalHost"></div>
        <div id="toasts" class="toast-container position-fixed bottom-0 end-0 p-3"></div>`;

        UI.initTheme();

        const themeBtn = UI.$("#themeToggleBtn");
        if (themeBtn) themeBtn.addEventListener("click", () => UI.toggleTheme());

        const logoutBtn = UI.$("#logoutBtn");
        if (logoutBtn) logoutBtn.addEventListener("click", async () => {
            try { await API.logout(); } catch (ignored) { /* session may already be gone */ }
            API.clearSession();
            window.location.hash = "#/login";
        });
    }

    let alertTimer = null;
    async function pollAlerts() {
        try {
            const unread = await API.alerts({ unreadOnly: true });
            const badge = UI.$("#alertBadge");
            if (!badge) return;
            if (unread.length > 0) {
                badge.textContent = unread.length > 20 ? "20+" : unread.length;
                badge.classList.remove("d-none");
            } else {
                badge.classList.add("d-none");
            }
        } catch (err) {
            if (err.status === 401) clearInterval(alertTimer);
        }
    }

    function boot() {
        UI.initTheme();
        registerViews();
        // refresh the cached identity from the server — role changes (e.g. a demotion by the
        // owner) take effect on next boot instead of living forever in localStorage
        if (API.authed()) {
            API.me().then((me) => {
                const prev = API.user() || {};
                API.setSession(API.token(), {
                    username: me.username, fullName: prev.fullName || "", role: me.role
                });
            }).catch(() => { /* the request wrapper's 401 path handles expired sessions */ });
        }
        const needsLogin = !API.authed() || (window.location.hash || "") === "#/login";
        if (needsLogin) {
            window.location.hash = "#/login";
        }
        window.addEventListener("hashchange", () => {
            const login = window.location.hash === "#/login";
            if (login || !API.authed()) {
                renderLogin();
            } else if (!UI.$("#navItems")) {
                shell();
                Router.run();
                clearInterval(alertTimer);
                alertTimer = setInterval(pollAlerts, 8000);
                pollAlerts();
            }
        });
        if (API.authed() && window.location.hash !== "#/login") {
            shell();
            Router.run();
            alertTimer = setInterval(pollAlerts, 8000);
            pollAlerts();
        } else {
            renderLogin();
        }
    }

    function renderLogin() {
        UI.$("#app").innerHTML = `
        <div class="mf-login-wrap">
          <div class="position-absolute top-0 end-0 p-3">
            <button class="btn btn-sm mf-theme-toggle" id="loginThemeToggle" title="Toggle dark/light mode" aria-label="Toggle theme">
              <i class="bi bi-moon-stars-fill"></i>
            </button>
          </div>
          <div class="mf-panel mf-login">
            <div class="text-center mb-4">
              <div class="mf-logo"><i class="bi bi-shop"></i></div>
              <h1 class="mf-login-title">Mart<b>Flow</b></h1>
              <p class="text-secondary">Supershop Retail Management Suite</p>
            </div>
            <form id="loginForm">
              <div class="mb-3">
                <label class="form-label">Username</label>
                <input class="form-control" name="username" autocomplete="username" required autofocus>
              </div>
              <div class="mb-3">
                <label class="form-label">Password</label>
                <input class="form-control" type="password" name="password" autocomplete="current-password" required>
              </div>
              <div id="loginError" class="text-danger mb-2 d-none"></div>
              <button class="btn mf-btn-primary w-100">Sign in <i class="bi bi-box-arrow-in-right"></i></button>
            </form>
            <div class="mf-demo-chips mt-4">
              <span class="text-secondary small">Demo accounts (password = role + 123):</span><br>
              <button class="btn btn-sm mf-chip" data-u="admin">admin / owner</button>
              <button class="btn btn-sm mf-chip" data-u="manager">manager</button>
              <button class="btn btn-sm mf-chip" data-u="cashier">cashier</button>
              <button class="btn btn-sm mf-chip" data-u="developer">developer / dev mode</button>
            </div>
          </div>
        </div>
        <div id="toasts" class="toast-container position-fixed bottom-0 end-0 p-3"></div>`;

        UI.initTheme();

        const loginThemeBtn = UI.$("#loginThemeToggle");
        if (loginThemeBtn) loginThemeBtn.addEventListener("click", () => UI.toggleTheme());

        const loginForm = UI.$("#loginForm");
        if (loginForm) {
            loginForm.addEventListener("submit", async (e) => {
                e.preventDefault();
                const fd = new FormData(e.target);
                try {
                    const session = await API.login(fd.get("username"), fd.get("password"));
                    API.setSession(session.token, {
                        username: session.username, fullName: session.fullName, role: session.role
                    });
                    UI.ok("Welcome back, " + session.username + "!");
                    window.location.hash = "#/pos";
                    shell();
                    Router.run();
                    clearInterval(alertTimer);
                    alertTimer = setInterval(pollAlerts, 8000);
                    pollAlerts();
                } catch (err) {
                    const box = UI.$("#loginError");
                    if (box) {
                        box.textContent = err.message;
                        box.classList.remove("d-none");
                    }
                }
            });
        }
        UI.$$(".mf-chip").forEach((chip) => chip.addEventListener("click", () => {
            const u = chip.dataset.u;
            const uInp = UI.$('#loginForm input[name="username"]');
            const pInp = UI.$('#loginForm input[name="password"]');
            if (uInp) uInp.value = u;
            if (pInp) pInp.value = u + "123";
            const form = UI.$("#loginForm");
            if (form) form.requestSubmit();
        }));
    }

    function registerViews() {
        Router.define("/login", { render: async () => renderLogin(), title: "Login" });
        Router.define("/pos", { render: (host) => Views.pos(host), title: "POS", icon: "bi-cash-register" });
        Router.define("/dashboard", { render: (host) => Views.dashboard(host), title: "Dashboard", roles: ["MANAGER", "ADMIN"], icon: "bi-speedometer2" });
        Router.define("/inventory", { render: (host) => Views.inventory(host), title: "Inventory", icon: "bi-boxes" });
        Router.define("/purchases", { render: (host) => Views.purchases(host), title: "Purchases", roles: ["MANAGER", "ADMIN"], icon: "bi-truck" });
        Router.define("/customers", { render: (host) => Views.customers(host), title: "Loyalty", icon: "bi-people" });
        Router.define("/returns", { render: (host) => Views.returns(host), title: "Returns", icon: "bi-arrow-counterclockwise" });
        Router.define("/reports", { render: (host) => Views.reports(host), title: "Reports", icon: "bi-file-earmark-bar-graph" });
        Router.define("/alerts", { render: (host) => Views.alerts(host), title: "Alerts", icon: "bi-bell" });
        Router.define("/sales", { render: (host) => Views.sales(host), title: "Sales Explorer", roles: ["MANAGER", "ADMIN"], icon: "bi-journal-text" });
        Router.define("/promotions", { render: (host) => Views.promotions(host), title: "Promotions", roles: ["MANAGER", "ADMIN"], icon: "bi-tags" });
        Router.define("/activity", { render: (host) => Views.activity(host), title: "Activity Log", roles: ["MANAGER", "ADMIN"], icon: "bi-clock-history" });
        Router.define("/day-close", { render: (host) => Views.dayClose(host), title: "Day Close (Z)", roles: ["MANAGER", "ADMIN"], icon: "bi-journal-check" });
        Router.define("/staff", { render: (host) => Views.staff(host), title: "Staff", roles: ["ADMIN"], icon: "bi-person-gear" });
        Router.define("/patterns", { render: (host) => Views.patterns(host), title: "Developer Mode", roles: ["DEVELOPER"], icon: "bi-terminal" });
    }

    boot();
})();
