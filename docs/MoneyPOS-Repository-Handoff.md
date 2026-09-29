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
| ME-1.2 code commit | `e44c6e9 feat(member): implement quantity deferred fulfillment` |
| Working tree | **CLEAN and synchronized** after final handoff record is pushed |
| Current phase | `ME-1.2 QUANTITY 延迟履约 — complete` |
| Current plan | `MoneyPOS-ME-1-Member-Entitlement-Implementation-Checklist.md` |
| Architecture fact | `MoneyPOS-Current-Architecture-and-Business-Scenarios.md` |
| Frozen decisions | ME-1 checklist; `MoneyPOS-AI-Handoff.md`; applicable AD/ME decision records |

## Last completed reliable point

ME-1.1 is committed in `30975df`. It supplies brand benefit tiers and UMS quantity/amount/target ledger foundations;
its recorded isolated MariaDB and Checkout regression evidence applies only to that committed baseline.

## Uncommitted ME-1.2 implementation state

- Added Entity-free UMS quantity-right query/pickup commands and GMS member-pickup command contracts.
- Added `V1.0.6` pickup tables, TRADE pickup entities/mappers, deferred-pickup DTOs, a deferred purchase endpoint,
  and a TRADE pickup orchestration draft.
- Consolidated normal sale and native `MEMBER_PICKUP` onto one atomic deduction/combo-expansion implementation, while
  keeping their log/document types separate from the entry point onward.
- Added UMS quantity-right pickup deduction, TRADE request idempotency, and end-to-end pickup rollback coverage.
- Added deferred-purchase pricing that reuses normal price rules but defers physical availability validation to pickup;
  ordinary trial and ordinary checkout remain stock-strict.
- Added QUANTITY refund splitting: unpicked rights are cancelled without stock movement; only picked quantities invoke
  native GMS `MEMBER_PICKUP_RETURN`. `V1.0.7` expands the inventory-document type column for that frozen code.

ME-1.2 implementation, validation, commit and synchronization are complete. The next session must not start ME-1.3
without a new accepted phase instruction.

## Validation actually performed

- `mvn -q -pl qk-money-app/money-app-biz -am test-compile -DskipTests` passed.
- Isolated `money_pos_test`: `MemberPickupStockCommandIntegrationTest` (1/0/0),
  `DeferredQuantityPickupServiceIntegrationTest` (6/0/0), `CheckoutIntegrationTest` (18/0/0), and
  `PosDeferredQuantityRouteContractTest` (1/0/0) passed.
- `git diff --check` passed.

- Isolated full `mvn -q test` passed: 64 test classes and 121 tests, zero failures/errors.
- `mvn -q package -DskipTests`, `bash scripts/test-architecture-scan.sh`, and
  `bash scripts/architecture-scan.sh --check-new` passed.

## Blockers and decisions

There is no unresolved business or architecture decision. Frozen QUANTITY rule: purchase confirms normal sales income
but does not deduct physical stock; pickup deducts quantity right and physical stock atomically; a refund without a
pickup must not restore stock, while a return of picked physical goods must use `MEMBER_PICKUP_RETURN`.

## Exact next action

No active ME-1.2 implementation action remains. Verify `git status --short --branch` after this handoff record is
pushed; it must show no changes and no ahead/behind before selecting a future accepted phase.

## Handoff rule

Before ending a substantive session, update this file with the actual working-tree state and validations. Before a
cross-computer handoff, commit and push first; then verify `dev...origin/dev` has neither `ahead` nor `behind`.
