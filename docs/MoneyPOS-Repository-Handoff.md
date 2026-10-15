# MoneyPOS Repository-driven Handoff

> Last updated: 2026-10-15. This is a concise, Git-explainable restart point; it is not an architecture or decision log.

## Recovery order

Read root `AGENTS.md`, the current architecture overview, the current phase plan, this file, linked frozen decisions,
then verify branch/HEAD/status/diff. Repository facts override this file when they differ; correct this file before
continuing.

## Current repository fact

| Field | Value |
| --- | --- |
| Branch | `dev` |
| ME-1.4 close commit | `108a866 docs(member): close target benefit fulfillment` |
| Working tree | **GMS-OPT-1 implementation is locally complete but not yet committed; see Exact next action.** |
| Current phase | `FCO-1 GMS — OPT-1 complete locally; validation and Git synchronization pending` |
| Current plan | `MoneyPOS-Feature-Consistency-Audit-and-Optimization-Implementation-Contract.md` |
| Architecture fact | `MoneyPOS-Current-Architecture-and-Business-Scenarios.md` |
| Frozen decisions | ME-1 checklist; `MoneyPOS-AI-Handoff.md`; applicable AD/ME decision records |

## Last completed reliable point

ME-1.3 is committed in `3cffdec` and its final handoff is `2ff751c`. It supplies AMOUNT non-product receipts,
frozen-tier mixed supplement pickup and full-pickup refund reversal.

## Local ME-1.4 implementation state

- `V1.0.9` adds TRADE-owned TARGET sale contribution links plus isolated non-product adjustment receipts and payments.
  Ordinary immediate orders remain the sole writer of normal order, payment, inventory, cost and sales data.
- `3dc88ec` adds Entity-free UMS TARGET-plan snapshots and confirmation/review commands; `/pos/target/settle`,
  `/supplement`, `/waive` and `/confirm` are all protected by `pos:cashier`.
- TARGET settlement calls existing immediate Checkout, then writes one `SALE_CONTRIBUTION` per plan/order. Supplement
  creates income-bearing non-product receipt payments; waiver creates no payment. Target tiers change only after
  explicit confirmation. Any linked full or partial historical refund creates `REFUND_REVIEW_REQUIRED` and status
  `REVIEW_REQUIRED`, never an automatic downgrade.

## Local ME-1.5 implementation state

- TRADE now exposes Entity-free non-product receipt daily/payment snapshots. FIN/HOME merge only non-product income,
  receipt and refund projections; product order count, sales volume and cost remain order-derived.
- UMS/TRADE provide protected member-benefit overview, TARGET progress-log and benefit trade-history read routes;
  the new `/ums/member-benefit` page calls the established write APIs with per-operation idempotency request IDs.
- The generated frontend component registry includes `ElRow` and `ElCol` used by the new page.
- A pre-existing `CheckoutIntegrationTest` fixture used a decimal nanosecond suffix which made the generated
  inventory document number exceed `doc_no varchar(32)`. The fixture now uses an equivalent unique base36 suffix;
  no production business rule changed.
- `MoneyPOS-ME-1.5-Member-Benefit-Operationalization-Implementation-Plan.md` is frozen locally as the unique
  follow-up contract. It defines POS full-screen Workspace, selected-member/POS-store isolation, three permissions,
  tier management and ME-1.5A～ME-1.5E.
- ME-1.5A has extracted the existing benefit screen as `MemberBenefitWorkspace`. The POS HeaderBar opens it in a
  main-window full-screen dialog, shallow-copying `currentMember` only as its initial value. The Workspace searches
  and changes only its local `selectedMember`, never calling `bindMember` or accessing cart/payment/trial state.
  The backend page reuses the same component and has no operator member-ID input.
- ME-1.5B completes local UI/API paths for QUANTITY purchase, TARGET dedicated sale settlement and AMOUNT pickup full
  refund. Goods search/scans remain Workspace-local. All payment-bearing dialogs use `BenefitPaymentEditor`, which
  loads the existing payment dictionary/tags, emits only local payment payloads and is destroyed on close; no Workspace
  path reads or writes ordinary POS payment/cart/member-binding state.
- ME-1.5C adds Flyway `V1.0.10` for the dynamic benefit menu and the frozen `operate/manage/tier` capability resources.
  Existing POS-capable roles retain daily benefit access; manage/tier remain explicit grants. Static routing is removed,
  menu registration is permission-tree-driven, and API/UI enforcement follows the new capability split.
- ME-1.5D has local, uncommitted UMS tier CRUD backed by an Entity-free GMS brand/price-level query contract, dynamic
  tier menu migration `V1.0.11`, tier-only REST guards and the backend tier-management page. Save validates the existing
  GMS brand and permitted price level, non-negative amount/rank/sort and same-brand code/rank uniqueness; issued-right
  snapshots are not changed. The V1.0.10 role-grant `INSERT ... SELECT` was corrected to select its declared `role_id`.

## Validation actually performed

