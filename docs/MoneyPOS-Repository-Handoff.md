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
| ME-1.3 code commit | `3cffdec feat(member): implement amount benefit pickup` |
| Working tree | **CLEAN and synchronized** after ME-1.3 handoff is pushed |
| Current phase | `ME-1.3 AMOUNT 权益包、混合补差与整笔提货退款 — complete` |
| Current plan | `MoneyPOS-ME-1-Member-Entitlement-Implementation-Checklist.md` |
| Architecture fact | `MoneyPOS-Current-Architecture-and-Business-Scenarios.md` |
| Frozen decisions | ME-1 checklist; `MoneyPOS-AI-Handoff.md`; applicable AD/ME decision records |

## Last completed reliable point

ME-1.2 is committed in `e44c6e9` and synchronized in `e3928a7`. It supplies QUANTITY deferred fulfillment,
native member pickup/return inventory commands and its recorded full isolated regression baseline.

## Local ME-1.3 implementation state

- `V1.0.8` adds TRADE-owned non-product AMOUNT receipts, isolated receipt payments, pickup headers and pickup lines.
  It does not create `oms_order`, order details or normal order payments.
- `3cffdec` adds Entity-free UMS AMOUNT-right/tier/balance-payment contracts, specialized purchase/pickup/refund routes
  under `pos:cashier`, frozen-tier-price mixed supplement orchestration, native pickup/return inventory commands and
  full-pickup refund reversal.
- New isolated integration coverage asserts package-purchase idempotency, 100-right + 50-scanned supplement for a
  150-price item, no product order creation, full refund restoration, stock rejection and the route-permission contract.

## Validation actually performed

- `mvn -q -pl qk-money-app/money-app-biz -am test-compile -DskipTests` passed.
- Isolated `money_pos_test`: `MemberAmountBenefitServiceIntegrationTest` (4/0/0),
  `CheckoutIntegrationTest`, and `PosDeferredQuantityRouteContractTest` passed.
- Isolated full `mvn -q test` passed: 65 test classes and 125 tests, zero failures/errors.
- `mvn -q package -DskipTests`, `bash scripts/test-architecture-scan.sh`, and
  `bash scripts/architecture-scan.sh --check-new` passed.
- `git diff --check` passed before `3cffdec`.

## Blockers and decisions

There is no unresolved business or architecture decision. Frozen AMOUNT rule: package purchase and supplement use
non-product receipts; an AMOUNT pickup prices from its right's frozen tier snapshot, deducts the right without new
income, and records only cash/scan/balance supplement as new income. V1 refunds only a complete pickup: restore the
right, reverse the supplement and use `MEMBER_PICKUP_RETURN` for physical stock.

## Exact next action

ME-1.3 has no active implementation action. Verify `git status --short --branch` after this final handoff record is
pushed; it must show no changes and no ahead/behind before selecting a future accepted phase.

## Handoff rule

Before ending a substantive session, update this file with the actual working-tree state and validations. Before a
cross-computer handoff, commit and push first; then verify `dev...origin/dev` has neither `ahead` nor `behind`.
