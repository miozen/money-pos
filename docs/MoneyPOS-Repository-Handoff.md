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
| Working tree | **CLEAN after ME-1.6B and workflow commits were pushed; `dev...origin/dev` is synchronized. Windows smoke evidence remains pending** |
| Current phase | `ME-1.6 会员权益 UX — ME-1.6C next` |
| Current plan | `MoneyPOS-ME-1.6-Member-Benefit-UX-Design-and-Implementation-Contract.md` |
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

## Exact next action

Begin ME-1.6C with the AMOUNT pickup preview contract and the isolated QUANTITY/AMOUNT local carts. Separately retain
the ME-1.5E five-item Windows POS smoke matrix as an outstanding close condition.

## Handoff rule

Before ending a substantive session, update this file with the actual working-tree state and validations. Before a
cross-computer handoff, commit and push first; then verify `dev...origin/dev` has neither `ahead` nor `behind`.