- `source /home/mio/.nvm/nvm.sh && nvm use 20 && npm run build` passed under Node `v20.20.2` / npm `10.8.2`
  (only pre-existing warnings), and `git diff --check` passed after its source changes.
- ME-1.5B's final Node 20 production build passed with the same pre-existing warnings. Static focused inspection found
  no `paymentList`, `bindMember`, `scanAndAddToCart` or `/pos/settleAccounts` reference in `views/ums/memberBenefit/`.
- C: `UmsMemberBenefitRouteContractTest` passed and Node 20 production build passed (only pre-existing warnings).
- D: after rebuilding only `money_pos_test`, Flyway migrated an empty isolated schema through `V1.0.11`; focused
  `MemberBenefitTierServiceTest` (3/0/0) and `UmsMemberBenefitRouteContractTest` (2/0/0) passed. Node `v20.20.2` /
  npm `10.8.2` `npm run build` passed with only the existing browser-data/CSS/dynamic-import/chunk-size warnings.
- E: full isolated regression initially found two stale test fixtures after ME-1.5C permission migration. The deferred
  quantity route contract now asserts frozen `operate/manage` permissions while retaining ordinary settlement's cashier
  guard; the POS-member integration fixture now uses an authenticated `SecurityUserDetail` with `pos:cashier`.
  Both focused regressions pass. The full test reports after the follow-up full run contain no failures/errors; package,
  both architecture gates, `git diff --check`, and Node 20 production build pass. Windows Electron smoke remains
  unrun because this Linux session has no callable Windows UI session; it must be completed before ME-1.5 can close.
- Isolated `money_pos_test`: `FinanceFeatureIntegrationTest` (14/0/0),
  `HomeCountSnapshotCharacterizationTest` (7/0/0), `UmsMemberBenefitRouteContractTest` (1/0/0),
  `CheckoutIntegrationTest` after fixture correction (18/0/0), and final `mvn -q test` (130/0/0) passed.
- `mvn -q package -DskipTests`, `bash scripts/test-architecture-scan.sh`,
  `bash scripts/architecture-scan.sh --check-new`, and current `git diff --check` passed.

## Blockers and decisions

There is no unresolved business or architecture decision. Frozen TARGET rule: dedicated settlement uses the current
brand-level price through normal immediate Checkout; only manual confirmation upgrades. Historical refunds require
review and cannot automatically downgrade a member level.

ME-1.6A has now frozen the UX and staged implementation contract in
`MoneyPOS-ME-1.6-Member-Benefit-UX-Design-and-Implementation-Contract.md`. It freezes separate QUANTITY/AMOUNT
transactions, one selected AMOUNT right per use, explicit (never automatic) TARGET plan selection from a single-brand
normal cart, POS TARGET-plan creation, and backend-only risk actions. It is design-only and awaits user confirmation;
implementation has been confirmed by the user and ME-1.6B is complete locally. The B work replaces the technical
first screen with three business entries, keeps the selected member local to the workspace, hides internal identifiers
and maps business display names through Entity-free GMS contracts. It does not alter any write route or transaction.
It is committed in `fee5c30` and, together with the Codex-push workflow update `977e545`, is pushed and synchronized.

ME-1.6C adds an AMOUNT pickup preview with no persistence writes, then wires QUANTITY and AMOUNT into independent
member-benefit carts, barcode dispatch and local payment state. QUANTITY supports multi-line pickup; AMOUNT requires
one selected right, enforces its brand, displays preview totals/deduction/supplement/balance-after, and reuses the
existing pickup transaction for final revalidation. The full isolated Maven test, package, both architecture gates,
Node 20 production build and diff check passed before this handoff update.

ME-1.6D adds UMS-owned `POST /pos/target/plans`, which validates the member and delegates creation/idempotency to the
existing Entity-free TARGET ledger command. `GET /ums/member-benefit/target-plan-options` exposes only the selected
member/brand's `IN_PROGRESS` plans to ordinary POS checkout. The checkout dialog now offers this explicit choice only
for a single-brand cart: no choice retains `/pos/settleAccounts`; a selected choice calls the existing
`/pos/target/settle` with the unchanged settlement payload. Mixed-brand carts disable selection and remain normal
checkout. Focused target/route regressions and the full isolated Maven suite, package, both architecture gates, Node
20 production build, and diff check passed.

ME-1.6G adds a protected QUANTITY deferred-pricing trial route, an isolated deposit cart with normal-cart price
labels, local whole-order discount, and Checkout-equivalent local payment allocation. `1bba18c` is the implementation
commit. `e7d01fe` shortens the UI-generated deposit request ID below `oms_order.order_no varchar(32)`; it fixes the
confirmed insert failure without changing final transaction-side pricing or payment validation.

The following product follow-ups are intentionally recorded only and are **not implemented**:

