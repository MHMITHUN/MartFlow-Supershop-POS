/**
 * MartFlow API client: bearer-token fetch wrapper with a single 401 path — expired session
 * drops you back at the login screen. No frameworks.
 */
const API = (() => {
    const BASE = "/api";
    let token = localStorage.getItem("martflow.token") || null;
    let currentUser = JSON.parse(localStorage.getItem("martflow.user") || "null");

    const authed = () => !!token;

    const setSession = (t, user) => {
        token = t;
        currentUser = user;
        localStorage.setItem("martflow.token", t);
        localStorage.setItem("martflow.user", JSON.stringify(user));
    };

    const clearSession = () => {
        token = null;
        currentUser = null;
        localStorage.removeItem("martflow.token");
        localStorage.removeItem("martflow.user");
    };

    function toSearchParams(query) {
        const params = new URLSearchParams();
        if (query) {
            for (const [k, v] of Object.entries(query)) {
                if (v !== undefined && v !== null && v !== "") {
                    params.set(k, v);
                }
            }
        }
        return params;
    }

    async function request(method, path, body, query) {
        const params = toSearchParams(query);
        const url = BASE + path + (params.toString() ? "?" + params.toString() : "");
        const opts = { method, headers: {} };
        if (token) opts.headers["Authorization"] = "Bearer " + token;
        if (body !== undefined) {
            opts.headers["Content-Type"] = "application/json";
            opts.body = JSON.stringify(body);
        }
        const res = await fetch(url, opts);
        if (res.status === 401 && !path.startsWith("/auth/login")) {
            clearSession();
            window.location.hash = "#/login";
            throw new Error("Session expired — please log in again");
        }
        if (res.status === 204) return null;
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            const err = new Error(data.message || data.error || "Request failed");
            err.status = res.status;
            err.payload = data;
            throw err;
        }
        return data;
    }

    /** CSV download (reports) — triggers the browser file save. */
    async function downloadCsv(path, query) {
        const params = toSearchParams(query);
        params.set("format", "csv");
        const res = await fetch(BASE + path + "?" + params.toString(), {
            headers: { Authorization: "Bearer " + token }
        });
        if (!res.ok) throw new Error("CSV download failed");
        const disposition = res.headers.get("Content-Disposition") || "";
        const match = disposition.match(/filename=([^;]+)/);
        const blob = await res.blob();
        const a = document.createElement("a");
        a.href = URL.createObjectURL(blob);
        a.download = match ? match[1] : "martflow-report.csv";
        a.click();
        URL.revokeObjectURL(a.href);
    }

    return {
        authed, setSession, clearSession,
        user: () => currentUser,
        token: () => token,
        role: () => (currentUser ? currentUser.role : null),

        // auth
        login: (username, password) => request("POST", "/auth/login", { username, password }),
        logout: () => request("POST", "/auth/logout", {}),
        me: () => request("GET", "/auth/me"),
        // catalog
        products: (q) => request("GET", "/products", undefined, q),
        product: (id) => request("GET", `/products/${id}`),
        byBarcode: (code) => request("GET", `/products/barcode/${code}`),
        categories: () => request("GET", "/categories"),
        createProduct: (body) => request("POST", "/products", body),
        updateProduct: (id, body) => request("PUT", `/products/${id}`, body),
        deleteProduct: (id) => request("DELETE", `/products/${id}`),
        restock: (id, body) => request("POST", `/products/${id}/restock`, body),
        adjustStock: (id, body) => request("POST", `/products/${id}/adjust`, body),
        // bill
        bill: () => request("GET", "/bill"),
        addLine: (body) => request("POST", "/bill/lines", body),
        updateLine: (index, body) => request("PUT", `/bill/lines/${index}`, body),
        removeLine: (index) => request("DELETE", `/bill/lines/${index}`),
        clearBill: () => request("DELETE", "/bill"),
        undo: () => request("POST", "/bill/undo", {}),
        setBillCustomer: (body) => request("PUT", "/bill/customer", body),
        setBillCoupon: (body) => request("PUT", "/bill/coupon", body),
        setBillCharges: (body) => request("PUT", "/bill/charges", body),
        tender: (body) => request("POST", "/bill/tender", body),
        // sales
        sales: (q) => request("GET", "/sales", undefined, q),
        sale: (receiptNo) => request("GET", `/sales/${receiptNo}`),
        voidSale: (receiptNo, reason) => request("POST", `/sales/${receiptNo}/void`, { reason }),
        // returns
        createReturn: (receiptNo, body) => request("POST", `/sales/${receiptNo}/returns`, body),
        returns: (q) => request("GET", "/returns", undefined, q),
        // promotions + customers
        promotions: () => request("GET", "/promotions"),
        savePromotion: (body) => request("POST", "/promotions", body),
        updatePromotion: (id, body) => request("PUT", `/promotions/${id}`, body),
        deletePromotion: (id) => request("DELETE", `/promotions/${id}`),
        checkCoupon: (body) => request("POST", "/promotions/validate", body),
        customers: (q) => request("GET", "/customers", undefined, q),
        customer: (id) => request("GET", `/customers/${id}`),
        registerCustomer: (body) => request("POST", "/customers", body),
        adjustPoints: (id, points) => request("POST", `/customers/${id}/points/adjust`, { points }),
        // suppliers + POs
        suppliers: () => request("GET", "/suppliers"),
        registerSupplier: (body) => request("POST", "/suppliers", body),
        purchaseOrders: (q) => request("GET", "/purchase-orders", undefined, q),
        purchaseOrder: (poNo) => request("GET", `/purchase-orders/${poNo}`),
        createPo: (body) => request("POST", "/purchase-orders", body),
        poAction: (poNo, action, body) => request("POST", `/purchase-orders/${poNo}/${action}`, body || {}),
        poFromTemplate: (body) => request("POST", "/purchase-orders/from-template", body),
        poTemplates: () => request("GET", "/purchase-orders/templates"),
        savePoTemplate: (body) => request("POST", "/purchase-orders/templates", body),
        // reports + alerts + users + audit
        audit: (q) => request("GET", "/audit", undefined, q),
        report: (key, q) => request("GET", `/reports/${key}`, undefined, q),
        dayClosePreview: (q) => request("GET", "/reports/day-close/preview", undefined, q),
        closeDay: (body) => request("POST", "/reports/day-close", body),
        dayCloses: () => request("GET", "/reports/day-close"),
        dashboard: () => request("GET", "/reports/dashboard"),
        alerts: (q) => request("GET", "/alerts", undefined, q),
        markAlertRead: (id) => request("POST", `/alerts/${id}/read`, {}),
        users: () => request("GET", "/users"),
        createUser: (body) => request("POST", "/users", body),
        updateUser: (id, body) => request("PUT", `/users/${id}`, body),
        // developer mode
        devPatterns: () => request("GET", "/dev/patterns"),
        devEndpoints: () => request("GET", "/dev/endpoints"),
        devSystem: () => request("GET", "/dev/system"),
        raw: (method, fullPath, body) => request(method,
            fullPath.startsWith("/api") ? fullPath.slice(4) : fullPath, body),
        downloadCsv
    };
})();
