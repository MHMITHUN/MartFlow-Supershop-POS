# MartFlow — Complete Project Explanation (A→Z)

> Eta porar por tumi puro project ta bujhe jabe — kon screen e ki ache, kon button ki kore,
> vitore vitore (backend e) ki hoy, ar 18 ta design pattern kothay kaj kore.
> Terminal/UI er technical word gulo English e rekhechi karon code e sei name ache —
> match kore dekhte sohoj hobe.

---

## 1. Project ta asole ki? (ek paragraph e)

**MartFlow** ekta **Supershop Retail Management Suite** — mane Bangladeshi supershop
(Shwapno / Meena Bazar / Unimart class er dokan) er **puro business** chalano er software.
Ekta supershoper daily jinish gulo: **bikri (POS)**, **stock (Inventory)**, **maal kena
(Purchasing)**, **loyalty points**, **return/refund**, **report (profit, VAT)** — sob ek
system e. Customer jar — supershoper **malik (admin), manager, ar cashier** — 3 jon alada
alada power diye eita use kore.

**Porano project ta ki chilo?** Ekta e-commerce demo — jekhane kono login chilo na, role
chilo header theke (`?as=admin` likhle admin hoye jato!), cart ta sakoler jonno common
chilo, tax dekhato kintu charge korto na, restart dile stock haraye jeto. Egulo SOB fix
kora hoyese — ar niche dekhabe kothay kothay.

**Kivabe chalabe:**

```powershell
.\mvnw.cmd spring-boot:run
# browser e: http://localhost:8080
```

**Login (demo account):**

| Username | Password | Ke? | Ki ki pabe? |
|---|---|---|---|
| `admin` | `admin123` | Malik (Owner) | Sob kichu + Staff account manage |
| `manager` | `manager123` | Floor Manager | Catalog edit, Purchasing, Finance report, Void, Day Close, Activity Log |
| `cashier` | `cashier123` | Cashier (till operator) | Billing, Return, Inventory dekha, operational report |
| `developer` | `developer123` | Developer | Cashier er screen + **Developer Mode** (pattern/API/diagnostics) — business power NAI |

> Login page e 4 ta **demo chip** button ache — click korle auto username/password bore
> diye login hoye jay. Upore **theme toggle** button (chand/tapur icon) diye dark ↔ light
> mode change kora jay. Browser tab e MartFlow er **favicon** (sobuj dokaan) dekha jay.

**Login er por ki hoy (backend e):** password ta **PBKDF2 hash** er sathe match hoy
(plaintext kothao save nei). Thik thakle ekta **random token** toiri hoy (32 byte), sei
token browser er localStorage e thake, ar por er sob request e `Authorization: Bearer
<token>` header e jay. Server e **AuthFilter** sei token check kore, kon user seta
**RoleContext** (ekta ThreadLocal) e rakhre — ei jaygay thekei sob role check hoy.
Token 12 ghonta idle thakle expire.

---

## 2. Navbar (upor er bar) — ki ki ache

Login er por upore navbar dekhbe:

- **MartFlow** brand (click korle POS e jay)
- **Menu gulo** — **role onujayi alada alada** dekhabe:
  - Cashier dekhbe: POS · Inventory · Loyalty · Returns · Reports · Alerts
  - Manager er kache extra: **Dashboard · Purchases · Sales Explorer · Promotions · Activity Log · Day Close**
  - Admin er kache extra: **Staff**
  - Developer dekhbe: cashier er screen gulo + **Developer Mode** — manager/admin/cashier
    Developer Mode kokhono dekhbe na (server side e exact role check)
  - (Mane cashier konobhabe finance report ba purchasing dekhte parbe na — eta server
    side e enforced, sudhu hide kora na!)
- **Bell icon 🔔** — unread alert count (red badge). Prati 8 second e auto-refresh hoy.
- **User chip** — kon user logged in + role
- **Log out** button

---

## 3. POS — New Bill (bikri er main screen)

Ei screen ta **cashier er daily haturi**. Duita bhaag: bam dike item khonjha, dan dike bill.

### Bam dike

| Element | Ki kaj kore |
|---|---|
| **Scan box** (boro input) | Barcode scanner ei box e **digit type kore Enter** pressed kore — 6+ digit hole server e barcode lookup hoy (jemon `8941234500011` = Teer Oil 5L), item bill e add hoye jay. Bar na dile **item er naam** likhe Enter — match hole add. **F2** chaple ei box e focus ashe (real POS er moto) |
| **Search dropdown** | Naam likhle niche 8 ta porjonto suggestion ashe — click korle add hoy |
| **Category chips** | All / Staples / Fresh / Grocery / Dairy / Beverages / Snacks / Toiletries / Personal Care / Household — filter kore quick grid dekhay |
| **Quick-pick grid** | 24 ta item tile — naam, price, "per kg" label (weighed item er jonno). **Ek click e bill e add** hoy (fast-moving item er jonno) |