- Deposit orders currently use a short `MB-QUANTITY-BUY-*` idempotency request ID while ordinary checkout uses
  `REQ${Date.now()}`. A later change should align the deposit *order request ID* to the ordinary `REQ` style while
  keeping it within 32 characters. Quantity pickup is a separate pickup document: its `request_no` is idempotency
  data and its `MP*` pickup number is the business trace number, so any desired display/identifier unification needs
  an explicit scope decision.
- Quantity pickup persists a pickup business document and items, updates the member right, and writes inventory
  documents/logs. `MEMBER_PICKUP` is the current internal inventory movement code. The requested business wording is
  "会员提货" (or equivalent) in display/dictionaries; do not rewrite the internal code or historic data without a
  dedicated compatibility plan.
- Deposit is an `OmsOrder` and now invokes ordinary checkout receipt printing as a non-blocking post-success effect.
  QUANTITY pickup has its own non-sales receipt route/template and automatic-print setting. AMOUNT pickup persists
  a pickup document and a supplement receipt but has no pickup-receipt print effect yet; TARGET normal sales use the
  ordinary order receipt and have no separate TARGET receipt.

## Exact next action

GMS-OPT-1 separates GMS turnover-warning snapshot reads from writes. `GmsTurnoverService` now computes the dashboard
and reads trends only; the new GMS-owned snapshot command writes the same-day row, and a GMS-owned startup/five-minute
task triggers that command with a same-JVM overlap guard. The turnover SQL/formula, routes, responses, snapshot table,
tenant exemption and historical data were not changed. The new focused tests passed: 3 tests, 0 failures, 0 errors.
The documented isolated `money_pos_test` full `mvn -q test`, `mvn -q package -DskipTests`,
`bash scripts/test-architecture-scan.sh`, `bash scripts/architecture-scan.sh --check-new` and `git diff --check` passed.
Current changes are uncommitted; the exact next action is to inspect the diff, commit GMS-OPT-1, push `dev`, fetch, and
verify `dev...origin/dev` is `0/0`. Only then begin the separately frozen GMS-OPT-2 design.

ME-1.6F final automated gates passed against isolated `money_pos_test`: full Maven regression, package, Node 20 production build, both architecture scans and diff check. The Windows Electron/POS matrix is unrun: this Linux session exposed no controllable Windows UI target, so no manual evidence is claimed.

ME-1.6G is completed in `1bba18c` and its `oms_order.order_no` length follow-up is in `e7d01fe`. It adds only
QUANTITY pre-submit deferred pricing, local whole-order discount and Checkout-equivalent local payment allocation;
normal POS state remains isolated and physical stock remains a pickup-only concern. The protected trial route uses
the existing no-stock-check pricing path; final settlement still recalculates in its transaction. Focused and full
isolated Maven regressions plus Node 20 build passed.

ME-1.6H is frozen in `MoneyPOS-ME-1.6H-Deferred-Quantity-Traceability-and-Receipt-Implementation-Contract.md`.
It turns the recorded follow-ups into four ordered slices: use local readable request IDs — ordinary POS
`REQyyyyMMddHHmmssSSS`, QUANTITY deposit `QDPyyyyMMddHHmmssSSS`, and QUANTITY pickup
`QPKyyyyMMddHHmmssSSS`; display the internal `MEMBER_PICKUP` movement as "会员提货"; reuse ordinary order receipt
printing after deposit; then add an explicitly non-sales quantity-pickup receipt. It preserves `MP*` pickup business
numbers, transaction ownership and historical internal codes.

ME-1.6H-A is complete. A shared local request-ID formatter now emits `yyyyMMddHHmmssSSS` using the cashier
workstation's local clock: normal checkout uses `REQ`, quantity deposit uses `QDP`, and quantity pickup uses `QPK`.
The quantity flows cache their request ID for an unchanged failed retry and discard it on success, cancellation/close,
or an explicit draft change. GMS stock-log filtering, tags and audit titles display `MEMBER_PICKUP` as “会员提货” and
`MEMBER_PICKUP_RETURN` as “会员提货退回”, while all persisted codes remain unchanged. The deterministic formatter
check and Node 20 production build passed (only the established frontend warnings), as did `git diff --check`.

ME-1.6H-B is complete. A successful QUANTITY deposit now extracts its returned `OmsOrder` number and invokes the
existing ordinary checkout receipt endpoint as a non-blocking post-commit effect. Missing order numbers and print
errors only log a warning: neither can turn a successful deposit into a failed one or cause an automatic reprint.

ME-1.6H-C is complete. Flyway `V1.0.13` adds `sys_print_config.member_pickup_auto_print` (default enabled), and the
existing backend “小票打印与硬件设置” page exposes it as an independent “会员提货单” switch while retaining the same
printer, store heading/contact/footer configuration. A protected TRADE-owned pickup-receipt read/print route builds
a non-sales receipt from the completed pickup and its item records through the Entity-free goods snapshot contract.
The receipt prints its pickup number, member ID, items, quantities, time, current operator and explicit no-revenue
notice; it never opens the cash drawer. The successful pickup UI calls it asynchronously, so a print error cannot
rollback or misreport the completed pickup.

