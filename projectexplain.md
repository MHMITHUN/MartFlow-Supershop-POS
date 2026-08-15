# MartFlow — সম্পূর্ণ প্রজেক্ট ব্যাখ্যা (A → Z)

> এটা পড়লে তুমি গোটা প্রজেক্টটা বুঝে যাবে — কোন স্ক্রিনে কী আছে, কোন বাটন কী করে,
> ভেতরে ভেতরে (backend-এ) কী হয়, আর ১৮টা design pattern কোথায় কাজ করে।
> Terminal/UI-র টেকনিক্যাল শব্দগুলো English-এই রেখেছি কারণ কোডে সেই নামগুলোই আছে —
> মিলিয়ে দেখতে সহজ হবে।

---

## 1. প্রজেক্টটা আসলে কী? (এক প্যারাগ্রাফে)

**MartFlow** একটা **Supershop Retail Management Suite** — মানে বাংলাদেশি সুপারশপ
(Shwapno / Meena Bazar / Unimart ক্লাসের দোকান)-এর **গোটা business** চালানোর সফটওয়্যার।
একটা সুপারশপের ডেইলি কাজগুলো: **বিক্রি (POS)**, **স্টক (Inventory)**, **মাল কেনা
(Purchasing)**, **loyalty points**, **return/refund**, **রিপোর্ট (profit, VAT)** — সব এক
সিস্টেমে। আর যারা চালায় — সুপারশপের **মালিক (admin), manager, cashier** (technical
demo-র জন্য আলাদা একটা developer account) — প্রত্যেকে আলাদা আলাদা power নিয়ে এটা ব্যবহার করে।

**আগের প্রজেক্টটা কী ছিল?** একটা e-commerce demo — যেখানে কোনো login ছিল না, role
ছিল header থেকে (`?as=admin` লিখলেই admin হয়ে যেত!), cart-টা সবার জন্য common
ছিল, tax দেখাত কিন্তু চার্জ করত না, restart দিলে স্টক হারিয়ে যেত। এগুলো সব fix
করা হয়েছে — আর নিচে দেখাবে কোথায় কোথায়।

**কীভাবে চালাবে:**

```powershell
.\mvnw.cmd spring-boot:run
# browser e: http://localhost:8080
```

**Login (demo account):**

| Username | Password | কে? | কী কী পাবে? |
|---|---|---|---|
| `admin` | `admin123` | মালিক (Owner) | সবকিছু + Staff account manage |
| `manager` | `manager123` | Floor Manager | Catalog edit, Purchasing, Finance report, Void, Day Close, Activity Log |
| `cashier` | `cashier123` | Cashier (till operator) | Billing, Return, Inventory দেখা, operational report |
| `developer` | `developer123` | Developer | Cashier-এর স্ক্রিন + **Developer Mode** (pattern/API/diagnostics) — business power নেই |

> Login পেজে ৪টা **demo chip** বাটন আছে — ক্লিক করলে auto username/password বসে
> যায়ে সোজা login হয়ে যায়। উপরে **theme toggle** বাটন (চাঁদ/তাপুর আইকন) দিয়ে dark ↔ light
> মোড বদলানো যায়। Browser tab-এ MartFlow-র **favicon** (সবুজ দোকান) দেখা যায়।

**Login-এর পর কী হয় (backend-এ):** password-টা **PBKDF2 hash**-এর সাথে match হয়
(plaintext কোথাও save নেই)। ঠিক থাকলে একটা **random token** তৈরি হয় (৩২ byte), সেই
token browser-এর localStorage-এ থাকে, আর পরের সব request-এ `Authorization: Bearer
<token>` header হয়ে যায়। Server-এ **AuthFilter** সেই token check করে, কোন user সেটা
**RoleContext** (একটা ThreadLocal)-এ রাখে — এই জায়গা থেকেই সব role check হয়।
Token ১২ ঘণ্টা idle থাকলে expire হয়।

---

## 2. Navbar (উপরের বার) — কী কী আছে

Login-এর পর উপরে navbar দেখবে:

- **MartFlow** brand (ক্লিক করলে POS-এ যায়)
- **মেনুগুলো** — **role অনুযায়ী আলাদা আলাদা** দেখাবে:
  - Cashier দেখবে: POS · Inventory · Loyalty · Returns · Reports · Alerts
  - Manager-এর কাছে extra: **Dashboard · Purchases · Sales Explorer · Promotions · Activity Log · Day Close**
  - Admin-এর কাছে extra: **Staff**
  - Developer দেখবে: cashier-এর স্ক্রিনগুলো + **Developer Mode** — manager/admin/cashier
    Developer Mode কখনোই দেখবে না (server side-এ exact role check)
  - (মানে cashier কোনোভাবেই finance report বা purchasing দেখতে পারবে না — এটা server
    side-এ enforced, শুধু hide করা না!)
- **Bell icon 🔔** — unread alert count (লাল badge)। প্রতি ৮ সেকেন্ডে auto-refresh হয়।
- **User chip** — কোন user logged in + role
- **Log out** বাটন

---

## 3. POS — New Bill (বিক্রির মেইন স্ক্রিন)

এই স্ক্রিনটা **cashier-এর ডেইলি হাতুড়ি**। দুইটা ভাগ: বাম দিকে item খোঁজা, ডান দিকে bill।

### বাম দিকে

| Element | কী কাজ করে |
|---|---|
| **Scan box** (বড় input) | Barcode scanner এই box-এ **digit type করে Enter** চাপে — ৬+ digit হলে server-এ barcode lookup হয় (যেমন `8941234500011` = Teer Oil 5L), item bill-এ যোগ হয়ে যায়। Barcode না দিলে **item-এর নাম** লিখে Enter — match করলে add হয়। **F2** চাপলে এই box-এ focus আসে (আসল POS-এর মতো) |
| **Search dropdown** | নাম লিখলে নিচে ৮টা পর্যন্ত suggestion আসে — ক্লিক করলে add হয় |
| **Category chips** | All / Staples / Fresh / Grocery / Dairy / Beverages / Snacks / Toiletries / Personal Care / Household — filter করে quick grid দেখায় |
| **Quick-pick grid** | ২৪টা item tile — নাম, price, "per kg" label (weighed item-এর জন্য)। **এক ক্লিকেই bill-এ add** হয় (fast-moving item-এর জন্য) |

**Weighed item** (আলু, মাছ, পেঁয়াজ — যা kg-তে বিক্রি হয়) add করলে একটা **prompt আসে
ওজন লেখার জন্য** (যেমন `1.25`)। Bill-এ `Potato 1.25kg` হিসেবে আসে, দাম =
1.25 × per-kg price।

### ডান দিকে — Current Bill