**Weighed item** (aalu, maach, peyaj — ja kg te bikri hoy) add korle ekta **prompt ashe
weight likhar jonno** (jemon `1.25`). Bill e `Potato 1.25kg` hishebe ashe, dam =
1.25 × per-kg price.

### Dan dike — Current Bill

| Element | Ki kaj kore |
|---|---|
| **Bill line gulo** | Prottek line e: item naam, quantity/weight, dam, "VAT 15% incl." ba "VAT-free" note, ar promotion save korle `saved BDT X` (sobuj) |
| **– / + button** | Quantity kom/beshi (weighed item e 0.25 step e) |
| **✏️ pencil** | Weighed line er weight directly change |
| **🗑️ delete** | Line muchhe dey |
| **Undo (n)** | **Memento pattern!** Last kaj ta undo kore — mis-scan hole ek click e thik. 10 porjonto undo rake |
| **✕ clear** | Puro bill clear |
| **Attach button** | Loyalty customer lagay (list theke choose koro) — lagatei **member price (5% off)** automatically e nite shuru kore, ar points track hoy |
| **Coupon button** | Coupon code dibe (jemon `SAVE50` = 50 taka off, `WELCOME10` = 10% off) — bhul code dile tender er somoy error dibe |
| **Bags/Delivery** | Koto bag (priti 5 taka) + delivery charge set kora |
| **Totals** | Gross → Promotions (−) → Coupon (−) → fees (+) → **PAYABLE** (boro sobuj) + "incl. VAT" |
| **Take payment button** | Payment modal khule |

### Take Payment modal

- Prottek row e: **payment type** (CASH / CARD / BKASH / NAGAD / POINTS) + **amount**
- **Split payment** — ekadhik method e vaag kore dite paro (jemon 500 cash + 300 bKash)
- Niche live: **Tendered** (koto nilam) ar **Change** (koto ferte hobe) — red hole kom nilam
- **POINTS type** dile loyalty points tender hishebe kaj kore (1 point = 1 taka) — customer
  attached thaka lagbe
- **Complete sale** chaplei **backend e magic ta hoy** ( niche section 4 e detail )

### Receipt (bill er print)

Sale complete hole receipt modal ashe: receipt number (`MF-20260815-003` format —
`MF-` + date + serial), cashier naam, sob line, discount/coupon, **Round Off** (cash
thakle poisha round kore whole taka), NET PAYABLE, VAT breakdown, payment gulo +
transaction id, Change. **Print** button e thermal-receipt style print hoy. **Next
customer** dile bill already clear hoye ase.

---

## 4. Ekta sale er purochondo lifecycle (vitorer kahini)

Eita **sobcheye important section** — sir jiggesa korle "sale ta kivabe hoy?" er jawab:

```
Scan/Click
   │
   ▼
1. BillableItem snapshot toiri — item er naam, SKU, price, VAT rate, COST
   ei moment ei copy hoy (pore dam bere geleo purano receipt er dam change hobe na)
   │
   ▼
2. Totals compute — PromotionEngine (Strategy pattern) prottek line e check kore:
   active Category Sale ache? (jemon Beverages 10%) → nao
   na hole customer attached + Member Price active? → 5% nao
   na hole Regular price
   + Coupon validate + carry bag/delivery fee jog
   │
   ▼
3. "Complete sale" chaple — VALIDATION CHAIN (Chain of Responsibility) 6 ta rule
   sequence e check: bill khali? → weight thik? → stock ache? (same item 2 line
   hole jog kore check!) → coupon valid? → POINTS hole customer ache? →
   tender ⊇ total?  → PROTHOM bhul ta paisi message dey, r shamne jay na
   │
   ▼
4. COMMAND PIPELINE (Command pattern) — 4 dhap, ekta fails hole SAB reverse:
   ① ReserveStock — stock komiye DB te save
   ② ChargeTender — prottek payment channel e charge (Card decline korle STOP!)
   ③ AwardPoints — customer e points add (100 taka e 1 point)
   ④ CreateSale — puro sale ta snapshot hishebe DB te save
   ↳ kono ekta fail hole: undhora ulta kore rollback — stock fire, points fire,
     sale save hoy na. (Card declined = dokan e kichu hoy nai!)
   │
   ▼
5. Receipt toiri + bill clear → next customer
```

**Cash rounding:** cash thakle payable whole taka te round hoy (`223.75 → 224`,
Round Off `+0.25` receipt e dekhay). Card/bKash e rounding hoy na.