ME-1.6H-D automated validation is complete. The documented command, with credentials extracted from
`application-dev.yml` and fixed `127.0.0.1:3306/money_pos_test`, completed the isolated full Maven regression:
68 reports, 135 tests, 0 failures and 0 errors. `mvn -q package -DskipTests`, Node 20 production build, both
architecture gates and `git diff --check` also passed. The frontend build retains only its pre-existing
Browserslist/CSS/dynamic-import/chunk-size warnings. An initial attempt without the documented environment variables
and a later overlapping attempt are invalid diagnostic history, not validation evidence.

The exact next action is the remaining ME-1.6H-D Windows Electron/POS manual matrix: verify 1280×800 POS,
normal-cart isolation, deposit quote/payment/order/receipt, pickup/stock deduction/pickup receipt,
failure-and-retry behavior, and existing AMOUNT/TARGET paths. Windows remains an outstanding close condition until
actually run.

ME-1.6I is an accepted implementation contract in
`MoneyPOS-ME-1.6I-Pickup-Confirmation-Target-Plan-and-Usage-Traceability-Implementation-Contract.md`. It orders
QUANTITY pickup preview/confirmation, auditable TARGET cancellation rather than physical deletion, mixed-cart
contribution only from the selected plan brand's settled detail amount, then member-profile asset-and-benefit history
with QDP order-detail routing and AMOUNT pickup receipt printing. I-D uses an Entity-free, read-only aggregation and
does not write synthetic entries into the legacy balance/coupon log or replace GMS inventory traceability.

ME-1.6I-A is complete: `POST /pos/deferred-quantity/pickup-preview` reads the current QUANTITY right and
goods snapshot without a write, and the standalone QUANTITY UI now requires “预览提货 → 确认提货”; any quantity change
invalidates the preview. The actual pickup still uses its existing atomic transaction for final right and physical-stock
validation. Focused tests (7 pickup-service + 1 route-contract) and the full isolated `money_pos_test` Maven suite
(68 reports, 136 tests, 0 failures/errors), package, Node 20 build, both architecture gates and `git diff --check`
passed. ME-1.6I-B is complete in `2111dc2`: TARGET cancellation is an auditable `CANCELLED` state, never deletion;
it records request/operator/time/reason and rejects every plan with a post-initial business flow. ME-1.6I-C is complete:
ordinary checkout automatically contributes each eligible brand's persisted order-detail net amount to its one TARGET
plan in the same transaction. Ambiguous same-brand plans reject checkout, target-complete plans await manual confirmation,
and linked refunds still require review. ME-1.6I-D is complete: UMS exposes an Entity-free, read-only member asset-and-benefit
history which combines legacy asset logs, its own entitlement ledgers and TRADE document snapshots; QDP uses an explicit ordinary-order
detail target, and AMOUNT pickup now asynchronously prints the same non-sales pickup receipt without opening the drawer. The I-D full
isolated regression reports 68 reports, 140 tests, 0 failures/errors; package, Node 20 build and architecture gates passed. The exact
next action is the remaining ME-1.6H-D Windows Electron/POS manual matrix. It is the only outstanding ME-1.6 close condition and must
not be reported complete until actually run.

ME-1.6I repair slice is complete. It initially kept the then-frozen I-C contribution formula unchanged,
but makes the QUANTITY pickup input fit its own table column, keeps TARGET cancellation reason input on one line, renders
asset/benefit dimensions and known business summaries in Chinese, and opens MP/MAP records in a read-only, non-printing
pickup receipt dialog instead of normal order detail. The protected GET pickup-receipt routes share the existing receipt
snapshot services and do not print. Checkout limits the automatic TARGET-plan list to its own scroll area so it cannot
cover the whole-order discount controls. Focused route-contract tests, package, Node 20 production build, both architecture
gates and `git diff --check` passed. The initially recorded `clean test` errors were not the accepted validation entry:
they mixed a non-contract clean invocation with reports from a later focused run that omitted the required `MONEY_TEST_DB_*`
environment. The documented, credential-injected `mvn -q test` was rerun and passed with 68 reports, 140 tests, 0 errors
and 0 failures.

ME-1.6I follow-up display repair is committed in `3573b6b`. The TARGET cancellation reason label is kept on one line;
asset-and-benefit records expose a clickable document only when the backend explicitly supplies an `ORDER`, `RECHARGE`
or `PICKUP` detail target, so TARGET/TRADE records without a readable document display `-` rather than opening normal-
order detail. Pickup receipts use a single-row description layout for long values and label the returned `memberId`
accurately as “会员 ID” (the database primary key, not a member card number).

On 2026-10-12, the user replaced I-C's prior whole-order-discount allocation premise: manual whole-order discounts
remain order-level settlement facts and are not allocated to order details or TARGET plans. Automatic TARGET
contribution and the POS contribution preview now use only each brand detail's own persisted net amount (after existing
detail coupon/commodity deductions). Full-reduction vouchers remain untouched: TARGET goods do not participate in or
bear them, and their member-asset/refund lifecycle is unchanged. No route, migration, transaction boundary, order
field, refund calculation or historical order was changed. The isolated documented full Maven regression completed
with 68 Surefire reports and no failures/errors; `mvn -q package -DskipTests`, Node 20 production build, both
architecture gates and `git diff --check` passed. The first single-module Maven attempt was invalid because it did not
include the reactor parent; it was followed by the documented full-reactor command.