| Element | কী কাজ করে |
|---|---|
| **Bill-এর line গুলো** | প্রতিটা line-এ: item-এর নাম, quantity/ওজন, দাম, "VAT 15% incl." বা "VAT-free" নোট, আর promotion সেভ করলে `saved BDT X` (সবুজ) |
| **– / + বাটন** | Quantity কম/বেশি (weighed item-এ 0.25 ধাপে) |
| **✏️ pencil** | Weighed line-এর ওজন সরাসরি বদলানো যায় |
| **🗑️ delete** | Line মুছে দেয় |
| **Undo (n)** | **Memento pattern!** শেষ কাজটা undo করে — mis-scan হলে এক ক্লিকে ঠিক। ১০ পর্যন্ত undo রাখে |
| **✕ clear** | পুরো bill মুছে ফেলা |
| **Attach বাটন** | Loyalty customer লাগায় (list থেকে বাছো) — লাগানোমাত্র **member price (5% off)** নিতে শুরু করে, আর points track হয় |
| **Coupon বাটন** | Coupon code দাও (যেমন `SAVE50` = ৫০ টাকা off, `WELCOME10` = 10% off) — ভুল code দিলে tender-এর সময় error দেবে |
| **Bags/Delivery** | কত bag (প্রতিটা ৫ টাকা) + delivery charge সেট করা |
| **Totals** | Gross → Promotions (−) → Coupon (−) → fees (+) → **PAYABLE** (বড় সবুজ) + "incl. VAT" |
| **Take payment বাটন** | Payment modal খোলে |

### Take Payment modal

- প্রতিটা row-তে: **payment type** (CASH / CARD / BKASH / NAGAD / POINTS) + **amount**
- **Split payment** — একাধিক method-এ ভাগ করে দেওয়া যায় (যেমন ৫০০ cash + ৩০০ bKash)
- নিচে live: **Tendered** (কত নিলাম) আর **Change** (কত ফেরত দিতে হবে) — লাল হলে কম নিলাম
- **POINTS type** দিলে loyalty points tender হিসেবে কাজ করে (১ point = ১ টাকা) — customer
  attached থাকা লাগবে
- **Complete sale** চাপলেই **backend-এ জাদুটা হয়** (নিচে সেকশন ৪-এ ডিটেইল)

### Receipt (bill-এর প্রিন্ট)

Sale complete হলে receipt modal আসে: receipt number (`MF-20260815-003` ফরম্যাট —
`MF-` + date + serial), cashier-এর নাম, সব line, discount/coupon, **Round Off** (cash
হলে পয়সা round করে whole টাকা), NET PAYABLE, VAT breakdown, payment গুলো +
transaction id, Change। **Print** বাটনে thermal-receipt style print হয়। **Next
customer** দিলে bill আগেই clear হয়ে আসে — আর **Enter ↵** চাপলেও একই কাজ (modal বন্ধ + scan box-এ focus)।

---

## 4. একটা sale-এর পূর্ণ জীবনচক্র (ভেতরের কাহিনী)

এটাই **সবচেয়ে গুরুত্বপূর্ণ সেকশন** — স্যার জিজ্ঞাসা করলে "sale-টা কীভাবে হয়?" এর জবাব:

```
Scan/Click
   │
   ▼
1. BillableItem snapshot তৈরি — item-এর নাম, SKU, price, VAT rate, COST
   এই মুহূর্তেই copy হয়ে যায় (পরে দাম বেড়ে গেলেও পুরনো receipt-এর দাম বদলাবে না)
   │
   ▼
2. Totals compute — PromotionEngine (Strategy pattern) প্রতিটা line-এ check করে:
   active Category Sale আছে? (যেমন Beverages 10%) → নাও
   না হলে customer attached + Member Price active? → 5% নাও
   না হলে Regular price
   + Coupon validate + carry bag/delivery fee যোগ
   │
   ▼
3. "Complete sale" চাপলে — VALIDATION CHAIN (Chain of Responsibility) ৬টা নিয়ম
   sequence-এ check করে: bill খালি? → ওজন ঠিক? → stock আছে? (একই item ২ line-এ
   হলে যোগ করে check!) → coupon valid? → POINTS হলে customer আছে? →
   tender ⊇ total?  → প্রথম ভুলটা পেলেই message দেয়, আর সামনে যায় না
   │
   ▼
4. COMMAND PIPELINE (Command pattern) — ৪ ধাপ, একটা fail হলে সব উল্টো যায়:
   ① ReserveStock — stock কমিয়ে DB-তে save
   ② ChargeTender — প্রতিটা payment channel-এ charge (Card decline করলে থাম!)
   ③ AwardPoints — customer-এ points add (১০০ টাকায় ১ point)
   ④ CreateSale — পুরো sale-টা snapshot হিসেবে DB-তে save
   ↳ কোনো একটা fail হলে: ধারা উল্টো করে rollback — stock ফেরত, points ফেরত,
     save হয় না। (Card declined = দোকানে কিছুই হয়নি!)
   │
   ▼
5. Receipt তৈরি + bill clear → পরের customer
```

**Cash rounding:** cash থাকলে payable whole টাকায় round হয় (`223.75 → 224`,
Round Off `+0.25` receipt-এ দেখায়)। Card/bKash-এ rounding হয় না।

**Receipt restart-এর পরেও একই:** sale-এর প্রতিটা line **self-contained snapshot** —
Mongo থেকে reload করেও exact একই receipt print হয় (দাম, VAT, decoration সব)।

---

## 5. Dashboard (manager/admin)

আজকের (Dhaka time অনুযায়ী) business-এর **health card** — ৬টা KPI tile:

| Tile | মানে কী |
|---|---|
| **Net Sales (today)** | আজকের মোট বিক্রি (VOIDED বাদে) + কতটা bill |
| **Average Basket** | গড়ে কোনো customer কত টাকার মাল নিল + total unit |
| **Output VAT (today)** | আজ NBR-এর জন্য যত VAT collected |
| **Cash in Drawer** | Cash tender হিসেবে কত টাকা নিলাম |
| **Low Stock Items** | Reorder level-এর নিচে/নিচে কত item (বিপদ!) |
| **Expiring ≤14 days** | কত item-এর batch ১৪ দিনের মধ্যে expire হবে |

নিচে: quick report link + **latest alerts feed**।

---

## 6. Inventory (স্টকের স্ক্রিন)

### Filter (উপরে)

- **View dropdown**: `All items` / `In stock` / `Low stock` / `Expiring ≤14d` — এগুলো
  **server-side Iterator** দিয়ে filter হয় (report আর live view একই iterator ব্যবহার
  করে, তাই কখনো mismatch হবে না)
- **Category dropdown**: category অনুযায়ী filter (VAT %-সহ দেখায়)
- **Search**: নাম / SKU / barcode দিয়ে

### Table-এর column গুলো

| Column | কী |
|---|---|
| Item | নাম + SKU + barcode |
| Type | `UNIT` (piece/pack) / `WEIGHED` (kg) / `COMBO` (hamper) badge |
| Category | Category + তার VAT rate |
| Cost | কত দামে কেনা হয়েছে (profit-এর ভিত্তি) |
| Price | MRP বা per-kg price |
| Stock | স্টক — **লাল + bold যদি low stock** হয় |
| Reorder | Reorder level (এর নিচে নামলেই alert) |
| Batches | Batch number → expiry date |
| Actions | নিচে দেখো |

### Manager-এর বাটনগুলো (cashier শুধু দেখে, চাপতে পারে না)

