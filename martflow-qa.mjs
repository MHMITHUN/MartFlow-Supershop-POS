export default async function run(page, ui) {
    const out = {};

    // (1) login page: title, favicon, chips
    await page.waitForSelector("#loginForm");
    out.title = await page.title();
    out.faviconLink = await page.evaluate(() => !!document.querySelector('link[rel="icon"]'));
    out.developerChip = await page.evaluate(() => !!document.querySelector('[data-u="developer"]'));

    // (2) developer login
    await page.locator('[data-u="developer"]').click();
    await page.waitForSelector("#navItems");
    out.devNav = await page.evaluate(() =>
        Array.from(document.querySelectorAll("#navItems .mf-nav-item span")).map((s) => s.textContent.trim()));
    out.devHasNoManagerScreens = !out.devNav.some((t) => ["Dashboard", "Purchases", "Staff", "Sales Explorer", "Promotions", "Activity Log", "Day Close (Z)"].includes(t));
    out.devHasDevMode = out.devNav.includes("Developer Mode");

    // (3) developer mode patterns
    await page.evaluate(() => { window.location.hash = "#/patterns"; });
    await page.waitForSelector("[data-pattern]");
    out.patternCards = await page.evaluate(() => document.querySelectorAll("[data-pattern]").length);
    await page.locator("[data-pattern]").first().click();
    await page.waitForSelector(".mf-dev-deepdive");
    out.modalHasProblem = await page.evaluate(() => document.querySelector(".mf-dev-deepdive").textContent.includes("The problem"));
    out.modalHasSnippet = await page.evaluate(() => !!document.querySelector(".mf-dev-deepdive .mf-code"));
    out.modalHasTest = await page.evaluate(() => document.querySelector(".mf-dev-deepdive").textContent.includes("Proved by"));
    await page.keyboard.press("Escape");
    await page.waitForTimeout(400);

    // (4) API explorer + diagnostics tabs present
    out.devTabs = await page.evaluate(() =>
        Array.from(document.querySelectorAll(".nav-tabs .nav-link")).map((a) => a.textContent.trim()));

    // diagnostics tab content
    await page.locator('.nav-tabs .nav-link:has-text("Diagnostics")').click();
    await page.waitForSelector("#devSysTab .mf-kpi");
    out.diagnosticsMode = await page.evaluate(() => document.querySelector("#devSysTab").textContent.includes("IN_MEMORY"));

    // (5) logout → manager login
    await page.locator("#logoutBtn").click();
    await page.waitForSelector("#loginForm");
    await page.locator('[data-u="manager"]').click();
    await page.waitForSelector("#navItems");
    out.managerNav = await page.evaluate(() =>
        Array.from(document.querySelectorAll("#navItems .mf-nav-item span")).map((s) => s.textContent.trim()));
    out.managerHasNoDevMode = !out.managerNav.includes("Developer Mode");

    // day close
    await page.evaluate(() => { window.location.hash = "#/day-close"; });
    await page.waitForSelector("#dcBody .mf-kpi, #dcBody .mf-panel");
    await page.waitForFunction(() => (document.querySelector("#dcBody") || {}).textContent?.includes("Expected drawer cash"), null, { timeout: 8000 }).catch(() => {});
    out.dayCloseKpi = await page.evaluate(() => (document.querySelector("#dcBody") || {}).textContent?.includes("Expected drawer cash"));

    // sales explorer
    await page.evaluate(() => { window.location.hash = "#/sales"; });
    await page.waitForSelector("#salesTable");
    out.salesTableHeaders = await page.evaluate(() =>
        Array.from(document.querySelectorAll("#salesTable th")).map((t) => t.textContent.trim()));

    // promotions
    await page.evaluate(() => { window.location.hash = "#/promotions"; });
    await page.waitForSelector("#promoTable");
    await page.waitForTimeout(600);
    out.promotionsLoaded = await page.evaluate(() => (document.querySelector("#promoTable") || {}).textContent?.includes("Monsoon Beverages Sale"));

    // activity log
    await page.evaluate(() => { window.location.hash = "#/activity"; });
    await page.waitForSelector("#actTable");
    await page.waitForFunction(() => (document.querySelector("#actTable") || {}).textContent?.includes("LOGIN"), null, { timeout: 8000 }).catch(() => {});
    out.activityHasLogin = await page.evaluate(() => (document.querySelector("#actTable") || {}).textContent?.includes("LOGIN"));

    return out;
}