The 2026-10-12 TARGET whole-order-discount correction is committed as `6af56e7`, pushed to `origin/dev`, fetched and
verified with `dev...origin/dev = 0/0`. The exact next action is the remaining ME-1.6 close condition: run the Windows
Electron/POS manual matrix for 1280×800 POS, normal-cart isolation, deposit quote/payment/order/receipt,
pickup/stock deduction/pickup receipt, failure-and-retry behavior, and existing AMOUNT/TARGET paths. Windows remains
outstanding until actually run.

The 2026-10-12 follow-up is committed as `538a2d4`, pushed to `origin/dev`, fetched and verified with
`dev...origin/dev = 0/0`: normal checkout now defaults every eligible TARGET
plan to checked but sends only the cashier-confirmed plan IDs. TRADE validates selected plans against the member,
cart brands and current eligibility in the existing settlement transaction; deselected plans create no contribution,
different brands may be selected together, and selecting two plans for one brand is rejected. The checkout layout now
places whole-order discount and waive-member-coupon controls below the combined payment details, inside its scroll
area, so they cannot overlap the fixed received-total footer. No migration, historic-data rewrite, refund change,
voucher change or transaction-boundary change was made. The documented isolated full Maven regression passed with
68 Surefire reports, 141 tests, 0 failures/errors; package, Node 20 production build, both architecture gates and
`git diff --check` passed. The exact next action is the Windows Electron/POS manual matrix, the only remaining
ME-1.6 close condition.

On 2026-10-12, the accepted checkout UI follow-up preserves the original one-payment-per-row layout, payment inputs
and aggregate-channel tags;
uses a compact Element Plus title header (16px outer padding, 12px from title to cards), removes the Element Plus
footer wrapper's duplicate padding, and standardizes the surrounding containers at 8px/12px/16px spacing. It replaces
the fixed 480px workspace height with a `clamp(420px, calc(100vh - 170px), 480px)` viewport-aware height and shortens
TARGET plan display to “计入 + 计划名称 + 本单计入金额”. The right payment area normally fits payments, whole-order
discount and waive-member-coupon controls without scrolling; the left TARGET plan list remains the sole intended local
scroll area, with no extra explanatory text. Its checkbox rows use a shrinkable grid and wrapping plan text so long
plan names/amounts remain inside the card. The checkout footer now reuses the cart's existing `totalCount` (“共 N 件”)
and is an independent rounded card aligned to the workspace padding. Node 20 production build and `git diff --check`
passed. It is committed and pushed after the user's visual acceptance. Exact next action: run the outstanding Windows
Electron/POS manual matrix before closing ME-1.6.

ME-1.6J is accepted in `MoneyPOS-ME-1.6J-Benefit-Workspace-Operation-History-Decision-Contract.md`; its current
execution plan is `MoneyPOS-ME-1.6J-Benefit-Workspace-Operation-History-Implementation-Plan.md`. Its local
implementation is complete and adds only limit-bounded, read-only operation history inside each QUANTITY/AMOUNT/TARGET
workspace entry plus read-only MAR/MTR details. UMS reads only its own member-scoped ledgers; TRADE owns MAR/MTR
snapshots; no synthetic member log, forced document link, print, migration or business write was added. Default history
is 10 rows, expandable to 20; only explicit `ORDER`, MP/MAP and MAR/MTR targets are clickable.

J-A focused ledger/route tests passed (6 + 2); J-B focused receipt/route tests passed (2 + 2). The documented
credential-injected isolated `money_pos_test` full Maven command passed with 69 Surefire reports, 145 tests,
0 failures and 0 errors. `mvn -q package -DskipTests`, Node 20 production build, `scripts/test-architecture-scan.sh`,
`scripts/architecture-scan.sh --check-new` and `git diff --check` passed. The frontend retains only established
Browserslist/CSS/dynamic-import/chunk-size warnings. The first two attempted focused runs were configuration diagnostics:
one omitted the reactor parent; another omitted `MONEY_TEST_DB_NAME`; neither is validation evidence.

Exact next action: perform the Windows Electron/POS manual matrix for the three new history panels: QUANTITY, AMOUNT
and TARGET 10/20 switching; ORDER/MP/MAP/MAR/MTR details; no-document rows; read-only behavior; and POS-state
isolation. ME-1.6J and the broader ME-1.6 remain open until that manual evidence exists.

