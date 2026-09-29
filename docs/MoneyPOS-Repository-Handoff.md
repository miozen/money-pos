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
| ME-1.4 code commit | `3dc88ec feat(member): implement target benefit settlement` |
| Working tree | **CLEAN and synchronized** after ME-1.4 implementation and handoff are pushed |
| Current phase | `ME-1.4 TARGET 专用结算、进度、补差和人工确认 — complete` |
| Current plan | `MoneyPOS-ME-1-Member-Entitlement-Implementation-Checklist.md` |
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

## Validation actually performed

- `mvn -q -pl qk-money-app/money-app-biz -am test-compile -DskipTests` passed.
- Isolated `money_pos_test`: `MemberTargetBenefitServiceIntegrationTest` (2/0/0),
  `MemberBrandBenefitLedgerServiceIntegrationTest` (3/0/0), and the route contract passed.
- Isolated full `mvn -q test` passed: 127 tests, zero failures/errors.
- `mvn -q package -DskipTests`, `bash scripts/test-architecture-scan.sh`, and
  `bash scripts/architecture-scan.sh --check-new` passed.
- `git diff --check` passed before `3dc88ec`.

## Blockers and decisions

There is no unresolved business or architecture decision. Frozen TARGET rule: dedicated settlement uses the current
brand-level price through normal immediate Checkout; only manual confirmation upgrades. Historical refunds require
review and cannot automatically downgrade a member level.

## Exact next action

ME-1.4 has no active implementation action. Verify `git status --short --branch` after this final handoff record is
pushed; it must show no changes and no ahead/behind before selecting ME-1.5.

## Handoff rule

Before ending a substantive session, update this file with the actual working-tree state and validations. Before a
cross-computer handoff, commit and push first; then verify `dev...origin/dev` has neither `ahead` nor `behind`.