| Button | কী করে |
|---|---|
| **➕ Restock** | Manual stock আনা — qty + optional batch no + expiry। GRN ছাড়া তাড়াহুড়ায় stock দিলে এখানে |
| **➖ Adjust** | **Shrinkage** — DAMAGE / LOSS / THEFT / COUNT কারণসহ stock কমানো (বা negative quantity দিয়ে বাড়ানো)। এটা করলে একটা SHRINKAGE alert-ও ওঠে — wastage track হয় |
| **✏️ Edit** | নাম, cost, price, reorder level বদল — **price change করলে PRICE_CHANGE alert** ওঠে |
| **🗑️ Delete** | Catalog থেকে মুছে দেয় — **শুধু admin (মালিক) পারে** |
| **Add item** | নতুন item: type (UNIT/WEIGHED/COMBO), unit (PIECE/PACK/KG/LITRE), category, cost, MRP বা per-kg, stock, reorder, barcode, SKU |

> **Add item-এ Factory Method কাজ করে:** তুমি type বাছো, factory (UnitProductFactory /
> WeighedProductFactory) ঠিক অবজেক্ট বানিয়ে দেয — kg item-কে piece বানিয়ে দেওয়ার ভুল
> কখনোই হবে না।

---

## 7. Purchases (মাল কেনা — manager/admin)

৩টা tab:

### Tab 1: Purchase Orders

Supplier-এর কাছ থেকে মাল আনার পূর্ণ cycle। একটা PO-র **state machine**:

```
DRAFT → ORDERED → PARTIALLY_RECEIVED → RECEIVED → CLOSED
   │        │             │
   └────────┴─────────────┴────────→ CANCELLED (কারণ লাগবে)
```

| Button (state অনুযায়ী দেখায়) | কী করে |
|---|---|
| **New draft PO** | Supplier + item list (product + qty) → DRAFT তৈরি |
| **Submit to supplier** | DRAFT → ORDERED (order-টা supplier-কে দেওয়া হলো) |
| **Receive goods (GRN)** | **সবচেয়ে গুরুত্বপূর্ণ!** ট্রাক এলে প্রতিটা line-এ: কত এলো + batch no + expiry + unit cost লেখো → স্টক shelf-এ ওঠে (batch-সহ), item-এর cost update হয়, state auto: সব এলে `RECEIVED`, কিছু এলে `PARTIALLY_RECEIVED`। বেশি receive করলে reject করে |
| **Record payment** | Supplier-কে কত টাকা দিলাম (কিস্তিতে দেওয়া যায়) — payables কমতে কমতে শূন্য |
| **Close PO** | RECEIVED → CLOSED (cycle শেষ) |
| **Cancel** | কারণসহ cancel (মাল আসার আগে) |

**Payables** column = ordered total − কত pay করা। এগুলোই "supplier-এর কাছে কত
বাকি" — দোকানদারের জন্য life-saver নম্বর।

### Tab 2: Suppliers

Distributor-দের list (নাম, contact, phone, **payment terms** যেমন "Net 15" = ১৫ দিন
বাকি, address) + **Add supplier**।

### Tab 3: Standing Templates

**Prototype pattern-এর আসল ব্যবহার:** weekly restock list একবার template করে রাখো
(যেমন "Weekly Staples Restock": ১২ তেল + ৮ চাল + ১০ লবণ + ১৫ চিনি) — তারপর
**"Clone into draft"** বাটনে এক ক্লিকেই নতুন DRAFT PO তৈরি (প্রতিবার ১৫টা item
আবার আবার select করতে হবে না)। Clone-টা original থেকে **fully independent** —
একটা clone receive করলে অন্যটায় প্রভাব পড়ে না।

---

## 8. Loyalty (customers)

- সব member-এর list: নাম, phone (unique), card no, **points**, member since
- **Register**: নাম + phone + card no
- **Adjust points** (manager): balance ঠিক করা
- **Points-এর হিসাব:** প্রতিটা sale-এ `floor(net / 100)` point জমে (২২৪ টাকা → ২ point),
  আর POS-এ **POINTS tender** হিসেবে ১ point = ১ টাকা খরচ করা যায়। Void করলে
  point ফেরত চলে যায়।

---

## 9. Returns (return/exchange)

- **Receipt number** লিখে **Look up** (যেমন `MF-20260815-001`) → ওই bill-এর line গুলো
  চলে আসে
- প্রতিটা line-এর সামনে **return qty** + **reason** (যেমন "dented can") লেখো
- **Refund channel** বাছো (default: original যে channel-এ pay করেছিল সেটা)
- **Process refund** → **pro-rata refund** (২ টানার ৪ টানার ২টা ফেরত দিলে ৫০% টাকা ফেরত),
  মাল **স্টকে ফেরত** যায়, sale status → `PARTIALLY_RETURNED` বা সব ফেরত দিলে `RETURNED`
- একই receipt-এ একাধিকবার return করা যায় — কত ফেরত দেওয়া হলো সেটা track থাকে, বেশি ফেরত দিলে reject
- **Voided receipt-এ return অসম্ভব**
- **Exchange = return + নতুন bill** (return-টা process করো, তারপর POS-এ নতুন bill)
- Manager **Return history**-ও দেখে

**Void আর Return-এর পার্থক্য:** void = পুরো bill বাতিল (stock + points + payment সব
উল্টো যায়, শুধু manager, কারণ লাগে)। Return = কিছু item ফেরত (cashier পারে)।

---

## 10. Reports

উপরে report picker + **From/To date** + **Run** + **CSV** download বাটন।

| Report | কী দেয় | কে দেখে |
|---|---|---|
| **Daily Sales** | Date অনুযায়ী: bills, gross, discount, coupon, fees, net, VAT | সবাই |
| **Best Sellers** | সবচেয়ে বিক্রিত item (unit count অনুযায়ী, top 15) | সবাই |
| **Low Stock** | Reorder worksheet — কত stock আছে, কত order করতে হবে | সবাই |
| **Expiring Batches** | ১৪ দিনের ভেতরে expire হওয়া batch (EXPIRED bold) | সবাই |
| **Returns & Refunds** | Return log + refund + reason | manager+ |
| **Staff Performance** | Cashier অনুযায়ী: bills, units, turnover, avg basket | manager+ |
| **Profit** | প্রতিটা receipt-এর: revenue (VAT বাদে) vs COGS vs profit vs margin% | manager+ |
| **VAT Summary (NBR)** | VAT rate slab অনুযায়ী taxable + output VAT — **এটাই NBR filing-এর উৎস** | manager+ |

**CSV বাটন** যেকোনো report-কে Excel-এ খোলা file-এ নামায় — যেমন accountant-কে পাঠানো।

> **Template Method:** এই ৮টা report-এর fetch→aggregate→build ফ্লো এক জায়গায়
> (`AbstractReportGenerator`) — "VOIDED কখনো revenue না" নিয়ম একবার লেখা, সব জায়গায় কাজ করে।

---

## 11. Alerts (alert center)

Auto-generated সময় সংবেদনশীল খেয়াল রাখার feed — **কেউ manual বানায় না**:

| Alert | Color | কখন ওঠে |
|---|---|---|
| **LOW_STOCK** | লাল | Stock reorder level ছাড়িয়ে নামলে + **reorder suggestion** message-ও আসে |
| **EXPIRY_SOON** | হলুদ | Batch ১৪ দিনের ভেতরে expire হলে (boot-এ + GRN-তে check) |
| **SHRINKAGE** | লাল | Damage/loss/theft write-off করলে |
| **RESTOCK** | সবুজ | Stock বাড়লে |
| **PRICE_CHANGE** | সবুজ | MRP বদলালে |