The 2026-10-12 display follow-up is committed in `48fa0c0`, pushed and synchronized (`dev...origin/dev = 0/0`): both read-only MP/MAP receipt snapshots now obtain
member name and phone through the existing Entity-free `MemberCheckoutQuery`; the UI displays those meaningful values
instead of the internal member ID. MAR/MTR payment-channel codes remain unchanged in storage, while the receipt dialog
resolves `paySubTag` through the existing frontend dictionary and displays its Chinese description. Backend compile,
Node 20 production build, focused quantity/amount pickup integration tests (7 + 5, all passing) and `git diff --check`
passed. Exact next action: retain and perform the Windows manual matrix above.

The 2026-10-12 pickup-detail layout follow-up is committed in `9058012`, pushed and synchronized (`dev...origin/dev = 0/0`): the two-column receipt header now orders
“提货单号 / 办理时间” first, then “会员姓名 / 手机号码”, with operator on the final row. Node 20 production build and
`git diff --check` passed. Exact next action: retain the Windows manual matrix above.

ME-1.6K is accepted in `MoneyPOS-ME-1.6K-Readable-Business-Document-Number-Decision-Contract.md`; its execution
plan is `MoneyPOS-ME-1.6K-Readable-Business-Document-Number-Implementation-Plan.md`. Its automated implementation
adds a tenant-scoped TRADE daily sequence and separates `oms_order.request_no` from the generated business `order_no`.
New REQ/QDP, MP, MAP, MAR/MARF and MTR documents originally used `prefix + yyyyMMddHHmmssSSS + six-digit daily sequence`;
historical records are not rewritten and old order-number replay lookup remains compatible. The isolated test database
was rebuilt and Flyway completed V1.0.0 through V1.0.16. Focused affected regressions passed (36 tests); the full
Maven suite produced 69 reports with zero failures/errors. Package, Node 20 production build, both architecture scans
and diff check passed; frontend warnings are the established Browserslist/CSS/dynamic-import/chunk-size warnings only.

ME-1.6K implementation and its frozen contract are committed in `f2444c4`, with this handoff update in `0f5f813`;
both are pushed and synchronized (`dev...origin/dev = 0/0`).

Exact next action: complete the Windows
Electron/POS manual matrix for new and historical REQ/QDP/MP/MAP/MAR/MARF/MTR detail, print and refund flows.

On 2026-10-12, the user accepted a narrowly scoped ME-1.6K rule revision before manual acceptance: future business
numbers use `prefix + yyyyMMddHHmmss + four-digit daily sequence` instead of milliseconds plus six digits. The sequence
remains independent per tenant, prefix and natural day, resets from `0001` each day, and rejects values beyond `9999`;
historical generated numbers are not rewritten. The implementation and all automated gates must be rerun, committed and
pushed before the Windows manual matrix resumes. K-D implementation now uses the accepted seconds-plus-four-digit
format; full isolated Maven regression (69 reports, 0 failures/errors), package, Node 20 build, both architecture
scans and diff check passed. The format revision is committed and pushed in `8ef80fb`, with `dev...origin/dev = 0/0`.

The 2026-10-12 amount-package management follow-up is a frontend-only, minimum-scope repair: the list resolves
stored brand IDs through the existing brand selection API and price-level codes through the existing `memberType`
dictionary; the edit form reuses the established brand selector and brand-scoped pricing-level selector. Invalid
historical references are explicitly labeled rather than silently changed. The “售价与权益额” row uses a two-column
grid so its label and input controls cannot wrap into the price-level row. No package payload, persistence, backend
validation or issued-right snapshot changed. Node 20 production build and diff check passed. It is committed and
pushed in `18509ed`, with `dev...origin/dev = 0/0`.

On 2026-10-12, the user confirmed all seven installed Windows/POS acceptance groups passed: installation/startup,
ordinary POS checkout, mixed-checkout workstation visibility, QUANTITY/AMOUNT/TARGET operations, TARGET boundaries,
benefit history/document details, and refund/restart persistence. The only subsequent code change is `ee0a37f`:
the QUANTITY “确认商品寄存” dialog now keeps its pricing summary above payment details and moves the whole-order
discount input below them. Payment and discount labels are left-aligned; pricing, payment allocation, submit request,
API and business behavior are untouched. Node 20 production build and `git diff --check` passed. The user subsequently
confirmed the new `dev` installer renders the dialog correctly. ME-1.6J and the full ME-1.6 phase are closed; no next
phase has been started.

## 2026-10-13 HOME 日快照后台刷新修复

用户已授权最小修复：`/home/count` 的今日四项 KPI 只读取 HOME 自有 `oms_daily_summary` 日快照；生产库当日存在
3 笔 `PAID` 订单、销售额 1324.00，但近八天无快照。启动及五分钟任务实际触发，却在补偿首个缺失日写入时因后台线程
没有 `SecurityContext`、`SecurityGuard.isLoggedState()` 直接解引用空 `Authentication` 而失败；任务记录错误后不会继续刷新当天。

