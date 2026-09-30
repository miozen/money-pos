# MoneyPOS Repository-driven Handoff

> Last updated: 2026-09-29. This is a concise, Git-explainable restart point; it is not an architecture or decision log.

## Recovery order

Read root `AGENTS.md`, the current architecture overview, the current phase plan, this file, linked frozen decisions,
then verify branch/HEAD/status/diff. Repository facts override this file when they differ; correct this file before
continuing.

## Current repository fact

| Field | Value |
| --- | --- |
| Branch | `dev` |
| ME-1.4 close commit | `108a866 docs(member): close target benefit fulfillment` |
| Working tree | **CLEAN after ME-1.5A and in-progress ME-1.5B checkpoint `393221f` was committed and pushed** |
| Current phase | `ME-1.5 会员权益操作化收口 — ME-1.5B local implementation in progress` |
| Current plan | `MoneyPOS-ME-1.5-Member-Benefit-Operationalization-Implementation-Plan.md` |
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
- ME-1.5B has local UI/API paths for QUANTITY purchase, TARGET dedicated sale settlement and AMOUNT pickup full refund.
  Goods search/scans remain Workspace-local. `BenefitPaymentEditor` is present but not yet wired through every existing
  benefit operation dialog, so B remains in progress and C～E have not started.

## Validation actually performed

- `source /home/mio/.nvm/nvm.sh && nvm use 20 && npm run build` passed under Node `v20.20.2` / npm `10.8.2`
  (only pre-existing warnings), and `git diff --check` passed after its source changes.
- Isolated `money_pos_test`: `FinanceFeatureIntegrationTest` (14/0/0),
  `HomeCountSnapshotCharacterizationTest` (7/0/0), `UmsMemberBenefitRouteContractTest` (1/0/0),
  `CheckoutIntegrationTest` after fixture correction (18/0/0), and final `mvn -q test` (130/0/0) passed.
- `mvn -q package -DskipTests`, `bash scripts/test-architecture-scan.sh`,
  `bash scripts/architecture-scan.sh --check-new`, and current `git diff --check` passed.

## Blockers and decisions

There is no unresolved business or architecture decision. Frozen TARGET rule: dedicated settlement uses the current
brand-level price through normal immediate Checkout; only manual confirmation upgrades. Historical refunds require
review and cannot automatically downgrade a member level.

## Exact next action

Complete only ME-1.5B: wire the Workspace-local mixed-payment editor into all benefit operation dialogs and run its
focused interaction checks; do not begin ME-1.5C～ME-1.5E early.

## Handoff rule

Before ending a substantive session, update this file with the actual working-tree state and validations. Before a
cross-computer handoff, commit and push first; then verify `dev...origin/dev` has neither `ahead` nor `behind`.