"unread only" toggle, **Mark read** বাটন, refresh। Navbar-এর bell-এ unread count।

---

## 12. Staff (শুধু admin)

- সব staff account: username, নাম, role badge, active, since
- **Add staff**: username + নাম + role (CASHIER/MANAGER/ADMIN/**DEVELOPER**) + password (৬+ char)
- **Disable/Enable**: account ব্লক/আনব্লক (disabled হলে login করতে পারবে না)
- **Reset password**

---

## 13. Sales Explorer (manager/admin)

Manager-এর receipt খোঁজার স্ক্রিন — আগে void backend-এ ছিল কিন্তু UI ছিল না, এখন সব আছে:

- **Filter**: from/to date, status (COMPLETED/PARTIALLY_RETURNED/RETURNED/VOIDED), cashier
- **Table**: receipt no, date, cashier, status badge, net, VAT
- **👁 View** — receipt modal খোলে (print/copy/save সব কাজ করে — POS-এর receipt-এর
  একই component, reprint মানে ফ্রি)
- **🚫 Void** — reason লেখা **লাগবেই** (textarea), confirm করলে: stock ফেরত ওঠে, tender
  refund, points উল্টো, status VOIDED। Audit log-এও entry যায়
- Void করলে inventory-তে stock আবার বেড়ে যায় — Dashboard-এও net থেকে বাদ যায়

## 14. Promotions (manager/admin)

আগে promotion শুধু seed data থেকে আসত — এখন manager নিজেই বানাতে পারে:

- **Table**: নাম, type badge (CATEGORY_SALE/MEMBER_PRICE/COUPON_FLAT/COUPON_PERCENT),
  detail (category+%, coupon code+amount), date window, ACTIVE/OFF
- **New/Edit modal**: type select করলে form বদলে যায় (category sale → category+percent,
  coupon → code+amount/percent)। Coupon code auto-uppercase
- **Power বাটন** — on/off toggle (Strategy LIVE — POS-এ সাথে সাথে দাম বদলায়)
- **Delete** — confirm করে মুছে ফেলা
- **Coupon tester** (নিচের প্যানেল): code + bill amount দিলে engine কত টাকা off দেবে
  দেখায় — PromotionEngine (Strategy)-কে live টেস্ট!

## 15. Activity Log (manager/admin)

"কে void দিলাম? কে price change করলো?" — এই প্রশ্নের উত্তর। প্রতিটা বড় action-এর
**audit trail**:

- কী কী record হয়: login (+ ভুল password), logout, void, return, shrinkage, restock,
  product create/edit/delete (price old→new সহ), promotion CRUD, PO-র সব state
  change, template save/clone, points adjust, staff change, day close
- **Filter**: date window, actor, action type
- Badge color: auth = ধূসর, void/shrinkage = লাল, money = হলুদ, catalog = নীল,
  purchasing = সবুজাভ
- Cashier/developer দেখতে পারবে না (403) — শুধু manager+
- ৫০০ entry পর্যন্ত রাখে (alert feed-এর মতো cap) — এটা shift-level trail, forensic
  ledger না। Observer pattern ব্যবহার **করিনি** — কারণ stock event-এ actor/intent নেই;
  javadoc-এ কারণ লেখা আছে (স্যার জিজ্ঞাসা করলে সেটাই বলো)

## 16. Day Close / Z-Report (manager/admin)

শিফট শেষে cash drawer মেলানোর স্ক্রিন — আসল সুপারশপের ডেইলি রুটিন:

- **Window** (default আজ) load করলে: Net/VAT/Bills/Expected drawer cash KPI
- **Tenders table**: cash/card/bKash/Nagad/points কত আসে
- **Returns + Voids section**: হিসাবটা **চোখে দেখা যায়** (viva-তে সোনার চামচ)
- **Close the day**: drawer-এ যত টাকা count করে লেখো → variance (counted − expected)
  — ০ হলে সবুজ (balanced!), না হলে লাল
- **Print Z-slip** — thermal প্রিন্টারে প্রিন্ট হয় (receipt-এর একই printer component)
- **History** — আগে কোনো দিন close করলে table থেকে আবার print করা যায়

**Cash-এর হিসাব (স্যার জিজ্ঞাসা করলে):** `expected = cashIn − changeOut − cashRefunds − voidCashOut`
- cashIn/changeOut = window-এর সব sale (same-window void নিট শূন্য হয়)
- voidCashOut = যেসব void-এর refund এই window-এ পড়েছে (কালকের sale আজ void করলে
  আজকের drawer থেকে টাকা যায় — `voidedAt` field-এর জন্যই সম্ভব)
- Revenue figure-এ VOIDED রাখি না, কিন্তু cash হিসাবে রাখতে হয় — দুইটা আলাদা সেট, যাতে
  ঝামেলা না হয় (javadoc-এ আছে)

---

## 17. Developer Mode (শুধু developer login)

**developer / developer123** দিয়ে login করলেই দেখা যায় (অন্য কেউ না)। ৩টা tab:

1. **Patterns** — ১৮টা card; ক্লিক করলে deep-dive modal: Problem → কেন এই pattern →
   Alternative (কী reject করেছি কেন) → আসল class + file → **আসল source snippet** → কোন test
   প্রমাণ করে → "See it live" (manager screen হলে "manager login-এ দেখো" hint দেয়)
2. **API explorer** — সব endpoint grouped (method badge, path, min role, description)
   + **Try it** বাটন: param + JSON body দেওয়া যায়, নিজের token-এই call হয় — 403 এলে
   সেটা GATE কাজ করছে দেখানো হয় (এটাই feature!)
3. **Diagnostics** — persistence mode (MONGO/IN-MEMORY), active token, alert count,
   repo count, uptime, Dhaka clock

Backend থেকে data আসে (`/api/dev/*` — exact DEVELOPER role match, manager/admin-ও 403)।
**Drift guard test** আছে: card-এ যেই class/test-এর নাম আছে, source tree-তে তা না থাকলে
build FAIL — "ReceiptBuilder"-টাইপের ভূত আর হবে না। API catalog-ও Spring-এর আসল
routing-এর সাথে match করে।

---

## 18. ১৮টা Design Pattern — এক জায়গায়, বাংলায়

| # | Pattern | কোথায় (class) | বাংলায় কেন দরকার ছিল |
|---|---|---|---|
| 1 | **Singleton** | `InventoryCatalog`, `DatabaseConnection` | পুরো app-এ stock list-এর একটাই copy থাকতে হবে — দুইটা থাকলে এক মেশিনে বিক্রি, অন্যটায় stock পুরনো = একই মাল ২ বার বিক্রি! |
| 2 | **Factory Method** | `ProductFactory` → `UnitProductFactory`, `WeighedProductFactory` | "Add item" form-এ তুমি শুধু type বাছো — factory ঠিক class বানিয়ে দেয়। না থাকলে if-else ভাবতে হতো piece না kg |
| 3 | **Builder** | `PurchaseOrderBuilder` | PO-তে অনেক field — step by step বানিয়ে শেষে validate করে। আধা-বানানো PO সিস্টেমে ঢুকতে পারবে না |
| 4 | **Prototype** | `StandingOrderTemplate` | Weekly restock list clone করে নতুন DRAFT — ১৫টা item আবার আবার select করা লাগবে না। (আগের প্রজেক্টে এটা DEAD CODE ছিল!) |
| 5 | **Adapter** | `CashAdapter`, `CardAdapter`, `BkashAdapter`, `NagadAdapter`, `PointsAdapter` | ৫ ধরনের payment-এর SDK আলাদা আলাদা shape — adapter সবগুলোকে এক `PaymentChannel` interface-এ আনে। নতুন payment add করলে bill-এর code change লাগে না |
| 6 | **Decorator** | `LineDiscount`, `CarryBagFee`, `DeliveryFee`, `RoundOffAdjustment` | Bill line-এর উপর discount/charge জোড়া যায় — line class গুলো change করা লাগে না, যত খুশি stack করো |
| 7 | **Facade** | `MartFlowFacade`, `BillingFacade` | Controller/frontend-কে একটা সিম্পল method লাগে (`tender()`) — ভেতরের ১০টা subsystem এরা handle করে |
| 8 | **Proxy** | `RoleGuardProxy` (product repo-র সামনে) | Cashier কোনোভাবেই product create/delete করতে পারবে না — UI-তে না, controller-এ bug থাকলেও না, data layer-এই ব্লক। Security-র last line |
| 9 | **Composite** | `ComboProduct` | Eid hamper = এক item, কিন্তু stock তার component-দের থেকে আসে — ১ hamper বিক্রি হলে প্রতিটা component-এর stock কমে |
| 10 | **Observer** | `AlertService`, `ReorderSuggestionObserver`, `ExpiryWatcher` | Stock কমলে/expiry হলে সিস্টেম নিজেই alert তোলে — কাউকে মনিটর করতে হয় না |
| 11 | **Strategy** | `RegularPrice`, `CategorySale`, `MemberPrice` + `PromotionEngine` | Manager promotion on/off করে — till-এর pricing CODE change হয় না। Dynamic pricing |
| 12 | **Command** | `billing/commands/` (Reserve, Charge, AwardPoints, CreateSale) + void/return | Tender একটা ছোট transaction: card decline করলে stock+points+sale সব auto rollback। এক ধাপও আধা থাকে না |
| 13 | **Template Method** | `AbstractReportGenerator` + ৮ report | নতুন report = ৩টা ছোট method। "Voided revenue না" নিয়ম একবার লেখা — ৮ জায়গায় copy-paste না |
| 14 | **Chain of Responsibility** | `billing/validation/` (৬ handler) | Cashier ঠিক প্রথম ভুলটা দেখে — "stock নেই, Coke-এর ১০ লাগবে না" টাইপের specific message |
| 15 | **State** | `suppliers/postate/` (৬টা state class) | DRAFT PO কোনোভাবেই receive করা যায় না — নিয়ম state class-এর ভেতরে, if-else চলে না। CLOSED-কে cancel করা যায় না |
| 16 | **Visitor** | `VatVisitor`, `ProfitVisitor`, `ReceiptFormatterVisitor` | একই bill line থেকে VAT report + profit report + receipt format — ৩টা ভিন্ন কাজ, এক traversal। Mongo থেকে load করা sale-এও একই হিসাব |
| 17 | **Memento** | `BillMemento` (session-এর undo stack) | Cashier-এর Undo বাটন — mis-scan POS-এ প্রতি মিনিটে হয়। ১০ ধাপ পর্যন্ত ফেরা যায় |
| 18 | **Iterator** | `InStockIterator`, `LowStockIterator`, `ExpiringSoonIterator`, `PriceRangeIterator` | "Low stock" view আর "Low stock" report একই iterator চালায় — কখনো mismatch হবে না। Reset করে আবার চালানো যায় |

**একটা intelligent design decision (স্যার জিজ্ঞাসা করলে বলো):** sale-এর status plain
enum (`COMPLETED/VOIDED/PARTIALLY_RETURNED/RETURNED`) কিন্তু PO-তে পুরো State pattern
**কেন?** কারণ sale-এর কোনো status-এ বসে থাকে না — void/return একটা instant operation,
আলাদা command-এ হয়। কিন্তু PO দিনের পর দিন একটা state-এ **বসে থাকে**, আর প্রতিটা state-এ
allowed operation আলাদা (draft-এ receive যায় না, closed-এ cancel যায় না)। Pattern
business-এর উপর base করা, syllabus-এর উপর না।

---

## 19. Architecture — একটা request কীভাবে পৌঁছায়

```
Browser (vanilla JS SPA)
   │  fetch + Bearer token
   ▼
AuthFilter ──── token check → RoleContext (ThreadLocal) set → finally-এ clear
   │
   ▼
Controller (খুবই পাতলা — ১ লাইনে facade call)
   │
   ▼
Facade (MartFlowFacade / BillingFacade / PurchasingService)
   │
   ▼
Domain POJOs ──── items, decorators, validation chain, commands,
   │              promotion engine, state machine, visitors...
   ▼
RoleGuardProxy (product repo-র write gate: create=manager+, delete=admin)
   │
   ▼
Repository interface ──── Mongo (Atlas) থাকলে → MongoDB
                          না থাকলে → in-memory (demo mode)
```

**৩টা iron rule:**

1. **Domain-এ ১০০% plain Java** — কোনো Spring annotation নেই। Spring শুধু `AppConfig`
   (DI wiring) + controller-এ। তাই pattern গুলো "আসল" — framework-এর জাদু না।
2. **টাকা সব জায়গায় `BigDecimal`** — ২ decimal, HALF_UP rounding, একটা `MoneyUtil`
   choke point। `double` floating point-এ 0.1+0.2 = 0.3000000004 হয় — টাকায় এটা suicide।
3. **সময় সব জায়গায় `TimeSource`** — Asia/Dhaka fixed। "আজ" = Dhaka-র আজ, server
   যেকোনো দেশে থাকুক না কেন।

**VAT-এর হিসাব (গুরুত্বপূর্ণ):** BD-তে price **VAT-inclusive** (shelf-এ ১১৫ টাকা = ১০০ মাল +
১৫ VAT)। তাই VAT back-calculate হয়: `VAT = net × rate / (100 + rate)`।
Check: 115 @ 15% → 115×15/115 = **15.00** ✓ ; 107.50 @ 7.5% → **7.50** ✓।
Rate: Staples/Fresh 0%, Grocery/Dairy 7.5%, বাকি 15% — `CategoryRegistry`-তে policy
হিসেবে আছে, এক লাইন change করে সব জায়গায় update হয়।

---

## 20. Data model (কে কে আছে, কার সাথে কার সম্পর্ক)

```
Product (abstract)
 ├── UnitProduct    (mrp, piece/pack stock)
 ├── WeighedProduct (pricePerUnit, fractional kg stock)
 └── ComboProduct   (components[] → Composite, scarcest stock)

User (username, passwordHash, role)          ← ৩টা role + developer
Customer (phone unique, pointsBalance)       ← loyalty
Sale ── SaleLine[] (FULL snapshot)           ← বিক্রির history
 │     └─ Tender[] (type, amount, trxId)
 └── SaleReturn[] (প্রতি line-এ qty, reason)
Promotion (CATEGORY_SALE / MEMBER_PRICE / COUPON_FLAT / COUPON_PERCENT, date window)
Supplier ── PurchaseOrder ── PurchaseOrderLine[] (orderedQty, receivedQty, unitCost)
              │                + Payment[]
              └── StandingOrderTemplate (Prototype)
Alert (ephemeral feed, 200 cap)
```

**সবকিছু (products, sales, returns, customers, promotions, PO, suppliers, users)
MongoDB-তে persist হয়** — restart দিলেও থাকে। শুধু login token আর alert feed
in-memory (restart-এ আবার login, কিন্তু data হারাবে না)।

---

## 21. Folder structure map

```
src/main/java/com/martflow/
├── MartFlowApplication.java    ← entry point
├── api/                        ← REST controller গুলো (পাতলা) + AuthFilter + DTO
├── app/                        ← AppConfig (DI wiring) + MartFlowFacade + SeedData
├── audit/                      ← AuditLog, AuditService (activity trail, 500 cap)
├── auth/                       ← User, PasswordHasher, TokenStore, AuthService
├── billing/                    ← Bill, BillMemento, BillingSession(+Registry),
│   ├── item/                     UnitLine/WeighedLine/ComboLine/AdjustmentLine
│   ├── decorator/                LineDiscount/CarryBagFee/DeliveryFee/RoundOff
│   ├── validation/               ৬টা handler + chain
│   ├── commands/                 Billing pipeline
│   └── visitor/                  BillItemVisitor interface
├── catalog/                    ← Product hierarchy, Factory, InventoryCatalog,
│   └── iter/                     ৪টা iterator + CategoryRegistry (VAT policy)
├── common/                     ← MoneyUtil, TimeSource, NotFoundException
├── dev/                        ← PatternCatalog + ApiCatalog (Developer Mode-র data,
│                                  test দিয়ে drift-guarded)
├── inventory/                  ← Observer event, AlertService, ExpiryWatcher,
│                                  InventoryService (stock chokepoint)
├── loyalty/                    ← Customer, LoyaltyService
├── payment/                    ← PaymentChannel + ৫টা adapter + vendor/ (fake SDK)
├── persistence/                ← Repository interface, Mongo/in-memory repo,
│   └── proxy/                    mapper গুলো, RoleGuardProxy
├── pricing/                    ← Strategy গুলো + Promotion + PromotionEngine
├── reports/                    ← AbstractReportGenerator + ৮টা report + CSV
│   ├── visitor/                  VatVisitor, ProfitVisitor, ReceiptFormatter
│   └── (DayClose + DayCloseService ← Z-report, এখানেই)
├── returns/                    ← ReturnService, SaleReturn
├── sales/                      ← Sale, SaleLine, ReceiptNoGenerator, SalesAdminService
├── security/                   ← Role, Caller, RoleContext (ThreadLocal), RoleGate
└── suppliers/                  ← Supplier, PO, Builder, PurchasingService,
    └── postate/                  State machine + StandingOrderTemplate (Prototype)

src/main/resources/static/      ← frontend (index.html, css/, js/ — views, router, api)
src/test/java/com/martflow/     ← ২৪টা test class → ১৬৭ test
```

---

## 22. স্যারের সামনে ১০ মিনিটের demo script

1. **Login** — cashier chip-এ click। "৪টা role, আসল password, PBKDF2 hash"
2. **POS** — barcode scan (box-এ `8941234500011` + Enter) → Teer oil bill-এ।
   আলুর tile click → weight prompt 1.25 → weighed billing দেখাও
3. **Attach customer** — 01711111111 (Nusrat) → 5% member discount LIVE নামতে দেখাও
4. **Coupon** — SAVE50 → আবার ৫০ টাকা off
5. **Take payment** — ২০০০ cash → Change দেখাও → receipt print → **Enter চাপো** —
   next customer (modal বন্ধ + scan box-এ focus)
6. **Undo দেখাও** — আরেকটা item scan করে Undo চাপো — Memento
7. Logout → **manager login** → **Dashboard** — আজকের sales, VAT, low stock
8. **Sales Explorer** — আজকের bill গুলো → একটা receipt View → reprint;
   একটা Void (reason সহ) → stock ফেরত ওঠে, Activity Log-এ entry
9. **Promotions** — নতুন "Eid Snacks 15%" বানাও → POS-এ ওই category-র item-এর দাম
   নামতে দেখাও → ফিরে এসে toggle off → দাম আবার আগের মতো
10. **Day Close** — mixed tender দিন বানাও → Day Close → preview-র হিসাব → drawer count
    → variance → Z-slip print
11. **Purchases** — seeded PO-টা খোলো → Receive (GRN) — batch+expiry সহ stock-এ উঠছে
    → Template tab-এ "Clone into draft" — Prototype
12. Logout → **developer login** → **Developer Mode** — Builder card খোলো (আসল snippet
    + test-এর নাম) → API Explorer-এ `GET /api/products` try (200) → `POST /api/products`
    try (**403 — gate-টা LIVE দেখাও**) → Diagnostics। Session শেষ 🔥

## 23. সম্ভাব্য প্রশ্ন + জবাব (FAQ)

**প্র: Payment gateway গুলো কি আসল?**
উ: Card/bKash/Nagad-এর SDK shape আসল (পয়সা conversion, response format) কিন্তু আসল
network call fake — course project-এ আসল merchant account লাগে। **Adapter layer-টা
১০০% আসল** — আসল SDK লাগিয়ে দিলে connector বদলে bill-এর code এক লাইনও change হবে না।
README-তে clearly বলা আছে।

**প্র: Data কি হারাবে?**
উ: না। MongoDB Atlas-এ সব persist। Test করা আছে: sale করে → app restart → receipt
byte-identical, stock/points/PO state সব থাকে।

**প্র: ২ জন cashier একসাথে bill করলে কী হবে?**
উ: প্রতিটা login token-এর নিজের bill session (আগের প্রজেক্টে ১টা global cart ছিল —
সবার বাস্কেট মিশে যেত!)। Stock movement `synchronized` InventoryService-এর ভেতরে
হয় — race হয় না।

**প্র: VAT ঠিক আছে?**
উ: Inclusive pricing, back-calc `net×rate/(100+rate)`, per-line round করে sum —
`VatVisitorTest`-এ exact সংখ্যা pin করা (115@15%→15.00)। VatSummary report-এ slab
অনুযায়ী total — NBR-এর মাসলাত দেওয়ার ready।

**প্র: Pattern গুলো কি আসল না dummy?**
উ: ১৬৭টা test প্রমাণ করে — pipeline rollback test (declined card), state matrix test,
proxy 403 test, hook-order test। আর ৩টা pattern-এর জন্য আগে dummy ছিল (Prototype dead
code, TaxVisitor test-only, Customer observer test-only) — প্রজেক্টে এখন business
feature বহন করে: weekly template, NBR VAT report, আসল customer points।

**প্র: Developer account-টা কী? কে এটা দেখবে?**
উ: `developer/developer123` — শুধু এই login-ই Developer Mode দেখে (pattern deep-dive +
API explorer + diagnostics)। Manager/admin/cashier-এর navbar-এ এটা নেই, URL-এ গেলেও
redirect। Developer till-side screen চালাতে পারে (live demo-র জন্য) কিন্তু manager
power নেই। Business ladder-এর পাশে বসে আছে — "উপরে" না।

**প্র: Z-report-এ cash-এর হিসাব কীভাবে?**
উ: `expected = cashIn − changeOut − cashRefunds − voidCashOut`। CashIn window-এর সব
sale-এর cash tender (void হয়ে গেলেও — টাকা তো drawer-এ ঢুকেছিল), change সব
drawer থেকে যায়, cash refund যায়, আর যেসব void-এর refund এই window-এ পড়েছে (কালকের
sale আজ void করলে আজকের drawer ক্ষতি হয় — `voidedAt`-এর জন্যই সম্ভব)। ৬টা hand-checked
test-এ pin করা।

**প্র: নতুন কোনো pattern add করেছ?**
উ: না — ১৮-তেই ১৮ থাকবে। Audit trail (Observer হতে পারত) আর Z-report (Template
Method হতে পারত) — দুটোতেই reject করেছি, আর কারণ javadoc-এ লেখা আছে। Feature
আগে, pattern পরে — এটাই এই প্রজেক্টের main policy।

---

## 24. পূর্ণ ACCESS MAP — কে কী পাবে, কেন আলাদা, কোন বাটনের কী কাজ

এই সেকশনটা **exam/viva-র ready reference** — প্রতিটা role, প্রতিটা option, কী কাজ,
কীভাবে কাজ করে, আর কেন আলাদা।

### 24.1 এক লাইনে philosophy

সুপারশপে **trust-এর ৩টা স্তর** + **১টা technical role**: cashier প্রতিদিন টাকা ছোঁয়,
manager দোকান চালায়, মালিক (admin) business-এর মারাত্মক decision নেয়, developer (শুধু
demo/viva-র জন্য) pattern দেখে। Role আলাদা না করলে: cashier দাম কমিয়ে দিতে পারত,
রমকম পরিষ্কার করে stock মুছে ফেলতে পারত, কেউ যেকোনো receipt void করে
টাকা লোপাতে পারত। তাই: **যে কাজে দোকানদারি/ক্ষতি করতে পারে, সেটা manager+;
যে কাজ পুরো business-কে affect করে, সেটা শুধু মালিকের।**

### 24.2 Master table — screen অনুযায়ী কে কী দেখে

| Screen (navbar) | Cashier | Developer | Manager | Admin | কেন এই role-এ |
|---|---|---|---|---|---|
| POS (বিক্রি) | ✔ | ✔ | ✔ | ✔ | বিক্রি সবাই করে — এখানে lock করলে দোকান চলবে না |
| Inventory (stock) | ✔ | ✔ | ✔ | ✔ | Stock দেখা সবার দরকার; **বাটনগুলো আলাদা** (নিচে দেখো) |
| Loyalty (customers) | ✔ | ✔ | ✔ | ✔ | Member attach কাজের; **points adjust manager+** |
| Returns | ✔ | ✔ | ✔ | ✔ | Return till-এ হয়; **history manager+** |
| Reports | ✔ (৪টা) | ✔ (৪টা) | ✔ (৮টা) | ✔ (৮টা) | Operational report সবার; **finance report manager+** |
| Alerts | ✔ | ✔ | ✔ | ✔ | Low stock/expiry সবাইকে দেখতে হবে |
| Dashboard | — | — | ✔ | ✔ | আজকের হিসেব = দোকান চালানোর কাজ, cashier-এর না |
| Purchases | — | — | ✔ | ✔ | মাল কেনা = টাকা গেছে, manager-এর decision |
| Sales Explorer | — | — | ✔ | ✔ | সব receipt দেখা + VOID = power, cashier-এর দরকার নেই |
| Promotions | — | — | ✔ | ✔ | দাম কমানো/বাড়ানো = marketing + margin-এর ব্যাপার |
| Activity Log | — | — | ✔ | ✔ | "কে কী করলো" = accountability, কেউ নিজের উপর judge হবে না |
| Day Close (Z) | — | — | ✔ | ✔ | Drawer reconciliation + টাকা safe-এ তোলা = manager-এর শেষ কাজ |
| Staff | — | — | — | ✔ | কে দোকানে ঢুকবে, কে pay পাবে — শুধু মালিকের ব্যাপার |
| Developer Mode | — | ✔ | — | — | Pattern/API/diagnostics — examiner বা dev-এর জিনিস, business না |

> **একটা screen-এ "✔" মানে সব বাটন না** — নিচের `24.5`-এ দেখো, একই screen-এ
> button-level আলাদা gate আছে (Inventory সবাই দেখে কিন্তু Delete শুধু admin)।

### 24.3 CASHIER-এর পূর্ণ option list (কী কাজ + কীভাবে কাজ করে)

| Option | কী কাজ | ভেতরে কীভাবে চলে |
|---|---|---|
| **POS — Scan box** | Barcode/নাম লিখে item add | ≥৬ digit = barcode lookup API, অন্য শব্দ = name search → `POST /api/bill/lines` → bill session-এ line |
| **POS — Category chips + tiles** | হাতে item select | সব product `GET /api/products?view=in_stock` থেকে আসে (Iterator pattern) |
| **+/– /✏️/🗑️ (line-এ)** | Qty বাড়ানো/কমানো (unit-এ ১ ধাপ, weighed-এ 0.25 kg), pencil = ওজন change, delete = line মুছে ফেলা | `PUT /api/bill/lines/{index}` — memento-তে আগের state save থাকে |
| **Undo (n)** | শেষ কাজ undo — mis-scan-এর ইলাজ | `POST /api/bill/undo` — Memento stack থেকে আগের snapshot-এ ফেরা |
| **Clear (✕)** | পুরো bill ফেলে দেওয়া | `DELETE /api/bill` |
| **Attach** | Loyalty member লাগানো → 5% member price auto | `PUT /api/bill/customer` → PromotionEngine-এর MemberPrice strategy চালু হয় |
| **Coupon** | Coupon code (SAVE50, WELCOME10) | `PUT /api/bill/coupon` → validate হয়, tender-এর সময় chain check করে |
| **Bags/Delivery** | ৫ টাকার bag + delivery charge | `PUT /api/bill/charges` → Decorator হিসেবে bill-এ জোড়া হয় |
| **Take payment** | Split tender (CASH/CARD/BKASH/NAGAD/POINTS) | `POST /api/bill/tender` → validation chain → ৪টা Command (Reserve→Charge→Points→CreateSale) — একটা fail হলে সব rollback |
| **Receipt (print/copy/save)** | Thermal print, text copy, TXT download | Iframe printer (320px slip); Reprint-ও একই জিনিস |
| **Enter ↵** | Next customer — modal বন্ধ + scan-এ focus | shown.bs.modal-এর পর armed, hidden-এ disarm |
| **Inventory দেখা** | Stock/batch/expiry দেখা | `GET /api/products?view=...` — server-side Iterator view |
| **Loyalty Register** | নতুন member add | `POST /api/customers` |
| **Returns process** | Receipt lookup → line ধরে qty+reason → refund | `POST /api/sales/{receiptNo}/returns` → pro-rata refund, stock ফেরত ওঠে |
| **Reports (৪টা)** | Daily sales, best sellers, low stock, expiry | `GET /api/reports/{key}` — Template Method + CSV download |
| **Alerts** | Feed দেখা, mark read | `GET /api/alerts`, `POST /api/alerts/{id}/read` |

**Cashier করতে পারবে না:** product create/edit/delete, restock, shrinkage, points
adjust, promotion change, PO/supplier, void, sales history, day close, staff, finance
report — বাটন নিজেই দেখাবে না, URL/api দিয়ে দিলেও **server 403** দেয়।

### 24.4 MANAGER যা বেশি পাবে (cashier-এর উপরে)

| Option | কী কাজ | ভেতরে কীভাবে চলে |
|---|---|---|
| **Dashboard** | আজকের ৬টা KPI (net, basket, VAT, cash, low stock, expiry) | `GET /api/reports/dashboard` — একই Iterator/repo থেকে |
| **Inventory — Add item** | নতুন item (Factory) বা COMBO (Composite, component picker) | `POST /api/products` → ProductFactory/ComboProduct → RoleGate MANAGER |
| **Inventory — Restock** | Stock বাড়ানো batch+expiry সহ | `POST /api/products/{id}/restock` → RESTOCK event → alert |
| **Inventory — Adjust (shrinkage)** | DAMAGE/LOSS/THEFT/COUNT লেখা | `POST /api/products/{id}/adjust` → SHRINKAGE event + audit |
| **Inventory — Edit** | নাম/cost/price/reorder change | `PUT /api/products/{id}` → PRICE_CHANGE event + audit (দাম old→new) |
| **Purchases (PO board)** | Draft→Submit→GRN receive→Pay→Close, cancel reason সহ | State pattern — ভুল transition server-ই ব্লক করে |
| **GRN receive** | মাল এলে batch+expiry+cost বসানো | Stock ওঠে + ExpiryWatcher check + audit PO_RECEIVED |
| **Suppliers tab** | Distributor add (terms, contact) | `POST /api/suppliers` |
| **Standing templates** | Save as template + Clone into draft | Prototype pattern — `POST /purchase-orders/templates` + `from-template` |
| **Sales Explorer** | Date/status/cashier filter, receipt view/reprint | `GET /api/sales?...` (manager gate) |
| **Void** | Receipt বাতিল — reason **লাগবেই** | `POST /api/sales/{receiptNo}/void` → Command pipeline উল্টো করে চলে: stock/tender/points সব reverse + audit |
| **Promotions** | Category sale/member price/coupon CRUD + toggle + tester | `POST/PUT/DELETE /api/promotions` — Strategy live, POS-এ সাথে সাথে দাম change |
| **Points adjust** | Member-এর points ঠিক করা | `POST /api/customers/{id}/points/adjust` + audit |
| **Activity Log** | কে কী করলো সব | `GET /api/audit` — 500 cap, newest first, filter সহ |
| **Day Close (Z)** | Drawer count → variance → Z-slip print → history | `GET preview` → `POST close` (server আবার নিজেই হিসাব করে) |
| **Returns history** | সব return-এর list | `GET /api/returns` |
| **Finance reports (৪টা)** | Returns, staff perf, profit, VAT | `GET /api/reports/{key}` — role map-এ MANAGER |

**Manager করতে পারবে না:** item **delete** (শুধু admin), staff account manage,
Developer Mode।

### 24.5 ADMIN (মালিক) যা বেশি পাবে (manager-এর উপরে)

| Option | কী কাজ | ভেতরে কীভাবে চলে |
|---|---|---|
| **Inventory — Delete 🗑️** | Catalog থেকে item বাতিল | `DELETE /api/products/{id}` — RoleGate ADMIN + **RoleGuardProxy আবার check করে** (double lock) |
| **Staff — Add staff** | নতুন account (CASHIER/MANAGER/ADMIN/DEVELOPER) | `POST /api/users` → PBKDF2 hash → audit USER_CREATED |
| **Staff — Disable/Enable** | Account ব্লক/আনব্লক | `PUT /api/users/{id}` — disabled মানে login-এ reject |
| **Staff — Reset password** | ভুল password-এর ইলাজ | একই PUT — audit-এ "password reset" লেখা থাকে |

মালিকও day close/promotions/void — সব manager-এর কাজ করতে পারে (ADMIN ≥ MANAGER
ladder-এ উপরে)। **মালিক Developer Mode দেখে না** — developer login লাগবে।

### 24.6 একই screen, আলাদা বাটন — এই জায়গাগুলো

| Screen | Cashier দেখে | Manager দেখে | Admin দেখে |
|---|---|---|---|
| Inventory | শুধু table | + Add/Restock/Adjust/Edit | + Delete |
| Loyalty | + Register | + Adjust points | + Adjust points |
| Returns | Process form | + History section | + History section |
| Reports | ৪টা বাটন | ৮টা বাটন | ৮টা বাটন |

এটা **UI-তে hide করা না** — server-এ প্রতিটা বাটনের নিজের gate (`RoleGate`),
frontend-এও route-level hard gate: cashier URL-এ `#/dashboard` লিখলে toast দেয়
"Your role cannot open that page" আর POS-এ ফেরত পাঠায়।

### 24.7 এই security কীভাবে কাজ করে (একটা বাটন ক্লিকের জীবন)

```
1. Login: username/password → PBKDF2 verify → 256-bit token (12h sliding)
2. প্রতিটা request: "Authorization: Bearer <token>" header-এ যায়
3. AuthFilter: token check → TokenStore থেকে Caller (কে? কোন role?)
   → ThreadLocal RoleContext-এ রাখা (finally-তে clear — thread race নেই)
4. Controller: RoleGate.requireAtLeast(MANAGER) / requireRole(DEVELOPER)
   → না পেলে 403 AccessDeniedException (JSON-এ clear message)
5. Product write-এর ক্ষেত্রে: RoleGuardProxy (repo-র সামনে) আবার check করে
   — controller-এ bug থাকলেও data layer-এ ব্লক
6. কাজ হলে: audit log-এ actor + action + detail জোড়া দেয়
```

**৩ স্তরের security:** (১) UI-তে menu hide — শুধু সুন্দর দেখানোর জন্য, (২) server-এর
RoleGate — আসল enforcement, (৩) RoleGuardProxy — product data-র last line of defense।
**Client কখনো role বলে না** — role আসে server-side token থেকে, তাই DevTools
থেকে change করলেও কাজ হবে না।

### 24.8 কেন এই separation (viva-র ১-মিনিটের জবাব)

- **টাকার ক্ষতি যেখানে** (void, shrinkage, price change, promotion, day close) →
  manager — কারণ duty shift শেষে manager drawer জমানোর জন্য accountable
- **Business-এর বড় decision** (staff, item delete) → মালিক — এই দুটো পিছায়ে ফেরা যায় না,
  তাই highest trust-এর মানুষ
- **বিক্রির গতি বজায় রাখতে** (POS, returns, stock দেখা) → সবাই — cashier-কে
  manager-দের wait করতে হবে না
- **Accountability** (Activity Log) → manager+ দেখে — কারণ "কে কী করলো"-র উত্তর
  মালিক/manager-কেই লাগবে
- **Examiner-দের জন্য আলাদা ঘর** (Developer Mode) → developer login — business
  staff-এর মাথা এটুকুই ভাঙা লাগবে না, আর developer আর business power মিশে যাবে না

---

*এই ডকুমেন্টটা `README.md`-এর বাংলা companion — README-তে English-এ formal details
(setup, API table, architecture diagram) আছে, এখানে business-level বোঝানো।*