修复将无认证上下文判定为未登录，保持现有操作人填充语义（空操作人）和所有 HOME 读写、路由、公式、迁移与事务边界不变。
`HomeCountSnapshotCharacterizationTest` 新增真实无请求/无认证上下文的快照刷新回归，并验证日快照订单数、销售额和利润。
该聚焦回归通过；`mvn -q package -DskipTests`、两项架构门禁、Node 20 前端生产构建及 `git diff --check` 均通过。

隔离库完整 Maven 回归在本环境未能成为通过证据：根 reactor 及 `-pl qk-money-app/money-app-biz -am` 均在既有测试类路径上报告
`NoClassDefFoundError`（例如 `CouponStatusEnum`、`OmsOrderDetailVO`）；这些类存在于已编译的 `money-app-api` 产物，且失败与本修复
无关。首次普通沙箱执行还因本机 MariaDB TCP 权限被拒绝，受控重试可连接数据库。不得将该全量回归记为通过；后续需单独诊断其
Surefire/reactor 类路径基线。

## 2026-10-13 TRADE 可读单据号首单序号修复

用户发现同日新单据表现为 `REQ...0009`、`MP...0010`、`MAP...0011`。经只读核对，生产 `REQ` 序列行的
`id=9` 而 `sequence_value=1`；此前 SQL 在首次 INSERT 后以 `LAST_INSERT_ID()` 取值，MariaDB 返回的是表自增主键
而非业务序号，故把全局行 ID 误拼入单据号。既有租户、前缀、自然日独立序列规则未改变，历史单据不改写。

修复移除对 `LAST_INSERT_ID()` 的依赖：原子插入/递增后，在既有写事务内对同一前缀、日期的序列行加锁读取
`sequence_value`。新增集成回归验证不同新前缀均从 `0001` 开始、同一前缀第二张为 `0002`；单据号回归（1）与完整
`CheckoutIntegrationTest`（18）通过，`mvn -q package -DskipTests`、两项架构门禁与 `git diff --check` 通过。
完整 Maven 回归未在本次单点修复中重新作为通过证据执行；先前记录的 Surefire/reactor 类路径基线问题仍待独立处理。

## 2026-10-13 发行版本统一与可见性

用户确认并冻结 `MoneyPOS-Release-Version-Contract.md`：`money-pos-web/package.json` 的 SemVer `version` 是唯一的
发行版本来源；根 Maven 的 `0.0.0-DEV` 只表示非发行本地构建。Windows EXE 工作流读取该版本、校验格式并以
`-Drevision` 传给 Maven；Electron Builder 与 Vite 同样从该 package 文件取得版本。后端 JAR 使用 Spring Boot
`build-info`，`GET /system/version` 在认证约束下返回其构建版本，备份 Manifest 也改读该构建版本，不再保留
`SysBackupService` 的 `2.2.0` 硬编码值。POS 顶栏展示版本；后台版本展示在左侧菜单栏顶部、品牌标题与“仪表盘”入口之间，并在侧栏折叠时隐藏。

Node 20 的 `npm run release:check` 与生产构建通过，产物确认包含 `1.0.12`；版本接口聚焦契约测试通过；默认本地 JAR
确认嵌入 `build.version=0.0.0-DEV`，模拟发行 `-Drevision=1.0.12` 后确认嵌入 `build.version=1.0.12`。Maven package、两项
架构门禁和 `git diff --check` 通过。完整 Maven 回归及 Windows EXE 实机显示仍未在本次变更中执行；完成后才可将 Windows
可见性验收记为通过。Exact next action: build the Windows EXE through the release workflow and verify the installer,
POS header, admin footer, authenticated `/system/version` response and a new backup Manifest all show the same version.

## 2026-10-13 HOME 近七日趋势日期顺序修复

`GET /home/charts?timeRange=today` 将 TRADE 商品订单趋势（原为 `MM-dd`）与非商品收款趋势（`yyyy-MM-dd`）直接以字符串键合并。
同一自然日因此会产生两个图表点，且 `TreeMap` 的字典序可将完整日期排在简短日期之后，导致横轴表现为 10 月 9、10、12 日后又出现 8 日。

修复将 TRADE 趋势合同的内部日期键统一为 `yyyy-MM-dd`；HOME 先按该键合并和按真实日期排序，最后才在既有
`TrendChartVO` 响应中格式化为 `MM-dd`。`/home/charts` 的前端展示格式、路由、金额与利润公式均未改变。新增回归覆盖订单和
非商品收款同日合并为一个点、销售额/利润累加及近七日标签单调递增。`HomeCountSnapshotCharacterizationTest`、
`mvn -q package -DskipTests`、两项架构门禁与 `git diff --check` 均通过。

## 2026-10-13 HOME 趋势修复测试夹具更正

上述趋势修复最初新增的测试夹具错误地调用了 `OmsMemberAmountReceiptPay.setCreateTime(...)`；该 Entity 未映射该字段，
所以用户重新打包时在 `testCompile` 阶段失败，未生成新 JAR/EXE。夹具已改为先插入支付记录、再仅在测试中以显式 SQL 列名更新
其 `create_time`，不改变生产 Entity 或持久化模型。`mvn -q package -DskipTests` 已确认包括测试源码在内能够编译并生成 JAR；
原记录中的 Home 聚焦测试“通过”不再作为有效证据；未按协议注入 `MONEY_TEST_DB_*` 的命令不能作为测试结论。
后续必须按 `MoneyPOS-AI-Handoff.md` 从 `application-dev.yml` 提取既有默认凭据并固定连接隔离 `money_pos_test`。