**Receipt restart er por o same:** sale er prottek line **self-contained snapshot** —
Mongo theke reload kore o exact same receipt print hoy (dam, VAT, decoration sob).

---

## 5. Dashboard (manager/admin)

Aajker (Dhaka time onujayi) business er **health card** — 6 ta KPI tile:

| Tile | Meaning |
|---|---|
| **Net Sales (today)** | Aajker total bikri (VOIDED bad) + koto bill |
| **Average Basket** | Gromoto customer koto taka'ro mall nilam + total unit |
| **Output VAT (today)** | Aaj NBR er jonno joto VAT collected |
| **Cash in Drawer** | Cash tender hishebe koto taka nilam |
| **Low Stock Items** | Reorder level er niche/niche koto item (danger!) |
| **Expiring ≤14 days** | Koto item er batch 14 din er modhe expire hobe |

Niche: quick report links + **latest alerts feed**.

---

## 6. Inventory (stock er screen)

### Filters (upore)

- **View dropdown**: `All items` / `In stock` / `Low stock` / `Expiring ≤14d` — egulo
  **server-side Iterator** diye filter hoy (report ar live view same iterator use kore
  tai kokhono mismatch hobe na)
- **Category dropdown**: category onujayi filter (VAT % soho dekhay)
- **Search**: naam / SKU / barcode diye

### Table er column gulo

| Column | Ki |
|---|---|
| Item | Naam + SKU + barcode |
| Type | `UNIT` (piece/pack) / `WEIGHED` (kg) / `COMBO` (hamper) badge |
| Category | Category + tar VAT rate |
| Cost | Koto dam e kena hoyse (profit er base) |
| Price | MRP ba per-kg price |
| Stock | Stock — **red + bold jodi low stock** hoy |
| Reorder | Reorder level (ei niche namle alert) |
| Batches | Batch number → expiry date |
| Actions | Niche dekho |

### Manager er button gulo (cashier sudhu dekhe, chapte pare na)

| Button | Ki kore |
|---|---|
| **➕ Restock** | Manual stock ana — qty + optional batch no + expiry. GRN chara haste-haste stock dile ekhane |
| **➖ Adjust** | **Shrinkage** — DAMAGE / LOSS / THEFT / COUNT reason soho stock komano (ba negative quantity diye barano). Eta korle ekta SHRINKAGE alert o uthe — wastage track hoy |
| **✏️ Edit** | Naam, cost, price, reorder level change — **price change korle PRICE_CHANGE alert** fire hoy |
| **🗑️ Delete** | Catalog theke muchhe dey — **shudhu admin (malik) pare** |
| **Add item** | Notun item: type (UNIT/WEIGHED/COMBO), unit (PIECE/PACK/KG/LITRE), category, cost, MRP ba per-kg, stock, reorder, barcode, SKU |

> **Add item e Factory Method kaj kore:** tumi type bachao, factory (UnitProductFactory /
> WeighedProductFactory) thik object baniye dey — kg item ke piece baniye deyar bhul
> kokhono hobe na.

---

## 7. Purchases (maal kena — manager/admin)

3 ta tab:

### Tab 1: Purchase Orders

Supervisor theke maal anar puro cycle. Ekta PO er **state machine**:

```
DRAFT → ORDERED → PARTIALLY_RECEIVED → RECEIVED → CLOSED
   │        │             │
   └────────┴─────────────┴────────→ CANCELLED (reason lagbe)
```

| Button (state onujayi dekhay) | Ki kore |
|---|---|
| **New draft PO** | Supplier + item list (product + qty) → DRAFT toiri |
| **Submit to supplier** | DRAFT → ORDERED (order ta supplier k diyam) |
| **Receive goods (GRN)** | **Sobcheye important!** Truk ele per line e: koto elo + batch no + expiry + unit cost likho → stock shelf e uthre (batch soho), item er cost update hoy, state auto: sob ele `RECEIVED`, kichu ele `PARTIALLY_RECEIVED`. Beshi receive korle reject kore |
| **Record payment** | Supplier koto taka dilaam (instalments kore kore dite paro) — payables komte komte |
| **Close PO** | RECEIVED → CLOSED (cycle sesh) |
| **Cancel** | Reason soho cancel (maal asar age) |

**Payables** column = ordered total − koto pay kora. Ei gulo'i "supplier er kache koto
baki" — dokandar er jonno life-saver number.

### Tab 2: Suppliers

Distributor der list (naam, contact, phone, **payment terms** jemon "Net 15" = 15 din
baki, address) + **Add supplier**.

### Tab 3: Standing Templates

