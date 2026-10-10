# MoneyPOS Repository-driven Handoff

> Last updated: 2026-10-08. This is a concise, Git-explainable restart point; it is not an architecture or decision log.

## Recovery order

Read root `AGENTS.md`, the current architecture overview, the current phase plan, this file, linked frozen decisions,
then verify branch/HEAD/status/diff. Repository facts override this file when they differ; correct this file before
continuing.

## Current repository fact

| Field | Value |
| --- | --- |
| Branch | `dev` |
| ME-1.4 close commit | `108a866 docs(member): close target benefit fulfillment` |
| Working tree | **Clean after ME-1.6H-A implementation and validation; Windows Electron smoke evidence remains pending.** |
| Current phase | `ME-1.6H 商品寄存与提货可追溯性、小票 — H-A complete, H-B next` |
| Current plan | `MoneyPOS-ME-1.6H-Deferred-Quantity-Traceability-and-Receipt-Implementation-Contract.md` |
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

The exact next action is the remaining ME-1.6H-D Windows Electron/POS manual matrix: verify 1280×800 POS, normal-cart
isolation, deposit quote/payment/order/receipt, pickup/stock deduction/pickup receipt, failure-and-retry behavior,
and existing AMOUNT/TARGET paths. Windows remains an outstanding close condition until actually run.

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

## Handoff rule

Before ending a substantive session, update this file with the actual working-tree state and validations. Before a
cross-computer handoff, commit and push first; then verify `dev...origin/dev` has neither `ahead` nor `behind`.