## 2026-10-15 HOME 近七日零值日期补齐

用户确认：HOME 的“近 7 日营业额与利润趋势”必须表达连续的七个自然日，而不是只显示发生交易或非商品收款的日期。
`timeRange=today` 现在在既有订单与非商品收款合并后，固定补齐从今天往前六天到今天的七个点；没有数据的日期返回销售额和净利润均为零。
月、年、累计趋势、路由、响应字段、金额公式和交易查询边界均未改变。`HomeCountSnapshotCharacterizationTest` 已扩展为断言七个连续日期及零值补齐；按既有固定凭据提取协议连接隔离 `money_pos_test` 的聚焦回归通过（9 tests，0 failures/errors），`mvn -q package -DskipTests`、两项架构门禁与 `git diff --check` 均通过。

用户进一步确认本月、本年趋势也不得跳过零营业额日期。相同的连续自然日补齐现已扩展为：本月从月初至今天、本年从年初至今天；不展示未来日期，累计趋势仍保持原有按实际数据点返回的行为。Home 聚焦回归（9 tests，0 failures/errors）、常规打包、两项架构门禁与差异检查再次通过。为兼容既有本地浏览器启动命令，还以 `-Drevision=1.0.0` 额外生成未提交的本地测试产物 `target/money-app-biz-1.0.0.jar`，并确认其已嵌入完整日期键和连续日补齐代码；这不改变正式发行版本来源或 Git 内容。

## 2026-10-15 HOME 本年/月度与总计/年度趋势聚合

用户确认的最小范围已经完成：今日（近七日）和本月继续按日；本年改为按月，固定返回当年一月至当前月的连续月点，缺失月销售额和净利润均为零；“总计/库存”中的营业额与利润改为按自然年汇总，库存卡片仍只表示当前库存成本货值，未构造库存历史趋势。

实现只在 `HomeServiceImpl` 的 HOME 读侧发生：订单日趋势和非商品收款日趋势先按原口径合并，再按月或按年二次汇总。路由、响应字段、订单/收款、库存、事务、品牌饼图和会员分布均未改变。`HomeCountSnapshotCharacterizationTest` 现覆盖月度累计、空月补齐与年度累计；隔离 `money_pos_test` 聚焦回归通过（11 tests，0 failures/errors），`mvn -q package -DskipTests`、两项架构门禁与 `git diff --check` 通过。

发行版本契约已补充本地浏览器联调标准命令：`mvn clean package -DskipTests` 后启动 `money-app-biz-0.0.0-DEV.jar`。本地调试不得手工使用 `-Drevision=1.0.0`；发行版本只能由 CI 从 `money-pos-web/package.json` 注入。用户已于 2026-10-15 确认本年仅显示月标签、总计仅显示年标签，以及空月显示零值，HOME 浏览器验收通过。

## 2026-10-15 Feature 一致性审查与渐进优化契约

用户确认继续保持单一 `money-app-biz` Maven 业务模块，并授权按 “GMS → UMS → TRADE → FIN → HOME → SYS 横切复核”
进行 Feature 一致性审查和最小优化。唯一当前契约为
`MoneyPOS-Feature-Consistency-Audit-and-Optimization-Implementation-Contract.md`：每个 Feature 必须先做只读审查，
将已确认约束、问题和可选建议分开记录；只有审查已证实、可独立回归的维护问题才能进入单一优化切片。不得借此拆分 Maven、
改变既有单库事务、公共 API、Flyway 历史、金额公式或历史数据。

FCO-1 GMS 只读审查完成并记录在 `MoneyPOS-FCO-1-Gms-Consistency-Audit.md`。已确认：GMS 的商品/库存所有权及
Entity-free 跨 Feature 契约保持完整；同时存在周转 GET 写快照、手工报损库存并发窗口、商品删除统计/套餐引用完整性、
以及部分 GMS 管理入口未做细粒度权限保护四项问题。审查未修改业务代码。候选 GMS-OPT-1 至 GMS-OPT-4 已记录；
其中 OPT-1 是不改商品/库存/金额/响应的读写分离优先候选，OPT-3/4 分别需确认删除和角色权限规则。

## Exact next action

选择并冻结一个 GMS 最小优化设计（建议先 GMS-OPT-1 周转快照读写分离），或明确跳过 GMS 优化后开始 FCO-2 UMS 只读审查；
在选择前不修改 GMS 业务代码。

## Handoff rule

Before ending a substantive session, update this file with the actual working-tree state and validations. Before a
cross-computer handoff, commit and push first; then verify `dev...origin/dev` has neither `ahead` nor `behind`.