**Prototype pattern er asol use:** weekly restock list ekbar template kore rakho
(jemon "Weekly Staples Restock": 12 oil + 8 chaal + 10 lobon + 15 chini) — tarpor
**"Clone into draft"** button e ek click e notun DRAFT PO toiri (prottek bar 15 item
abar abar select korte hobe na). Clone ta original theke **fully independent** —
ekta clone receive korle onnota affect hoy na.

---

## 8. Loyalty (customers)

- Sob member der list: naam, phone (unique), card no, **points**, member since
- **Register**: naam + phone + card no
- **Adjust points** (manager): balance thik kora
- **Points er math:** prottek sale e `floor(net / 100)` point jome (224 taka → 2 point),
  ar POS e **POINTS tender** hishebe 1 point = 1 taka khoroch kora jay. Void korle
  point phire jay.

---

## 9. Returns (return/exchange)

- **Receipt number** likhe **Look up** (jemon `MF-20260815-001`) → oi bill er line gulo
  ashe
- Prottek line er shamne **return qty** + **reason** (jemon "dented can") likho
- **Refund channel** bacho (default: original je channel e pay korsilo sei ta)
- **Process refund** → **pro-rata refund** (2 tina 4 tina 2 ta felle 50% taka fire),
  mall **stock e fire**, sale status → `PARTIALLY_RETURNED` ba sob felle `RETURNED`
- Same receipt e multiple return kora jay — koto firlam ta track thake, beshi firla reject
- **Voided receipt e return impossible**
- **Exchange = return + notun bill** (return ta process koro, tarpor POS e notun bill)
- Manager **Return history** o dekhe

**Void vs Return difference:** void = puro bill cancel (stock + points + payment sob
reverse, manager only, reason lagbe). Return = kichu item fire (cashier pare).

---

## 10. Reports

Upre report picker + **From/To date** + **Run** + **CSV** download button.

| Report | Ki dey | Ke dekhe |
|---|---|---|
| **Daily Sales** | Date onujayi: bills, gross, discount, coupon, fees, net, VAT | sobai |
| **Best Sellers** | Sobcheye bikto item (unit count onujayi, top 15) | sobai |
| **Low Stock** | Reorder worksheet — koto stock ache, koto order korte hobe | sobai |
| **Expiring Batches** | 14 din er bhitore expire howa batch (EXPIRED bold) | sobai |
| **Returns & Refunds** | Return log + refund + reason | manager+ |
| **Staff Performance** | Cashier onujayi: bills, units, turnover, avg basket | manager+ |
| **Profit** | Prottek receipt er: revenue (VAT bad) vs COGS vs profit vs margin% | manager+ |
| **VAT Summary (NBR)** | VAT rate slab onujayi taxable + output VAT — **ei ta e NBR filing er source** | manager+ |

**CSV button** je kono report ke Excel-e khola file e namay — jemon accountant k pathano.

> **Template Method:** ei 8 ta report er fetch→aggregate→build flow EK jaygay
> (`AbstractReportGenerator`) — "VOIDED kokhono revenue na" rule ekbar likha, sob jaygay kaj kore.

---

## 11. Alerts (alert center)

Auto-generated somoy sonttok kheyal rakhar feed — **kewu manual banay na**:

| Alert | Color | Kokhon uthre |
|---|---|---|
| **LOW_STOCK** | red | Stock reorder level cross kore namle + **reorder suggestion** message o ashe |
| **EXPIRY_SOON** | yellow | Batch 14 din er bhitore expire hole (boot e + GRN te check) |
| **SHRINKAGE** | red | Damage/loss/theft write-off korle |
| **RESTOCK** | green | Stock barle |
| **PRICE_CHANGE** | green | MRP change korle |

"unread only" toggle, **Mark read** button, refresh. Navbar er bell e unread count.

---

## 12. Staff (admin only)

