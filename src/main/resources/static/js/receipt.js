/**
 * Shared receipt rendering + thermal slip printing. Extracted from the POS view so the
 * Sales Explorer (reprint / void) and Day-Close (Z-slip) screens reuse the same
 * components instead of each keeping a copy.
 *
 * Receipt.show(sale, { onNext, actions })
 *   - onNext: when set, renders the "Next customer (Enter ↵)" footer button and wires
 *     the Enter key to close the modal and continue (POS passes scan focus back).
 *   - actions: extra footer HTML (e.g. a Void button from the Sales Explorer).
 * Receipt.printSlip(title, slipInnerHtml) prints any 320px thermal-style slip through
 * a hidden iframe with its own self-contained stylesheet.
 */
const Receipt = (() => {

    function generateBarcodeSvg(text) {
        const str = String(text || "MF-00000000-000");
        const bars = [];
        let x = 10;
        for (let i = 0; i < str.length; i++) {
            const code = str.charCodeAt(i);
            const w1 = (code % 3) + 1;
            const w2 = ((code >> 1) % 2) + 1;
            const w3 = ((code >> 2) % 3) + 1;
            bars.push(`<rect x="${x}" y="0" width="${w1}" height="28" fill="currentColor"/>`);
            x += w1 + 2;
            bars.push(`<rect x="${x}" y="0" width="${w2}" height="28" fill="currentColor"/>`);
            x += w2 + 1;
            bars.push(`<rect x="${x}" y="0" width="${w3}" height="28" fill="currentColor"/>`);
            x += w3 + 2;
        }
        return `<svg class="mf-receipt-barcode" style="max-width: 220px; height: 32px; margin: 0 auto; display: block;" viewBox="0 0 ${x + 10} 30" xmlns="http://www.w3.org/2000/svg">${bars.join("")}</svg>`;
    }

    function formatPlainTextReceipt(sale) {
        const line = "----------------------------------------";
        const dblLine = "========================================";
        const pad = (left, right, width = 40) => {
            const l = String(left || "");
            const r = String(right || "");
            const spaces = Math.max(1, width - l.length - r.length);
            return l + " ".repeat(spaces) + r;
        };
        let text = `${dblLine}\n           MARTFLOW SUPERSHOP\n       Retail Management Suite\n${dblLine}\n`;
        text += `Receipt: ${sale.receiptNo}\n`;
        text += `Date:    ${sale.at.replace("T", " ").slice(0, 19)}\n`;
        text += `Cashier: ${sale.cashier}\n`;
        text += `${line}\n`;
        (sale.lines || []).forEach((l) => {
            const item = `${l.name} x ${Number(l.quantity || 0)}`;
            const price = UI.money(l.net);
            text += pad(item, price) + "\n";
        });
        text += `${line}\n`;
        if (sale.totals.discount > 0) text += pad("Promotions / Discount:", "-" + UI.money(sale.totals.discount)) + "\n";
        if (sale.totals.coupon > 0) text += pad("Coupon Savings:", "-" + UI.money(sale.totals.coupon)) + "\n";
        if (sale.totals.roundOff != 0) text += pad("Round off:", UI.money(sale.totals.roundOff)) + "\n";
        text += pad("NET PAYABLE:", UI.money(sale.totals.net)) + "\n";
        text += pad("  (incl. VAT):", UI.money(sale.totals.vat)) + "\n";
        text += `${line}\n`;
        (sale.tenders || []).forEach((t) => {
            text += pad("Paid (" + t.type + "):", UI.money(t.amount)) + "\n";
        });
        text += pad("Change Returned:", UI.money(sale.totals.change)) + "\n";
        text += `${dblLine}\n    VAT Inclusive · Thanks for shopping!\n${dblLine}\n`;
        return text;
    }

    /** The printable slip body shared by the on-screen and printed receipts. */
    function receiptSlipBody(sale) {
        const barcodeSvg = generateBarcodeSvg(sale.receiptNo);
        return `
        <div class="slip">
          <div class="text-center mb-2">
            <div class="fs-5 fw-bold">MartFlow Supershop</div>
            <div class="small">Retail Management & POS Suite</div>
            <div class="fw-bold mt-1">Receipt #${UI.esc(sale.receiptNo)}</div>
            <div class="small">${sale.at.replace("T", " ").slice(0, 19)} · Cashier: <b>${UI.esc(sale.cashier)}</b></div>
          </div>
          <hr>
          <div class="d-flex justify-content-between small mb-1">
            <span>ITEM / QTY</span>
            <span>TOTAL</span>
          </div>
          ${sale.lines.map((l) => `
            <div class="d-flex justify-content-between my-1">
              <span>${UI.esc(l.name)} × ${UI.qty(l.quantity)}</span>
              <b>${UI.money(l.net)}</b>
            </div>`).join("")}
          <hr>
          ${sale.totals.discount > 0 ? `<div class="d-flex justify-content-between my-1"><span>Promotions / Discount</span><span>-${UI.money(sale.totals.discount)}</span></div>` : ""}
          ${sale.totals.coupon > 0 ? `<div class="d-flex justify-content-between my-1"><span>Coupon Savings</span><span>-${UI.money(sale.totals.coupon)}</span></div>` : ""}
          ${sale.totals.roundOff != 0 ? `<div class="d-flex justify-content-between my-1"><span>Round off</span><span>${UI.money(sale.totals.roundOff)}</span></div>` : ""}
          <div class="d-flex justify-content-between fs-5 my-1">
            <span>NET PAYABLE</span>
            <span>${UI.money(sale.totals.net)}</span>
          </div>
          <div class="d-flex justify-content-between small my-1">
            <span>Including VAT</span>
            <span>${UI.money(sale.totals.vat)}</span>
          </div>
          <hr>
          ${sale.tenders.map((t) => `
            <div class="d-flex justify-content-between my-1">
              <span>Paid via ${UI.esc(t.type)}${t.transactionId ? " · " + UI.esc(t.transactionId) : ""}</span>
              <span>${UI.money(t.amount)}</span>
            </div>`).join("")}
          <div class="d-flex justify-content-between fw-bold my-1">
            <span>Change Returned</span>
            <span>${UI.money(sale.totals.change)}</span>
          </div>
          <hr>
          <div class="text-center my-2">
            ${barcodeSvg}
            <div class="small mt-1">${UI.esc(sale.receiptNo)}</div>
          </div>
          <div class="text-center mt-2 small">All prices are VAT inclusive. Thank you for shopping with us!</div>
        </div>`;
    }

    /** Prints a self-contained 320px thermal-style slip through a hidden iframe. */
    function printSlip(title, slipInnerHtml) {
        const iframe = document.createElement("iframe");
        iframe.style.position = "fixed";
        iframe.style.right = "0";
        iframe.style.bottom = "0";
        iframe.style.width = "0";
        iframe.style.height = "0";
        iframe.style.border = "0";
        iframe.style.opacity = "0";
        document.body.appendChild(iframe);

        const doc = iframe.contentWindow.document;
        doc.open();
        doc.write(`
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8">
              <title>${UI.esc(title)}</title>
              <style>
                @page {
                  margin: 0;
                  size: auto;
                }
                * {
                  box-sizing: border-box;
                  margin: 0;
                  padding: 0;
                }
                body {
                  font-family: 'Cascadia Mono', 'Courier New', Courier, monospace;
                  font-size: 12px;
                  line-height: 1.4;
                  color: #000000;
                  background: #ffffff;
                  padding: 12px 14px;
                  display: flex;
                  justify-content: center;
                }
                .slip {
                  width: 100%;
                  max-width: 320px;
                  padding: 12px 14px;
                  border: 1px dashed #000000;
                  background: #ffffff;
                }
                .text-center { text-align: center; }
                .d-flex { display: flex; }
                .justify-content-between { justify-content: space-between; }
                .fw-bold, b, strong { font-weight: 700; }
                .small { font-size: 11px; }
                .my-1 { margin-top: 3px; margin-bottom: 3px; }
                .mb-1 { margin-bottom: 4px; }
                .mb-2 { margin-bottom: 8px; }
                .mt-1 { margin-top: 4px; }
                .mt-2 { margin-top: 8px; }
                .fs-5 { font-size: 14px; font-weight: 700; }
                .fs-6 { font-size: 13px; font-weight: 700; }
                hr {
                  border: none;
                  border-top: 1px dashed #000000;
                  margin: 6px 0;
                }
                svg {
                  color: #000000;
                }
              </style>
            </head>
            <body>
              ${slipInnerHtml}
            </body>
            </html>
        `);
        doc.close();

        setTimeout(() => {
            iframe.contentWindow.focus();
            iframe.contentWindow.print();
            setTimeout(() => {
                if (iframe && iframe.parentNode) iframe.parentNode.removeChild(iframe);
            }, 4000);
        }, 200);
    }

    function printReceiptSlip(sale) {
        printSlip(`Receipt — ${sale.receiptNo}`, receiptSlipBody(sale));
    }

    /**
     * Shows the receipt modal. opts.onNext adds the "Next customer (Enter ↵)" flow;
     * opts.actions injects extra footer buttons (Sales Explorer's Void).
     */
    function show(sale, opts) {
        opts = opts || {};
        const barcodeSvg = generateBarcodeSvg(sale.receiptNo);
        const inst = UI.modal(`
            <div class="d-flex align-items-center justify-content-between w-100 me-3">
              <span><i class="bi bi-receipt"></i> Receipt ${UI.esc(sale.receiptNo)}</span>
              ${UI.saleBadge(sale.status)}
            </div>`,
            `<div class="mf-receipt-container">
               <div class="mf-receipt-actions mf-receipt-interactive-only mb-3 d-flex gap-2 justify-content-center flex-wrap">
                 <button class="btn btn-sm mf-btn-primary" id="btnReceiptPrint"><i class="bi bi-printer"></i> Print Slip</button>
                 <button class="btn btn-sm mf-btn-ghost" id="btnReceiptCopy"><i class="bi bi-clipboard"></i> Copy Text</button>
                 <button class="btn btn-sm mf-btn-ghost" id="btnReceiptDownload"><i class="bi bi-download"></i> Save TXT</button>
                 <button class="btn btn-sm mf-btn-ghost" id="btnReceiptTaxToggle" type="button" data-bs-toggle="collapse" data-bs-target="#taxBreakdownCollapse">
                   <i class="bi bi-percent"></i> VAT Details
                 </button>
               </div>
               <div class="mf-receipt" id="printableReceipt">
                 <div class="mf-receipt-header text-center mb-2">
                   <div class="mf-logo mf-receipt-interactive-only mb-2"><i class="bi bi-shop"></i></div>
                   <div class="mf-receipt-store-title fw-bold fs-5">MartFlow Supershop</div>
                   <div class="small text-secondary">Retail Management & POS Suite</div>
                   <div class="fw-bold mt-1">Receipt #${UI.esc(sale.receiptNo)}</div>
                   <div class="text-secondary small">${sale.at.replace("T", " ").slice(0, 19)} · Cashier: <b>${UI.esc(sale.cashier)}</b></div>
                 </div>
                 <hr>
                 <div class="mf-receipt-lines">
                   <div class="d-flex justify-content-between small text-secondary mb-1">
                     <span>ITEM / QTY</span>
                     <span>TOTAL</span>
                   </div>
                   ${sale.lines.map((l) => `
                     <div class="d-flex justify-content-between my-1 mf-receipt-item-row">
                       <span class="mf-receipt-item-name">${UI.esc(l.name)} <span class="text-secondary">× ${UI.qty(l.quantity)}</span></span>
                       <b class="mf-receipt-item-price">${UI.money(l.net)}</b>
                     </div>`).join("")}
                 </div>
                 <hr>
                 <div class="mf-receipt-totals">
                   ${sale.totals.discount > 0 ? `<div class="d-flex justify-content-between text-success my-1"><span>Promotions / Discount</span><span>-${UI.money(sale.totals.discount)}</span></div>` : ""}
                   ${sale.totals.coupon > 0 ? `<div class="d-flex justify-content-between text-success my-1"><span>Coupon Savings</span><span>-${UI.money(sale.totals.coupon)}</span></div>` : ""}
                   ${sale.totals.roundOff != 0 ? `<div class="d-flex justify-content-between my-1"><span>Round off</span><span>${UI.money(sale.totals.roundOff)}</span></div>` : ""}
                   <div class="d-flex justify-content-between fs-5 fw-bold my-1 mf-receipt-net-row">
                     <span>NET PAYABLE</span>
                     <span>${UI.money(sale.totals.net)}</span>
                   </div>
                   <div class="d-flex justify-content-between text-secondary small my-1">
                     <span>Including VAT</span>
                     <span>${UI.money(sale.totals.vat)}</span>
                   </div>
                 </div>

                 <!-- Collapsible VAT Breakdown -->
                 <div class="collapse mf-receipt-interactive-only mt-2" id="taxBreakdownCollapse">
                   <div class="p-2 rounded bg-black bg-opacity-10 border border-secondary border-opacity-25 small">
                     <div class="fw-bold mb-1"><i class="bi bi-info-circle"></i> NBR VAT Breakdown:</div>
                     <div class="d-flex justify-content-between"><span>Total Taxable Amount:</span><span>${UI.money(Number(sale.totals.net) - Number(sale.totals.vat))}</span></div>
                     <div class="d-flex justify-content-between"><span>Total VAT (Output):</span><span>${UI.money(sale.totals.vat)}</span></div>
                   </div>
                 </div>

                 <hr>
                 <div class="mf-receipt-tenders">
                   ${sale.tenders.map((t) => `
                     <div class="d-flex justify-content-between my-1">
                       <span>Paid via ${UI.esc(t.type)}${t.transactionId ? " · " + UI.esc(t.transactionId) : ""}</span>
                       <span>${UI.money(t.amount)}</span>
                     </div>`).join("")}
                   <div class="d-flex justify-content-between fw-bold my-1">
                     <span>Change Returned</span>
                     <span>${UI.money(sale.totals.change)}</span>
                   </div>
                 </div>
                 <hr>
                 <div class="text-center my-2">
                   ${barcodeSvg}
                   <div class="mf-receipt-barcode-text small text-secondary">${UI.esc(sale.receiptNo)}</div>
                 </div>
                 <div class="text-center mt-2 small text-secondary">All prices are VAT inclusive. Thank you for shopping with us!</div>
               </div>
             </div>`,
            `<button class="btn mf-btn-ghost mf-receipt-interactive-only" id="btnReceiptPrintBottom"><i class="bi bi-printer"></i> Print</button>
             ${opts.actions || ""}
             ${opts.onNext ? `<button class="btn mf-btn-primary" data-bs-dismiss="modal" id="btnNextCustomer">Next customer <span class="badge bg-black bg-opacity-25 ms-1">Enter ↵</span></button>` : ""}`);

        const plainText = formatPlainTextReceipt(sale);

        const printBtn = UI.$("#btnReceiptPrint");
        if (printBtn) printBtn.addEventListener("click", () => printReceiptSlip(sale));

        const printBottomBtn = UI.$("#btnReceiptPrintBottom");
        if (printBottomBtn) printBottomBtn.addEventListener("click", () => printReceiptSlip(sale));

        const copyBtn = UI.$("#btnReceiptCopy");
        if (copyBtn) {
            copyBtn.addEventListener("click", async () => {
                try {
                    await navigator.clipboard.writeText(plainText);
                    UI.ok("Receipt copied to clipboard!");
                    copyBtn.innerHTML = `<i class="bi bi-check2"></i> Copied!`;
                    setTimeout(() => { copyBtn.innerHTML = `<i class="bi bi-clipboard"></i> Copy Text`; }, 2000);
                } catch (e) {
                    UI.fail("Could not copy to clipboard");
                }
            });
        }

        const dlBtn = UI.$("#btnReceiptDownload");
        if (dlBtn) {
            dlBtn.addEventListener("click", () => {
                const blob = new Blob([plainText], { type: "text/plain;charset=utf-8" });
                const a = document.createElement("a");
                a.href = URL.createObjectURL(blob);
                a.download = `receipt-${sale.receiptNo}.txt`;
                a.click();
                URL.revokeObjectURL(a.href);
                UI.ok("Receipt TXT downloaded");
            });
        }

        // Enter continues to the next customer (ignored while typing in fields or
        // focusing a button, so form submits and native clicks keep working).
        // Armed on "shown" — bootstrap silently drops hide() calls that land while the
        // show transition is still running, which would eat an impatient keypress.
        let onKey = null;
        const modalEl = UI.$("#modalHost .modal");
        if (opts.onNext && modalEl) {
            onKey = (e) => {
                if (e.key !== "Enter") return;
                const tag = ((e.target && e.target.tagName) || "").toLowerCase();
                if (tag === "input" || tag === "select" || tag === "textarea" || tag === "button") return;
                e.preventDefault();
                inst.hide();
                opts.onNext();
            };
            modalEl.addEventListener("shown.bs.modal", () =>
                document.addEventListener("keydown", onKey));
            modalEl.addEventListener("hidden.bs.modal", () =>
                document.removeEventListener("keydown", onKey));
            const nextBtn = UI.$("#btnNextCustomer");
            if (nextBtn) nextBtn.addEventListener("click", () => opts.onNext());
        }

        return { inst, plainText };
    }

    return { show, printSlip, printReceiptSlip, formatPlainTextReceipt, generateBarcodeSvg };
})();