- Sob staff account: username, naam, role badge, active, since
- **Add staff**: username + naam + role (CASHIER/MANAGER/ADMIN/**DEVELOPER**) + password (6+ char)
- **Disable/Enable**: account block/unblock (disabled login korte parbe na)
- **Reset password**

---

## 13. Sales Explorer (manager/admin)

Manager er receipt khoja-r screen — age void backend e chilo UI chilo na, ekhon sob ase:

- **Filter**: from/to date, status (COMPLETED/PARTIALLY_RETURNED/RETURNED/VOIDED), cashier
- **Table**: receipt no, date, cashier, status badge, net, VAT
- **👁 View** — receipt modal khule (print/copy/save sob kaj kore — POS er receipt er
  same component, reprint mane FREE)
- **🚫 Void** — reason lekha LAGBE (textarea), confirm korle: stock fire uthe, tender
  refund, points reverse, status VOIDED. Audit log eo entry jay
- Void korle inventory te stock abar bere jay — Dashboard eo net theke baad jay

## 14. Promotions (manager/admin)

Age promotion shudhu seed data theke ashto — ekhon manager nijeriii banate pare:

- **Table**: naam, type badge (CATEGORY_SALE/MEMBER_PRICE/COUPON_FLAT/COUPON_PERCENT),
  detail (category+%, coupon code+amount), date window, ACTIVE/OFF
- **New/Edit modal**: type select korle form change hoy (category sale → category+percent,
  coupon → code+amount/percent). Coupon code auto-uppercase
- **Power button** — on/off toggle (Strategy LIVE — POS e sathe sathe dam change hoy)
- **Delete** — confirm kore muchhe
- **Coupon tester** (nice panel): code + bill amount dile engine koto taka off dibe
  dekhay — PromotionEngine (Strategy) ta live test!

## 15. Activity Log (manager/admin)

"Ke void dilam? Ke price change korlo?" — ei question er answer. Prottek boro action er
**audit trail**:

- Ki ki record hoy: login (+ bhul password), logout, void, return, shrinkage, restock,
  product create/edit/delete (price old→new soho), promotion CRUD, PO er sob state
  change, template save/clone, points adjust, staff change, day close
- **Filter**: date window, actor, action type
- Badge color: auth = gray, void/shrinkage = red, money = yellow, catalog = blue,
  purchasing = green-ish
- Cashier/developer dekhte parbe na (403) — manager+ only
- 500 entry porjonto rakhe (alert feed er moto cap) — eta shift-level trail, forensic
  ledger na. Observer pattern use KORINAI — karon stock event e actor/intent nai;
  javadoc e reason lekha ache (sir jiggesa korle setai bolo)

## 16. Day Close / Z-Report (manager/admin)

Shift sheshe cash drawer milano-r screen — real supershop er daily routine:

- **Window** (default aaj) load korle: Net/VAT/Bills/Expected drawer cash KPI
- **Tenders table**: cash/card/bKash/Nagad/points koto ashe
- **Returns + Voids section**: math ta CHOKHE DEKHA jay (viva te gold)
- **Close the day**: drawer e jotola taka count kore likho → variance (counted − expected)
  — 0 hole sobuj (balanced!), na hole red
- **Print Z-slip** — thermal printer e print hoy (receipt er same printer component)
- **History** — age kono day close korle table theke abar print kora jay

**Cash math (sir jiggesa korle):** `expected = cashIn − changeOut − cashRefunds − voidCashOut`
- cashIn/changeOut = window er SHOB sale (same-window void net zero hoy)
- voidCashOut = oi void jara EI window e refund hoise (kale kal er sale aaj void korle
  AAJKER drawer theke taka jay — `voidedAt` field er jonno possible)
- Revenue figure e VOIDED khabi na, cash math e rakhte hoy — duita alada set, jaate
  farkiro na jay (javadoc e ache)

---

## 17. Developer Mode (shudhu developer login)

**developer / developer123** diye login korlei dekha jay (onno keu na). 3 ta tab:

1. **Patterns** — 18 ta card; click korle deep-dive modal: Problem → Keno ei pattern →
   Alternative (reject ki keno) → real class + file → **asol source snippet** → kon test
   prove kore → "See it live" (manager screen hole "manager login e dekho" hint dey)
2. **API explorer** — sob endpoint grouped (method badge, path, min role, description)
   + **Try it** button: param + JSON body dawa jay, nije r token e call hoy — 403 ashle
   oita GATE kaj korche dekhano hoy (feature!)
3. **Diagnostics** — persistence mode (MONGO/IN-MEMORY), active token, alert count,
   repo count, uptime, Dhaka clock

Backend theke data ashe (`/api/dev/*` — exact DEVELOPER role match, manager/admin o 403).
**Drift guard test** ace: card e jei class/test er naam ase, source tree te ta_na thakle
build FAIL — "ReceiptBuilder" type ghost ar hobe na. API catalog o Spring er actual
routing er sathe match kore.

---

## 18. 18 ta Design Pattern — ekta jaygay, Banglish e

| # | Pattern | Kothay (class) | Banglish e keno dorkar chilo |
|---|---|---|---|
| 1 | **Singleton** | `InventoryCatalog`, `DatabaseConnection` | Puro app e stock list er EKTA copy thakte hobe — duita thakle ek machine e bikri, onnakta te stock purano = same maal 2 bar bikri! |
| 2 | **Factory Method** | `ProductFactory` → `UnitProductFactory`, `WeighedProductFactory` | "Add item" form e tumi shudhu type bachao — factory thik class baniye dey. Na thakle if-else bhabo piece na kg |
| 3 | **Builder** | `PurchaseOrderBuilder` | PO te onek field — step by step baniye LAST e validate kore dey. Adha-bana PO system e dhukte parbe na |
| 4 | **Prototype** | `StandingOrderTemplate` | Weekly restock list clone kore notun DRAFT — 15 item abar abar select kora lagbe na. (Porano project e eta DEAD CODE chilo!) |
| 5 | **Adapter** | `CashAdapter`, `CardAdapter`, `BkashAdapter`, `NagadAdapter`, `PointsAdapter` | 5 dhoroner payment er SDK alada alada shape — adapter era sob k ek `PaymentChannel` interface e ana. Notun payment add korle bill er code change lage na |
| 6 | **Decorator** | `LineDiscount`, `CarryBagFee`, `DeliveryFee`, `RoundOffAdjustment` | Bill line er upor discount/charge JODA jay — line class gulo change kora lagbe na, joto khushi stack koro |
| 7 | **Facade** | `MartFlowFacade`, `BillingFacade` | Controller/prontend ekta simple method laage (`tender()`) — bitorer 10 ta subsystem era handle kore |
| 8 | **Proxy** | `RoleGuardProxy` (product repo er samne) | Cashier konoBHABE product create/delete korte parbe na — UI na, controller bug holeo na, DATA layer ei block. Security er last line |
| 9 | **Composite** | `ComboProduct` | Eid hamper = ek item, kintu stock tar component gulor modde theke ashe — 1 hamper bikri hoile prottek component er stock komre |
| 10 | **Observer** | `AlertService`, `ReorderSuggestionObserver`, `ExpiryWatcher` | Stock komle/expiry kale SYSTEM NIJERI alert uthre — kewu monito korte pare na |
| 11 | **Strategy** | `RegularPrice`, `CategorySale`, `MemberPrice` + `PromotionEngine` | Manager promotion on/off kore — till er pricing CODE change hoy na. Dynamic pricing |
| 12 | **Command** | `billing/commands/` (Reserve, Charge, AwardPoints, CreateSale) + void/return | Tender ekta choto transaction: card decline korle stock+points+sale SOB auto rollback. Ek dhap o adha thake na |
| 13 | **Template Method** | `AbstractReportGenerator` + 8 report | Notun report = 3 ta choto method. "Voided revenue na" rule ekbar likha — 8 jaygay copy-paste na |
| 14 | **Chain of Responsibility** | `billing/validation/` (6 handler) | Cashier EXACT first bhul ta dekhe — "stock nai, Coke er 10 lagan jay nai" type specific message |
| 15 | **State** | `suppliers/postate/` (6 state class) | DRAFT PO konoBHABE receive kora jay na — rule state class er bitorer, if-else cholche na. CLOSED ke cancel kora jay na |
| 16 | **Visitor** | `VatVisitor`, `ProfitVisitor`, `ReceiptFormatterVisitor` | Same bill line gulo theke VAT report + profit report + receipt format — 3 ta bhinnno kaj, ek traversal. Mongo theke load kora sale eo same math |
| 17 | **Memento** | `BillMemento` (session er undo stack) | Cashier er Undo button — mis-scan POS e proti minute hoy. 10 step porjonto fire jaa jay |
| 18 | **Iterator** | `InStockIterator`, `LowStockIterator`, `ExpiringSoonIterator`, `PriceRangeIterator` | "Low stock" view ar "Low stock" report SAME iterator chole — kokhono mismatch hobe na. Reset kore abar chola jay |

**Ekta intelligent design decision (sir jiggesa korle bole):** sale er status plain
enum (`COMPLETED/VOIDED/PARTIALLY_RETURNED/RETURNED`) kintu PO te full State pattern
KENO? Karon sale er kono status e bose thakena — void/return ek instant operation,
alada command e hoy. Kintu PO din din dhore ekta state e BOSE thake, ar prottek state e
allowed operation alada (draft e receive jay na, closed e cancel jay na). Pattern
business er upor base kora, syllabus er upor na.

---

## 19. Architecture — request ekta kivabe pochan hoy

```
Browser (vanilla JS SPA)
   │  fetch + Bearer token
   ▼
AuthFilter ──── token check → RoleContext (ThreadLocal) set → finally clear
   │
   ▼
Controller (khub patla — 1 line e facade call)
   │
   ▼
Facade (MartFlowFacade / BillingFacade / PurchasingService)
   │
   ▼
Domain POJOs ──── items, decorators, validation chain, commands,
   │              promotion engine, state machine, visitors...
   ▼
RoleGuardProxy (product repo er write gate: create=manager+, delete=admin)
   │
   ▼
Repository interface ──── Mongo (Atlas) thakle → MongoDB
                          na thakle → in-memory (demo mode)
```

**3 ta iron rule:**

1. **Domain e 100% plain Java** — kono Spring annotation nei. Spring shudhu `AppConfig`
   (DI wiring) + controller e. Tai pattern gulo "real" — framework er jadudora na.
2. **Taka sob jaygay `BigDecimal`** — 2 decimal, HALF_UP rounding, ekta `MoneyUtil`
   choke point. `double` floating point e 0.1+0.2 = 0.3000000004 hoy — taka te suicide.
3. **Somoy sob jaygay `TimeSource`** — Asia/Dhaka fixed. "Aaj" = Dhaka er aaj, server
   jekono deshe thakuk na keno.

**VAT math (important):** BD te price **VAT-inclusive** (shelf e 115 taka = 100 mall +
15 VAT). Tai VAT back-calculate hoy: `VAT = net × rate / (100 + rate)`.
Check: 115 @ 15% → 115×15/115 = **15.00** ✓ ; 107.50 @ 7.5% → **7.50** ✓.
Rates: Staples/Fresh 0%, Grocery/Dairy 7.5%, baki 15% — `CategoryRegistry` te policy
hisebe ache, ek line change kore sob jaygay update hoy.

---

## 20. Data model (ke ke ache, kar sathe kar somporko)

```
Product (abstract)
 ├── UnitProduct    (mrp, piece/pack stock)
 ├── WeighedProduct (pricePerUnit, fractional kg stock)
 └── ComboProduct   (components[] → Composite, scarcest stock)

User (username, passwordHash, role)          ← 3 role
Customer (phone unique, pointsBalance)       ← loyalty
Sale ── SaleLine[] (FULL snapshot)           ← bikri er history
 │     └─ Tender[] (type, amount, trxId)
 └── SaleReturn[] (per line qty, reason)
Promotion (CATEGORY_SALE / MEMBER_PRICE / COUPON_FLAT / COUPON_PERCENT, date window)
Supplier ── PurchaseOrder ── PurchaseOrderLine[] (orderedQty, receivedQty, unitCost)
              │                + Payment[]
              └── StandingOrderTemplate (Prototype)
Alert (ephemeral feed, 200 cap)
```

**Sob kichu (products, sales, returns, customers, promotions, PO, suppliers, users)
MongoDB te persist hoy** — restart dileo thake. Sudhu login token ar alert feed
in-memory (restart e abar login, data hu jalbe na).

---

## 21. Folder structure map

```
src/main/java/com/martflow/
├── MartFlowApplication.java    ← entry point
├── api/                        ← REST controller gulo (patla) + AuthFilter + DTO
├── app/                        ← AppConfig (DI wiring) + MartFlowFacade + SeedData
├── audit/                      ← AuditLog, AuditService (activity trail, 500 cap)
├── auth/                       ← User, PasswordHasher, TokenStore, AuthService
├── billing/                    ← Bill, BillMemento, BillingSession(+Registry),
│   ├── item/                     UnitLine/WeighedLine/ComboLine/AdjustmentLine
│   ├── decorator/                LineDiscount/CarryBagFee/DeliveryFee/RoundOff
│   ├── validation/               6 handler + chain
│   ├── commands/                 Billing pipeline
│   └── visitor/                  BillItemVisitor interface
├── catalog/                    ← Product hierarchy, Factory, InventoryCatalog,
│   └── iter/                     4 iterators + CategoryRegistry (VAT policy)
├── common/                     ← MoneyUtil, TimeSource, NotFoundException
├── dev/                        ← PatternCatalog + ApiCatalog (Developer Mode data,
│                                  drift-guarded by tests)
├── inventory/                  ← Observer events, AlertService, ExpiryWatcher,
│                                  InventoryService (stock chokepoint)
├── loyalty/                    ← Customer, LoyaltyService
├── payment/                    ← PaymentChannel + 5 adapter + vendor/ (fake SDKs)
├── persistence/                ← Repository interface, Mongo/in-memory repos,
│   └── proxy/                    mappers, RoleGuardProxy
├── pricing/                    ← Strategy gulo + Promotion + PromotionEngine
├── reports/                    ← AbstractReportGenerator + 8 report + CSV
│   ├── visitor/                  VatVisitor, ProfitVisitor, ReceiptFormatter
│   └── (DayClose + DayCloseService ← Z-report, ekhanei)
├── returns/                    ← ReturnService, SaleReturn
├── sales/                      ← Sale, SaleLine, ReceiptNoGenerator, SalesAdminService
├── security/                   ← Role, Caller, RoleContext (ThreadLocal), RoleGate
└── suppliers/                  ← Supplier, PO, Builder, PurchasingService,
    └── postate/                  State machine + StandingOrderTemplate (Prototype)

src/main/resources/static/      ← frontend (index.html, css/, js/ — views, router, api)
src/test/java/com/martflow/     ← 24 ta test class → 136 tests
```

---

## 22. Sir er samne 10 minute er demo script

1. **Login** — cashier chip e click. "4 role, real password, PBKDF2 hash"
2. **POS** — barcode scan (box e `8941234500011` + Enter) → Teer oil bill e.
   Aalu tile click → weight prompt 1.25 → weighed billing dekhao
3. **Attach customer** — 01711111111 (Nusrat) → 5% member discount LIVE namte dekhao
4. **Coupon** — SAVE50 → abar 50 taka off
5. **Take payment** — 2000 cash → Change dekhao → receipt print → **Enter chapo** —
   next customer (modal close + scan box e focus)
6. **Undo dekhao** — arekta item scan kore Undo chapo — Memento
7. Logout → **manager login** → **Dashboard** — aajker sales, VAT, low stock
8. **Sales Explorer** — aajker bill gulo → ekta receipt View → reprint;
   ekta Void (reason soho) → stock fire uthe, Activity Log e entry
9. **Promotions** — notun "Eid Snacks 15%" banao → POS e oi category er item er dam
   NAME dekhao → back eshe toggle off → dam abar age jemon
10. **Day Close** — mixed tender din banao → Day Close → preview math → drawer count
    → variance → Z-slip print
11. **Purchases** — seeded PO ta khulo → Receive (GRN) — batch+expiry soho stock e uthche
    → Template tab e "Clone into draft" — Prototype
12. Logout → **developer login** → **Developer Mode** — Builder card khulo (real snippet
    + test name) → API Explorer e `GET /api/products` try (200) → `POST /api/products`
    try (**403 — gate ta LIVE dekhao**) → Diagnostics. Session sesh 🔥

## 23. Expected questions + jawab (FAQ)

**Q: Payment gateway gulo ki real?**
A: Card/bKash/Nagad er SDK shape real (poisha conversion, response format) kintu actual
network call fake — course project e real merchant account lage. **Adapter layer ta
100% real** — real SDK lagiye ditle connector badle bill code ek line change hobe na.
README te clearly bolche.

**Q: Data ki harabe?**
A: Na. MongoDB Atlas e sob persist. Test kora achi: sale kore → app restart → receipt
byte-identical, stock/points/PO state sob thake.

**Q: 2 jon cashier ek sathe bill korle ki hobe?**
A: Prottek login token er nijer bill session (purano project e 1 global cart chilo —
sobaiyer basket meshye jeto!). Stock movement `synchronized` InventoryService er bitor
hoy — race hoy na.

**Q: VAT thik ache?**
A: Inclusive pricing, back-calc `net×rate/(100+rate)`, per-line round kore sum —
`VatVisitorTest` e exact number pin kora (115@15%→15.00). VatSummary report e slab
onujayi total — NBR er musol at dhrar ready.

**Q: Pattern gulo ki real na dummy?**
A: 167 test era prove kore — pipeline rollback test (declined card), state matrix test,
proxy 403 test, hook-order test. Ar 3 pattern joono age dummy chilo (Prototype dead
code, TaxVisitor test-only, Customer observer test-only) — project e EKhON business
feature bojha: weekly template, NBR VAT report, real customer points.

**Q: Developer account ta ki? Ke eta dekhbe?**
A: `developer/developer123` — shudhu ei login Developer Mode dekhe (pattern deep-dive +
API explorer + diagnostics). Manager/admin/cashier er navbar e oita nai, URL e gelao
redirect. Developer till-side screen chalate pare (live demo er jonno) kintu manager
power nai. Business ladder er side e bosh ase — "upor" na.

**Q: Z-report e cash math kivabe?**
A: `expected = cashIn − changeOut − cashRefunds − voidCashOut`. CashIn window er SHOB
sale er cash tender (void howay gele o — taka to drawer e dhukechilo), change sob
drawer theke jay, cash refund jay, ar oi void jara EI window e refund hoise (kale kal er
sale aaj void korle aajker drawer khoti hoy — `voidedAt` er jonno). 6 ta hand-checked
test e pin kora.

**Q: Notun kono pattern add korco?**
A: NA — 18 e 18 i thakbe. Audit trail (Observer hote parto) ar Z-report (Template
Method hote parto) dono te e reject korchi, ar reason javadoc e lekha ase. Feature
age, pattern porer — ei project er main policy.

---

*Ei document ta `README.md` er Bangla companion — README te English e formal details
(setup, API table, architecture diagram) ache, ekhane business-level bujhaano.*
