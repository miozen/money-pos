# MoneyPOS Architecture Adjustment Implementation Ledger

## Purpose

This ledger is the execution baseline for the minimal MoneyPOS architecture adjustment. It records the plan, implementation status, verification evidence, risks, and the next action after each completed stage.

The adjustment must preserve existing product behavior, public HTTP APIs, database schema, Flyway history, transaction ownership, and Electron startup behavior. It must not introduce new business functions, microservices, a payment SPI, a database abstraction layer, or a platform upgrade.

## Progress Summary

| Stage | Objective | Status | Completion evidence | Next action |
| --- | --- | --- | --- | --- |
| 0 | Establish settlement and refund regression protection | Complete | 10 direct MariaDB integration tests cover cash, balance, coupon, mixed payment, full and partial refund, member-asset restoration, and rollback | Start stage 2 dependency inventory |
| 1 | Govern cross-domain dependencies | Complete | Static scan, compile, and stage 0 integration suite validate facade-mediated core paths | Start stage 2 dependency inventory |
| 2 | Organize logical business package boundaries | In progress | Checkout, transaction boundary services, order support, order query, and refund application services are under the TRADE logical boundary; the sales-analysis service, metric assembler, profit-analysis service, and risk-analysis service are under FIN; regression suite remains green | Assess the remaining OMS order-service dependency surface |
| 3 | Organize runtime platform capabilities | Not started | - | Evaluate after stage 2 |
| 4 | Prevent architectural regression | Not started | - | Add minimal ArchUnit rules after package boundaries stabilize |
| 5 | Evaluate physical Maven module split | Not started | - | Assessment only; no split is planned now |

## Stage 0: Business Regression Protection

### Goal

Protect the existing strong-consistency paths before further architecture work: settlement, inventory, member assets, payment records, and refunds.

### Planned changes

- Add test-scope Spring Boot testing dependencies to `money-app-biz`.
- Add `application-test.yml` with a standalone MariaDB test database configuration.
- Use a database name ending in `_test`; read connection data from environment variables and fail fast when the configured database name is not a test database.
- Let Flyway initialize the test schema.
- Add transaction integration tests that invoke the settlement orchestrator and refund service directly.
- Provide reusable test fixtures for members, goods, stock, prices, coupons, and payment methods.

### Completed: Test infrastructure

- Added test-scope `spring-boot-starter-test` to `money-app-biz`.
- Added `application-test.yml`; its datasource credentials must be supplied with `MONEY_TEST_DB_*` environment variables.
- Added a test-only startup guard that rejects a database name not ending in `_test` and a JDBC URL that does not target that name.
- Retained Flyway initialization for the test profile.
- Verified with `mvn -pl qk-money-app/money-app-biz -am test-compile -DskipTests -q`.
- Verified the full Spring test context against `money_pos_test`: 1 test passed, 43 tables and 5 Flyway history rows created.

### Completed: Settlement transaction verification

- Added a reusable `TradeFixture` for a sellable good and a cash settlement request.
- Added direct integration tests for a normal cash settlement and an insufficient-payment failure.
- The normal scenario verifies the order, payment record, stock deduction, and sale-out inventory document.
- The failure scenario verifies that order writes and stock changes roll back together.
- Verified with `mvn -pl qk-money-app/money-app-biz -am test -Dtest=CheckoutIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -q`: 2 tests passed against `money_pos_test`; each test transaction rolled back after assertion.

### Completed: Refund transaction verification

- Added direct integration tests that create an order through the checkout orchestrator, then invoke the existing full-refund and partial-refund service entries.
- The full-refund scenario verifies `REFUNDED` state, complete line return quantity, and complete stock restoration.
- The partial-refund scenario verifies `PARTIAL_REFUNDED` state, partial line return quantity, and restoration of only the returned stock.
- Verified with `mvn -pl qk-money-app/money-app-biz -am test -Dtest=CheckoutIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -q`: 4 tests passed against `money_pos_test`; each test transaction rolled back after assertion.

### Completed: Member balance transaction verification

- Added a reusable member fixture and a balance-payment settlement request.
- The balance-payment scenario verifies member balance deduction, accumulated consumption amount and count, and the persisted `BALANCE` payment record.
- The insufficient-balance scenario runs outside the test wrapper transaction to verify the real service transaction rollback: no order, stock change, balance deduction, or member-consumption write remains.
- Verified with `mvn -pl qk-money-app/money-app-biz -am test -Dtest=CheckoutIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -q`: 6 tests passed against `money_pos_test`.
- Residual risk recorded: sale-out documents prepend `XS-` to the request ID while `gms_inventory_doc.doc_no` is `varchar(32)`; request IDs longer than 29 characters can fail after order creation. This behavior is not changed in the current architecture adjustment.

### Completed: Coupon and mixed-payment verification

- Added coupon-rule and issued-member-coupon fixtures.
- The coupon scenario verifies threshold deduction, order voucher amount, final payment amount, and member coupon transition from `UNUSED` to `USED`.
- The mixed-payment scenario verifies two payment records and that only the `BALANCE` portion reduces the member balance.
- Verified with `mvn -pl qk-money-app/money-app-biz -am test -Dtest=CheckoutIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -q`: 10 tests passed against `money_pos_test`.
- Added full-refund coverage for member balance restoration, consumption-statistics restoration, and used-voucher restoration to `UNUSED`.

### Stage 0 completion

The isolated test profile prevents production and development database targeting, and the core settlement and refund scenarios now run against MariaDB. Stage 0 is complete.

### Next unit

Start stage 2 with a dependency inventory and a package-move map; do not move code until the target boundary and compatibility bridges are explicit.

### Required automated scenarios

1. Standard settlement and order persistence.
2. Member settlement and member asset consumption.
3. Balance payment and insufficient-balance rollback.
4. Coupon or voucher settlement behavior.
5. Mixed payment and change normalization.
6. Full-order refund.
7. Partial refund.
8. Settlement or refund failure rolls back order, stock, payment, and member-asset writes together.

### Manual acceptance scenarios

- Electron starts the Java backend and opens the POS and customer-display windows.
- Barcode search, settlement dialog, and 401 POS lock recovery work as before.
- Receipt, cash-drawer, shift-report, and installation-package startup/shutdown behavior remain available.

### Completion criteria

- The integration test suite runs against an isolated MariaDB test database.
- Core settlement and refund scenarios pass.
- No production or development database can be targeted by the test profile.

## Stage 1: Cross-Domain Dependency Governance

### Goal

Remove direct trading-domain dependencies on GMS and UMS internal `Mapper` and `ServiceImpl` classes while retaining the existing single transaction orchestration.

### Implemented scope

- Added `PosPricingFacade` for POS trial calculation.
- Added `GoodsStockFacade` for sales deductions, refund restoration, stock logs, and inventory documents.
- Added `MemberAssetFacade` for settlement asset consumption, voucher restoration, and balance refunds.
- Added scenario DTOs under `com.money.service.facade.dto`.
- Updated POS controller, checkout services, and refund helpers to depend on the facades.
- Kept OMS order-detail atomic refund updates and OMS payment-record reads in the trading side.
- Kept transaction annotations and transaction ownership unchanged.

### Current verification

- `mvn -pl qk-money-app/money-app-biz -am compile -DskipTests -q` passed.
- Static dependency scan confirms that checkout/refund services and `PosController` no longer directly reference the targeted GMS/UMS internal mappers, services, or `PosCalculationEngine`.

### Stage 1 completion

- The stage 0 suite exercises price trial, sale and refund stock handling, member balance consumption and restoration, and voucher consumption and restoration through the new facades.
- Static boundary scan and compilation remain clean.
- Stage 1 is complete for the core transaction paths; the suite is the regression baseline for subsequent stages.

## Stage 2: Logical Business Boundaries

Do not start before stages 0 and 1 are verified.

- Keep `money-app-system` as the SYS boundary.
- Gradually organize business code under logical `feature.gms`, `feature.ums`, `feature.trade`, `feature.fin`, and `feature.home` packages.
- Do not move shared entity/DTO types wholesale. New cross-boundary input and output must use scenario DTOs.
- Do not change controllers, URLs, database table names, or Flyway scripts.

## Stage 3: Runtime Capability Boundaries

Do not start before stage 2 is stable.

- Candidate early moves: workspace initialization, MariaDB guardian, and local file capability.
- Keep backup, printer, and POS WebSocket business semantics in place until their technical and business portions can be separated safely.
- Do not change Electron `main.cjs` in this stage.

## Stage 4: Regression Prevention

After package boundaries stabilize, add minimal enforceable rules:

- Controllers do not depend directly on mappers.
- A feature does not depend on another feature's `ServiceImpl` or mapper.
- Cross-feature calls do not use another feature's internal entity as a contract.
- `platform` does not depend on `feature`.

Add the rules with a short architecture guide for future changes.

## Stage 5: Maven Module Split Assessment

Physical Maven splitting is an assessment gate, not an implementation commitment. Consider it only when facade boundaries are stable, no cyclic dependency remains, and independent compilation/testing offers proven value. GMS, UMS, and TRADE are the only initial candidates.

## P1: Cross-Feature Contract Convergence

P1 follows the Stage 4 ownership plan and Stage 5 deferral decision. It incrementally replaces scenario-specific cross-Feature Entity, Mapper and `IService<Entity>` usage with neutral read snapshots and command/query interfaces. It is not a physical Maven split and must preserve the single-process transaction model.

### Completed: P1.1 Neutral Member Coupon Count Contract

- Added `MemberCouponCountQuery` under the existing `money-app-api` neutral contract package and moved the UMS member-asset export dependency to it. TRADE remains the implementation owner and retains `PosMemberCouponMapper`; UMS now has no source import of TRADE for this read.
- The contract returns only a member-ID-to-`UNUSED`-coupon-count map. It contains no Spring, MyBatis or Entity type and introduces no POM/module split. `UmsMemberAssetExcelExportServiceIntegrationTest` continues to verify the exported count excludes `USED` coupons.
- Published `MoneyPOS-P1-Contract-Convergence-Checklist.md`. The next smallest task is a field-level design for the checkout member-validation snapshot; settlement and refund writes remain explicitly out of scope until their transaction characteristics are isolated and tested.

### Completed: P1.2 Checkout Member Verification Snapshot

- Added the neutral `MemberCheckoutSnapshot` and `MemberCheckoutQuery` contracts under `money-app-api`; the snapshot carries only member ID, name, and phone required by checkout.
- UMS owns the query implementation and its `UmsMemberMapper`; TRADE checkout validation no longer calls `UmsMemberService.getById()` or stores `UmsMember` in `CheckoutContext`.
- Order creation and member-asset processing consume the snapshot, preserving the existing checkout transaction and member-order archive behavior.

### Completed: P1.3 Order Profile and POS Member Search Snapshots

- Added separate neutral contracts for order-member profiles and POS member entitlement search. Both expose only the fields their consuming UI requires.
- UMS now owns member and member-brand-level reads for these contracts. TRADE order queries and POS search no longer depend on `UmsMemberService` or `UmsMemberBrandLevelMapper`.
- Existing REST response shapes remain compatible: order details still expose `memberInfo`, and POS search still returns `PosMemberVO` with balances, coupon summaries and brand-level maps.

### In progress: P1.4a Checkout Goods Read Snapshot

- Added `CheckoutGoodsSnapshot` and `CheckoutGoodsQuery` to express exactly the GMS data required by checkout: product archive fields, category name, stock availability, combo flag, and per-level price/coupon matrices.
- GMS owns snapshot assembly through its product, category and price services. Checkout validation, pricing, order creation and stock-command preparation no longer read `GmsGoodsService`, `GmsGoods`, category services, or price Mapper types.
- Inventory mutation remains in the existing `GoodsStockFacade` command boundary and retains its transaction/locking order; it is intentionally deferred from this read-only slice.

### Completed: P1.4b POS Goods Catalog Snapshot

- Added `PosGoodsCatalogSnapshot` and `PosGoodsCatalogQuery`; GMS now owns POS product keyword search and level-price/coupon matrix assembly.
- TRADE `PosService` converts this narrow snapshot into the unchanged `PosGoodsVO` response and no longer reads the GMS goods service, goods Entity, or level-price Mapper directly.

### Completed: P1.5.1 Member Asset Write-Side Inventory

- Published `MoneyPOS-P1.5-Member-Asset-Command-Checklist.md`, recording the remaining TRADE-owned direct writes for member consumption, vouchers, balance, logs and visit time.
- The baseline preserves existing settlement/refund behavior and identifies two missing feature tests before commands are migrated: partial refunds after balance payment and voucher conditional-update rollback.

### Completed: Stage 5 Maven-Reactor Baseline

- Published `MoneyPOS-Stage-5-Maven-Module-Split-Assessment-Checklist.md`. The current reactor has only `money-app-api`, `money-app-system` and one business module, `money-app-biz`; GMS (36 source files), UMS (14) and TRADE (43) are logical packages inside that business module, not separately compilable Maven units.
- Source dependency scanning found UMS→GMS in three files, UMS→TRADE in one, TRADE→GMS in five and TRADE→UMS in five. The UMS↔TRADE cycle makes an immediate physical split invalid. GMS is currently acyclic relative to the other three candidates, but its shared API/entity/mapper dependencies mean it also has no approved physical split yet.
- No POM or source package was changed. The next smallest task is a file-level UMS↔TRADE cycle inventory and a narrow, non-cyclic contract design; only after that can an independent compilation benefit be evaluated.

### Completed: Stage 5 UMS-TRADE Cycle Inventory

- Published `MoneyPOS-Stage5-Ums-Trade-Cycle-Inventory.md`. It classifies the reverse UMS→TRADE coupon-count read, three TRADE→UMS reads, the POS member aggregate, and settlement/refund write coordination. It also records TRADE's direct UMS Entity/Mapper/IService usages, which the Stage 4 structural scan intentionally does not classify as a cross-Feature implementation import but which block physical module separation.
- The proposed dependency shape is a future, neutral contract module containing only Entity-free DTOs and ports. UMS would implement member read/write ports; TRADE would implement coupon count; the Boot composition module wires them. This breaks Maven direction without creating a UMS↔TRADE reactor cycle.
- No POM, package or behavior changed. The lowest-risk eventual implementation is to relocate the existing Entity-free coupon-count contract; it is not performed during this assessment. Next, evaluate the GMS one-way candidate and define the contract module's minimal dependency set before deciding whether any independent compilation is valuable.

### Completed: Stage 5 GMS Candidate Assessment

- Published `MoneyPOS-Stage5-Gms-Module-Candidate-Assessment.md`. GMS has no direct UMS or TRADE Feature imports and is therefore the only directionally acyclic initial candidate. It contains 36 source files and four current integration tests; UMS, TRADE, HOME and legacy compatibility entry points consume GMS services.
- The assessment does not approve a GMS POM split: GMS interfaces still expose `IService<Entity>`, 22 files import shared Entity types, 11 import shared Mappers, several flows use SYS configuration/dictionary capabilities, and the tests start the full Boot application. A physical move would not yet demonstrate independently useful compilation or testing.
- Defined the future contract module as dependency-minimal: JDK-only Entity-free DTOs and ports, never Spring services, Mappers, Entity, Flyway or Feature implementations. No POM or source changed. Next, consolidate the target graph and issue a split/defer decision with concrete entry criteria.

### Completed: Stage 5 Maven Module-Split Decision

- Published `MoneyPOS-Stage5-Maven-Module-Split-Decision.md`, which records the current three-module reactor, the UMS↔TRADE cycle, the future acyclic target shape and a five-condition re-entry checklist. GMS is directionally acyclic but still fails independent-build and stable-contract gates.
- The decision is to defer physical splitting. No new POM, source directory, Spring scan rule, dependency or build command was added. This avoids an invalid cyclic reactor and avoids falsely treating full Boot integration tests as independent Feature-module verification.
- The remaining Stage 5 action is documentary closure: record the evidence for deferral, preserve the Stage 4 gate as the ongoing safeguard, and state that future contract extraction requires a separately authorized implementation task.

### Completed: Stage 5 Assessment Closure

- Closed the Stage 5 checklist after confirming no candidate meets all four physical-split gates: stable Entity-free contracts, an acyclic Maven graph, proven independent compilation value and verified Spring/Mapper/Flyway/transaction assembly. The only correct current action is to retain the existing `money-app-api` / `money-app-system` / `money-app-biz` reactor.
- The deferral is evidence-based, not an unbounded postponement: `MoneyPOS-Stage5-Maven-Module-Split-Decision.md` records the current and target graphs plus the five prerequisites that must be completed before reopening the assessment. No POM, source package, route, table, migration, configuration or test setup was changed during Stage 5.
- `architecture-scan.sh --check-new` remains the ongoing local safeguard while development continues in the single business module. Any future contract extraction or physical module split needs separate authorization and its own implementation/verification plan.

## Execution Rule

After every completed unit of work, update this ledger with:

1. Status and completion evidence.
2. Changed files and boundary impact.
3. Test or verification result.
4. Known residual risk.
5. The immediate next action.

## Stage 2: Dependency Inventory and Package-Move Map

### Completed: Inventory

- The current business module has 27 controllers, 35 service interfaces, 34 mappers, and 31 service implementations directly under shared technical packages.
- GMS ownership is identifiable by `Gms*` controllers, services, mappers, inventory, stock, goods, brand, category, turnover, and analysis classes.
- UMS ownership is identifiable by `Ums*` controllers, services, mappers, member assets, logs, imports, recharge, and member-profile classes.
- TRADE ownership is identifiable by `Oms*`, `Pos*`, `checkout`, and checkout `facade` classes; it is the only intentionally cross-domain transaction coordinator.
- FIN ownership is identifiable by `Finance*`, `DecisionEngineServiceImpl`, and finance assemblers; it reads trade and member data for reporting.
- HOME ownership is `HomeController` and `HomeServiceImpl`; it is a read-model/dashboard feature.
- SYS remains outside this stage. `config`, `workspace`, `printer`, `websocket`, and backup capability stay in their current locations until stage 3 separates runtime capabilities.

### Boundary Rules for This Stage

- Preserve all controller package names, HTTP routes, request/response DTO packages, entities, mappers, table names, and Flyway scripts.
- Preserve Spring bean names and transaction annotations unless an explicit compatibility bridge is added.
- Treat `checkout` as the TRADE application layer; it may call only the stage 1 facades for GMS and UMS write-side work.
- Keep finance and home read-side dependencies explicit. They may read trade data but must not become transaction orchestrators.
- Do not move `ServiceImpl` classes across a feature boundary while another feature injects them directly; introduce or reuse a feature-facing interface first.

### Initial Move Map

| Target logical boundary | Candidate code | First action | Deferred because |
| --- | --- | --- | --- |
| `feature.trade` | `checkout`, `facade`, `Oms*`, `Pos*` | Start with internal package organization of checkout coordination only | POS pricing and asset helpers span GMS and UMS contracts |
| `feature.gms` | `Gms*` goods, inventory, stock, brand, category, turnover | Inventory only | Some POS and finance paths still inject GMS services directly |
| `feature.ums` | `Ums*` member, asset, log, import, recharge | Inventory only | POS asset actions and finance read models need stable contracts |
| `feature.fin` | `Finance*`, `DecisionEngineServiceImpl`, finance assemblers | Inventory only | Finance currently reads OMS/UMS/GMS mappers directly |
| `feature.home` | `HomeController`, `HomeServiceImpl` | Inventory only | Dashboard reads cross-feature data directly |

### Next Unit

Create a narrow `feature.trade` compatibility package for the checkout application layer only, retaining bridge types in their current package if required. Before moving code, enumerate every direct import and Spring injection of `CheckoutOrchestrator`, checkout services, and facades.

### Completed: Trade Compatibility Scan

- `CheckoutOrchestrator` has one production caller: `PosServiceImpl`; its public API is the `orchestrate(SettleAccountsDTO)` method.
- `OmsOrderRefundServiceImpl` depends on `RefundStateGuard`, `RefundInventoryHelper`, and `RefundAssetHelper`; those helpers form the refund application layer and must move together if their package changes.
- `PosController` depends on the POS pricing facade, not on checkout internals.
- The test suite directly references `CheckoutOrchestrator`; its package must be updated in the same change as the production move.
- No other production classes import the checkout package. This is a narrow enough first move without public HTTP, entity, mapper, or database-contract changes.

### Next Unit: First Logical Package Move

Move the checkout application layer to `com.money.feature.trade.application.checkout`, including its refund helpers. Update the single `PosServiceImpl` import and the integration-test package/imports. Do not rename Spring components, change public method signatures, move facades, or move OMS services in this unit.

### Completed: First Logical Package Move

- Moved the checkout application layer and its refund helpers from `com.money.service.checkout` to `com.money.feature.trade.application.checkout`.
- Updated the only production checkout caller, `PosServiceImpl`, and the refund-service helper imports.
- Moved the integration test to the corresponding trade application package.
- Preserved all class names, Spring annotations, bean names, transaction annotations, public methods, HTTP routes, entity packages, mapper packages, and Flyway scripts.
- Verified with `mvn -pl qk-money-app/money-app-biz -am test-compile -DskipTests -q`.
- Verified with `mvn -pl qk-money-app/money-app-biz -am test -Dtest=CheckoutIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -q`: 10 tests passed against `money_pos_test`.
- Verified no source or test code still imports `com.money.service.checkout`.

### Next Unit

Inventory direct callers of `PosPricingFacade`, `GoodsStockFacade`, and `MemberAssetFacade`, then decide whether these transaction boundary services should move under `feature.trade` together or remain in a neutral compatibility location for the duration of stage 2.

### Completed: Trade Boundary Service Move

- Moved `PosPricingFacade`, `GoodsStockFacade`, `MemberAssetFacade`, and their scenario DTOs to `com.money.feature.trade.application.boundary.facade`.
- Moved `PosCalculationEngine`, `PosInventoryActionService`, and `PosAssetActionService` to `com.money.feature.trade.application.support` because they have no callers outside the moved boundary services.
- Updated checkout, refund helpers, and `PosController` imports; no public method, Spring annotation, bean name, URL, entity, mapper, or database contract changed.
- Verified with `mvn -pl qk-money-app/money-app-biz -am test-compile -DskipTests -q`.
- Verified with `mvn -pl qk-money-app/money-app-biz -am test -Dtest=CheckoutIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -q`: 10 tests passed against `money_pos_test`.
- Verified no source or test code still imports the former facade or moved `Pos*` support packages.

### Next Unit

Inventory OMS service interfaces and implementations to identify the first trade-owned interface/implementation pair that can move without forcing controller, mapper, entity, or public API changes. Keep GMS, UMS, FIN, HOME, and SYS code in place for now.

### Completed: Trade Order Support Service Move

- Moved `OmsOrderDetailService`, `OmsOrderDetailServiceImpl`, `OmsOrderLogService`, and `OmsOrderLogServiceImpl` to `com.money.feature.trade.domain.order`.
- Updated checkout, order-query, and refund-service imports only; controllers do not directly depend on either moved service.
- Preserved MyBatis-Plus service inheritance, all method signatures, Spring annotations, entities, mappers, and database contracts.
- Verified with `mvn -pl qk-money-app/money-app-biz -am test-compile -DskipTests -q`.
- Verified with `mvn -pl qk-money-app/money-app-biz -am test -Dtest=CheckoutIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -q`: 10 tests passed against `money_pos_test`.
- Verified no source or test code still imports the former order-detail or order-log service packages.

### Next Unit

Inventory `OmsOrderRefundService` and `OmsOrderRefundServiceImpl` callers, then move the refund application-service pair only if its controller and integration-test references are the complete compatibility surface.

### Completed: Refund Application Service Move

- Confirmed the complete direct compatibility surface before moving: `OmsOrderController`, `CheckoutIntegrationTest`, and the implementation itself. The `OmsOrderService` occurrence is documentation only, not a type dependency.
- Moved `OmsOrderRefundService`, `OmsOrderRefundServiceImpl`, `RefundStateGuard`, `RefundInventoryHelper`, and `RefundAssetHelper` to `com.money.feature.trade.application.refund`.
- Updated only the controller, integration-test, and internal order-log imports required by the new package. HTTP routes, request and response DTOs, public methods, Spring annotations and bean names, transaction ownership, entities, mappers, and database contracts remain unchanged.
- Verified with `mvn -pl qk-money-app/money-app-biz -am test-compile -DskipTests -q`.
- Verified with `mvn -pl qk-money-app/money-app-biz -am test -Dtest=CheckoutIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -q`: 10 tests passed against `money_pos_test`.
- Verified no source or test code still imports the former `OmsOrderRefundService` or refund-helper packages.

### Next Unit

Inventory `OmsOrderService` and `OmsOrderServiceImpl` separately. Move neither by default: first distinguish their controller-facing query and management compatibility surface from their transaction dependencies, and keep FIN read-side code in place until that boundary can be made explicit without widening this adjustment.

### Completed: Order Query Application Service Move

- Confirmed the complete direct compatibility surface before moving: `OmsOrderController` is the only direct caller; there are no XML, reflection, qualifier, or Bean-name references.
- Moved `OmsOrderService` and `OmsOrderServiceImpl` to `com.money.feature.trade.application.orderquery`. The service remains a read-model assembler for the order list, order detail, receipt lookup, member-detail enrichment, status-description lookup, order logs, and payment summaries.
- Updated only the controller import. Class names, Spring annotations and generated bean name, endpoint routes, public methods, DTOs, entities, mappers, and transaction behavior remain unchanged.
- Kept the existing direct reads from `UmsMemberService` and `SysDictDetailService` unchanged. They are query-time enrichments, outside the settlement and refund transaction path, and require a later explicit read-model contract before further dependency governance.
- Verified with `mvn -pl qk-money-app/money-app-biz -am test-compile -DskipTests -q`.
- Verified with `mvn -pl qk-money-app/money-app-biz -am test -Dtest=CheckoutIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -q`: 10 tests passed against `money_pos_test`.
- Verified no source or test code still imports the former `OmsOrderService` package.

### Next Unit

Inventory `OmsSalesAnalysisService` and `OmsSalesAnalysisServiceImpl` as a reporting/read-side candidate. Do not move it merely because the controller is named OMS: first identify whether its mapper dependencies and output DTOs establish FIN ownership, and keep its HTTP compatibility surface unchanged.

### Completed: FIN Sales Analysis Application Service Move

- Confirmed `OmsSalesAnalysisService` is a FIN read-side capability despite its OMS name: it composes dashboards, performance reports, marketing ROI, traffic, category sales, goods trends, and profit-audit views without executing writes or transaction orchestration.
- Confirmed the complete direct compatibility surface: `OmsOrderController` and `OmsSalesAnalysisController`; there are no XML, reflection, qualifier, or Bean-name references.
- Confirmed `OmsOrderAnalysisMapper`, `OmsOrderTrafficMapper`, and `OmsOrderAuditMapper` expose `@Select` queries only and are shared with other FIN services, so they remain in the shared mapper package.
- Moved `OmsSalesAnalysisService`, `OmsSalesAnalysisServiceImpl`, and its sole caller-owned `FinanceMetricAssembler` to `com.money.feature.fin.application.analysis`. Updated only the two controller imports.
- Preserved class names, Spring annotations and generated bean names, endpoint routes, public methods, DTOs, Mapper interfaces, SQL, and database contracts.
- Verified with `mvn -pl qk-money-app/money-app-biz -am test-compile -DskipTests -q`.
- Verified with `mvn -pl qk-money-app/money-app-biz -am test -Dtest=CheckoutIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -q`: 10 tests passed against `money_pos_test`; the full Spring context successfully scanned and injected the moved FIN components.
- Residual test gap: the current suite does not assert report aggregation values or HTTP response payloads. This is deliberately recorded rather than inferred from the settlement/refund tests.
- Verified no source or test code still imports the former analysis-service or metric-assembler packages.

### Next Unit

Inventory `FinanceProfitService`, `FinanceRiskService`, and `DecisionEngineService` together. Move only a service-plus-helper group whose callers and shared Mapper dependencies form a complete compatibility surface; do not move shared analysis Mappers or OMS DTOs in that unit.

### Completed: FIN Profit Analysis Service Move

- Confirmed the complete direct compatibility surface: `FinanceController` is the only direct caller; there are no XML, reflection, qualifier, or Bean-name references.
- Confirmed the service is read-side only. Its calls are limited to `OmsOrderDetailMapper.getProfitRankingData` and `OmsOrderAnalysisMapper.getMarketingRoiStats`, both `@Select` operations; shared Mappers and FIN DTOs remain in their existing packages.
- Moved `FinanceProfitService` and `FinanceProfitServiceImpl` to `com.money.feature.fin.application.analysis`, updating only the `FinanceController` import.
- Preserved class names, Spring annotations and generated bean name, endpoint routes, public methods, DTOs, Mapper interfaces, SQL, and database contracts.
- Verified with `mvn -pl qk-money-app/money-app-biz -am test-compile -DskipTests -q`.
- Verified with `mvn -pl qk-money-app/money-app-biz -am test -Dtest=CheckoutIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -q`: 10 tests passed against `money_pos_test`; the Spring context successfully scanned and injected the moved FIN service.
- Residual test gap: no direct assertion currently covers profit-ranking or campaign-review values.
- Verified no source or test code still imports the former profit-service packages.

### Next Unit

Inventory `FinanceRiskService` and `FinanceRiskServiceImpl` as the next isolated FIN read-side candidate. Defer `DecisionEngineService`: it writes daily snapshots and directly reads GMS and UMS data, so moving it needs a separate transaction and dependency assessment.

### Completed: FIN Risk Analysis Service Move

- Confirmed the complete direct compatibility surface: `FinanceController` is the only direct caller; there are no XML, reflection, qualifier, or Bean-name references.
- Confirmed the service is read-side only. It calls `OmsOrderAuditMapper.getCashierRiskSummary` and `OmsOrderAuditMapper.getAbnormalOrderList`, both `@Select` operations, to aggregate risk-card values and lists. The shared audit Mapper remains in its existing package.
- Moved `FinanceRiskService` and `FinanceRiskServiceImpl` to `com.money.feature.fin.application.analysis`, updating only the `FinanceController` import.
- Preserved class names, Spring annotations and generated bean name, endpoint routes, public methods, Mapper interfaces, SQL, and database contracts.
- Verified with `mvn -pl qk-money-app/money-app-biz -am test-compile -DskipTests -q`.
- Verified with `mvn -pl qk-money-app/money-app-biz -am test -Dtest=CheckoutIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -q`: 10 tests passed against `money_pos_test`; the Spring context successfully scanned and injected the moved FIN service.
- Residual test gap: no direct assertion currently covers risk-summary values, null Mapper values, or HTTP response payloads.
- Verified no source or test code still imports the former risk-service packages.

### Next Unit

Perform a no-change dependency assessment of `DecisionEngineService` and `DecisionEngineServiceImpl`. Its snapshot writes, direct `JdbcTemplate` SQL, GMS inventory read, UMS member query, OMS daily-summary persistence, and Home-controller API require a separate compatibility and transaction analysis before any package movement.

### Completed: Decision Engine Dependency and Transaction Assessment (No Code Move)

- Confirmed the only direct application caller is `HomeController` through `GET /home/count`; no XML, reflection, qualifier, Bean-name, test, or scheduled-task caller was found.
- Confirmed that this GET path is write-capable: `getComprehensiveDashboard` invokes `getTodayDashboardWithAlerts`, which compensates up to seven missing daily snapshots and regenerates today's snapshot. `generateDailySnapshot` inserts or updates `oms_daily_summary`.
- The engine crosses multiple boundaries in one request: OMS analysis and daily-summary Mappers, direct `JdbcTemplate` reads of UMS member data, GMS stock-value service calls, OMS daily-summary persistence, and Home dashboard output assembly.
- No `@Transactional` or transaction-template boundary exists on the service or interface. Current behavior therefore allows a snapshot write to complete before a later read or write fails; this adjustment does not change that behavior.
- `oms_daily_summary.record_date` has unique index `uk_record_date`. The existing select-then-insert path can race under concurrent `/home/count` requests and surface a duplicate-key failure. This is a pre-existing risk; no schema or concurrency behavior changed.
- Decision: do not move the service in stage 2. Moving it as a package-only change would conceal rather than reduce its cross-domain coupling and write-side risk.
- Verification: assessment-only unit; no production code changed and no test run was required.

### Next Unit

Add a narrow characterization test for the existing `GET /home/count` / decision-engine snapshot behavior using the isolated test database, including first-call snapshot creation and same-date update behavior. Do not add locking, an upsert, a transaction boundary, or package movement in that test unit.

## Stage 2: Actual Progress and Completion Checklist

Stage 2 is **in progress**, not near completion. The earlier completed entries are narrow, tested migrations; they do not satisfy the original feature-wide package-move plan by themselves.

| Planned ownership slice | Current status | Evidence / remaining scope |
| --- | --- | --- |
| `feature.trade` | Partial | Checkout, refund, order query, order detail/log support, transaction facades, and three support services moved. `PosService`, 3 Pos controllers, 2 OMS controllers, 4 Pos Mappers, and 9 OMS Mappers remain in legacy packages. |
| `feature.fin` | Complete | Sales, profit, risk, dashboard, report, and shift analysis services; both FIN controllers; the dedicated mapper; and FIN-only assemblers now have FIN logical packages. Shared OMS/GMS/UMS read Mappers remain compatible shared infrastructure. |
| `feature.gms` | Not started | 9 controllers, 11 service interfaces, 8 implementations, and 12 Mappers remain in legacy packages. |
| `feature.ums` | Not started | 5 controllers, 6 service interfaces, 2 implementations, and 4 Mappers remain in legacy packages. |
| `feature.home` | Not started | `HomeController`, `HomeService`, and the decision engine remain in legacy packages. The decision engine assessment explicitly deferred its move because the endpoint performs snapshot writes across OMS, UMS, and GMS. |
| SYS | Out of scope | `money-app-system` remains unchanged, as planned. |
| DTO / Entity | On-plan | No wholesale migration has been made; new TRADE cross-boundary inputs use scenario DTOs while existing shared DTOs and entities remain compatible. |

### What Is Complete

- Stage 0 is complete: an isolated MariaDB test profile and 10 settlement/refund integration scenarios protect the core transaction path.
- Stage 1 is complete for the settlement/refund path: TRADE calls GMS and UMS write-side behavior through the three facades rather than targeted internal mapper or implementation dependencies.
- In stage 2, the TRADE settlement/refund application slice and selected FIN read-side services have moved without changing routes, public method signatures, database contracts, or Flyway history.
- The decision engine has been assessed and deliberately left in place; its existing write-on-read and concurrency risks are documented rather than hidden by a package-only move.

### What Remains Before Stage 2 Can Be Complete

1. Complete a feature-at-a-time migration for FIN: inventory and move the remaining finance dashboard, report, shift, controller, and FIN-owned persistence types only after their callers are mapped.
2. Complete the HOME feature boundary, including `HomeController`, `HomeService`, and the decision engine only after a snapshot characterization test establishes current write behavior.
3. Complete GMS and UMS feature boundaries in separately tested slices. Their controllers, services, implementations, and Mappers are the largest unstarted scope. Facades remain the TRADE write-side boundary.
4. Complete the remaining TRADE presentation and persistence ownership moves, including Pos/OMS controllers and Mappers, while preserving `@MapperScan` discovery and public endpoints.
5. Run the stage completion verification: `mvn test`, `mvn package`, stage 0 integration suite, Spring Bean/Mapper discovery checks, and manual POS settlement/refund acceptance.
6. Restore the planned rollback discipline: no feature-specific Git commits have been created for the completed uncommitted work yet. Before the next broad feature move, organize commits by completed feature slice without including unrelated user changes.

### Next Unit

Do not start another arbitrary leaf-service move. First inventory the remaining FIN dashboard, report, shift, controller, and Mapper types as one feature-completion candidate, and define a single independently testable, separately committable FIN slice.

### Completed: Stage 2 Baseline and Execution Checklist

- Created `MoneyPOS-Stage-2-Feature-Checklist.md` with explicit feature ownership, migration, verification, completion, and rollback gates for FIN, HOME, TRADE, GMS, and UMS.
- Re-audited the pre-existing uncommitted architecture-adjustment scope and reran `test-compile` plus `CheckoutIntegrationTest`: 10 tests passed with 0 failures and 0 errors against `money_pos_test`.
- Established a single corrective baseline commit for the already-verified stage 0, stage 1, and partial stage 2 work. It is intentionally a baseline, not a retrospective claim of feature-by-feature history.
- From the next slice onward, only one feature may change per commit; the ledger will record the commit identifier and rollback scope.

### Next Unit

Inventory the remaining FIN dashboard, report, shift, controller, and Mapper types as the first feature-completion candidate. Do not move code until the FIN compatibility surface and a single independently testable commit boundary are explicit.

### Completed: FIN Feature Completion Slice

- Completed the remaining FIN production-type migration as one feature slice: `FinanceDashboardService`, `FinanceReportService`, `FinanceShiftService`, their implementations, `FinanceDashboardAssembler`, `FinanceController`, `FinanceReportController`, and the FIN-only `FinanceReportMapper`.
- Placed application services under `feature.fin.application.dashboard` and `feature.fin.application.report`, controllers under `feature.fin.interfaces.rest`, and the dedicated Mapper under `feature.fin.infrastructure.persistence.mapper`.
- Updated `MybatisConfig` to scan both the legacy shared Mapper package and the new FIN Mapper package; Spring context startup and FIN queries confirm the moved Mapper is discovered.
- Preserved controller class names, generated Spring bean names, request routes, method signatures, DTOs, SQL, tables, and Flyway scripts. Shared OMS/GMS/UMS Mappers remain in the compatible shared package because other features still use them.
- Added `FinanceFeatureIntegrationTest`; it establishes tenant context and executes dashboard, channel mix, asset dashboard, shift handover, and the report service null-input guard through the moved FIN components.
- Verification: `FinanceFeatureIntegrationTest` passed (1 test); `CheckoutIntegrationTest` passed (10 tests); `test-compile` passed; old FIN package references scan clean; `git diff --check` clean.
- Residual risk: invoking the existing waterfall report with date parameters under a tenant context fails MyBatis-Plus tenant SQL parsing for its UNION ALL query. This behavior predates the package move; no tenant-interceptor bypass or SQL behavior change was made in this architecture slice.
- Rollback scope: revert only the `refactor(fin): complete logical feature boundary` commit.

### Next Unit

Start HOME item 2.2.1: add a narrow decision-engine snapshot characterization test before moving any HOME production type. Keep the decision engine package and behavior unchanged until that test establishes first-call creation and same-date update behavior.

### Completed: HOME Snapshot Characterization

- Added `HomeCountSnapshotCharacterizationTest` for the existing `HomeController.homeCountVO()` entry used by `GET /home/count`.
- The test deletes only the current-day test snapshot, verifies the first call creates it, modifies the persisted value, then verifies the second same-day call keeps the same snapshot ID and recalculates its content rather than inserting a duplicate.
- The test fixes the existing dashboard response shape at six top-level keys: `today`, `month`, `year`, `total`, `inventoryValue`, and `alerts`.
- Preserved all HOME production packages, Spring component names, routes, SQL, mapper usage, snapshot write timing, and transaction behavior.
- Verified with `mvn -B -ntp -pl qk-money-app/money-app-biz -am test -Dtest=HomeCountSnapshotCharacterizationTest -Dsurefire.failIfNoSpecifiedTests=false`: 1 test passed against `money_pos_test`.
- Residual risk remains explicit: the endpoint writes daily snapshots on read and its application-level select-then-insert path has no synchronization. The database `UNIQUE(record_date)` constraint prevents duplicate rows, but concurrent first access can still surface a unique-key failure; this test characterizes the behavior but does not change it.

### Next Unit

Complete HOME item 2.2.2: inventory every direct dependency and read/write operation of `HomeController`, `HomeService`, and `DecisionEngineService` before moving any production type.

### Completed: HOME Dependency Inventory

- `HomeController` is the only production caller of `DecisionEngineService.getComprehensiveDashboard()` through `GET /home/count`; `GET /home/charts` is the only production caller of `HomeService.getChartsData(String)`. `HomeService.homeCount()` has no production caller and remains a compatibility API.
- `HomeServiceImpl` is read-only: it reads order aggregates through `OmsOrderMapper`, trend and brand data through `OmsOrderDetailMapper`, member-level chart data through `UmsMemberBrandLevelMapper`, and inventory value through the GMS-facing `GmsGoodsService` interface.
- `DecisionEngineServiceImpl` is the HOME write-on-read component: it reads order analysis through `OmsOrderAnalysisMapper`, member/order and historical-summary aggregates through `JdbcTemplate`, reads inventory value through `GmsGoodsService`, and creates or updates `OmsDailySummary` through `OmsDailySummaryMapper`.
- The snapshot table has `UNIQUE(record_date)`. There is no service-level transaction annotation or application synchronization around the select-then-insert branch, so the existing concurrent-first-request risk must be retained during a package-only move.
- No other production type injects either HOME service; the controller, both service interfaces and implementations, and the decision engine form a contained compatibility surface. The controller routes, shared GMS interface, shared OMS/UMS mappers, DTOs, entities, and JDBC SQL remain unchanged for the next unit.

### Next Unit

Move the contained HOME controller, service interfaces and implementations, and decision engine to `feature.home` with compatibility imports only. Preserve existing Spring bean names, routes, direct mapper/JDBC access, and write-on-read snapshot behavior; then run the HOME characterization test plus the stage 0 checkout suite before creating the HOME-only commit.

### Completed: HOME Logical Package Move

- Moved `HomeController` to `com.money.feature.home.interfaces.rest` and moved `HomeService`, `DecisionEngineService`, and both implementations to `com.money.feature.home.application`.
- Updated only the controller and HOME characterization-test imports. Class names, generated Spring bean names, HTTP routes, public methods, DTO/entity packages, mapper packages, JDBC SQL, snapshot write timing, and transaction annotations remain unchanged.
- Verified the full Spring context resolves the moved components and ran `HomeCountSnapshotCharacterizationTest` plus `CheckoutIntegrationTest`: 11 tests passed, with 0 failures and 0 errors, against `money_pos_test`.
- Verified no source or test code imports the former HOME controller/service packages and `git diff --check` is clean.
- Residual risk remains unchanged: `GET /home/count` writes snapshots on read; the database unique key rejects duplicate dates but concurrent first access can still encounter a unique-key failure. Manual browser smoke of `/home/count` and `/home/charts` remains required before Stage 2 completion.

### Next Unit

The HOME route manual smoke is deferred until a Windows/Electron desktop environment is available because the current source workspace runs under WSL. The current HOME-only commit is the rollback scope. This acceptance item remains required for Stage 2 completion but does not block code-boundary work; start TRADE item 2.3.1 with a call-surface and mapper-scan inventory, and do not move TRADE production types before it is explicit.

### Completed: TRADE Presentation and Persistence Inventory

- `PosController`, `OmsOrderController`, and `PosService` are the contained TRADE presentation/application surface. `UmsMemberPosController` is their only external production caller and can retain a dependency on the TRADE-facing `PosService` interface.
- `PosGoodsController` belongs to GMS because it only calls `GoodsPosFacade`; `PosCouponRuleController` belongs to UMS because it manages coupon rules and member coupons; `OmsSalesAnalysisController` belongs to FIN because it only calls the FIN-owned analysis service. None belongs in the TRADE move.
- `OmsOrderLogMapper` is used only by the TRADE order-log service and `OmsRefundIdempotentMapper` only by the TRADE refund state guard. They are safe TRADE persistence candidates.
- `OmsOrderMapper`, `OmsOrderDetailMapper`, and `OmsOrderPayMapper` remain shared compatibility Mappers because TRADE writes through them while HOME and FIN read them. `OmsDailySummaryMapper`, `OmsOrderAnalysisMapper`, and `OmsOrderTrafficMapper` are HOME/FIN-owned; POS coupon, member-coupon, member-level, and level-price Mappers remain UMS/GMS compatible types because other features use them.
- `MybatisConfig` currently scans `com.money.mapper` and the FIN Mapper package. A TRADE Mapper move must add the TRADE persistence package while retaining both existing scan roots.

### Next Unit

Move only `PosController`, `OmsOrderController`, `PosService`, `PosServiceImpl`, `OmsOrderLogMapper`, and `OmsRefundIdempotentMapper` into TRADE packages. Update `UmsMemberPosController`, the two TRADE mapper consumers, and `MybatisConfig`; preserve all routes, component names, SQL, DTO/entity types, and transaction behavior.

### Completed: TRADE Presentation and Dedicated Mapper Move

- Moved `PosController` and `OmsOrderController` to `com.money.feature.trade.interfaces.rest`, and moved `PosService` with `PosServiceImpl` to `com.money.feature.trade.application.pos`.
- Moved `OmsOrderLogMapper` and `OmsRefundIdempotentMapper` to `com.money.feature.trade.infrastructure.persistence.mapper`; updated their only TRADE consumers and extended `MybatisConfig` to scan the shared legacy, FIN, and TRADE Mapper roots.
- Updated `UmsMemberPosController` to depend on the moved TRADE-facing `PosService` interface. All controller class names, generated component names, routes, public signatures, SQL, DTO/entity packages, and transaction behavior remain unchanged.
- Retained the shared OMS payment/order/detail Mappers and the GMS/UMS compatible POS Mappers in `com.money.mapper` because HOME, FIN, GMS, or UMS still use them. The TRADE settlement write path remains the checkout application layer and its Facades.
- Verified no source or test code imports the former TRADE controller, service, implementation, or moved Mapper packages. `HomeCountSnapshotCharacterizationTest` and `CheckoutIntegrationTest` passed together: 11 tests, 0 failures, 0 errors, against `money_pos_test`.

### Next Unit

Run TRADE API-level regression for POS listing, member lookup, settlement trial, order query, refund, and receipt-print entry points when a suitable desktop or authenticated HTTP test setup is available. The Windows/Electron manual smoke remains deferred; do not move shared OMS/GMS/UMS Mappers before their owning Feature inventories are complete.

### Completed: GMS Subdomain Inventory

- Catalog: brand, category, goods, combo, import, and level-price types form the product-catalog surface. `GmsBrandMapper` is also used by UMS member import, and brand/category Mappers are used by the GMS import controller, so they remain compatible until UMS is organized.
- Pricing and stock: `GmsGoodsService`, stock calculation, combo, stock log, and level-price types support the GMS catalog. TRADE reads through GMS service interfaces for POS views and uses its existing checkout Facade for settlement writes; it must not gain new direct GMS internals.
- Inventory documents and analysis: inventory-order, inventory-document, document-item, stock-log, turnover, and stock-analysis types are GMS-owned. `GmsInventoryDocMapper` is read by FIN and written through the TRADE stock Facade; `GmsGoodsMapper` is used by the TRADE Facade and test fixtures. Both remain compatible shared Mappers for now.
- The first safe GMS slice is an internal controller/service pair not directly injected by TRADE, with its Mapper moved only after the full caller set and `@MapperScan` impact are explicit.

### Next Unit

Choose and inventory one isolated GMS internal candidate—prefer turnover or stock analysis—then move only that controller/service/implementation/Mapper set in a separately verified GMS commit. Keep catalog, price, and shared inventory Mappers in their compatibility packages until their cross-feature callers are addressed.

### Completed: GMS Turnover Warning Slice

- Moved `GmsTurnoverController` to `com.money.feature.gms.interfaces.rest`, `GmsTurnoverService` and its implementation to `com.money.feature.gms.application.turnover`, and the turnover query and snapshot Mappers to `com.money.feature.gms.infrastructure.persistence.mapper`.
- The turnover controller/service/implementation and both Mappers have no callers outside this slice. Its remaining `SysStrategyMapper` dependency stays in the shared SYS compatibility package.
- Extended `MybatisConfig` to scan the GMS persistence package while retaining the legacy shared, FIN, and TRADE Mapper scan roots.
- Preserved controller class and component names, `/gms/analysis` routes, Excel export behavior, turnover SQL, snapshot write-on-read behavior, DTO/entity packages, and tenant-interceptor annotations.
- Verified with `CheckoutIntegrationTest`: 10 tests passed, 0 failures, 0 errors, against `money_pos_test`; Spring startup confirms the moved GMS Mappers are scanned.

### Next Unit

Inventory the GMS stock-analysis controller/service as the next candidate, but keep `GmsStockLogMapper` compatible until all inventory-document and stock-log consumers are addressed. Do not move catalog or shared stock types together with it.

### Completed: GMS Stock Analysis Slice

- Moved `GmsStockAnalysisController` to `com.money.feature.gms.interfaces.rest` and moved its interface and implementation to `com.money.feature.gms.application.stockanalysis`.
- `GmsStockLogMapper` and `GmsGoodsService` remain shared GMS compatibility dependencies: inventory documents, inventory orders, stock logs, and TRADE still use them.
- Preserved `/gms/analysis` report and export routes, Excel behavior, query aggregation, DTO/entity packages, and all database contracts.
- Verified `test-compile` and `CheckoutIntegrationTest`: 10 tests passed, 0 failures, 0 errors, against `money_pos_test`.

### Next Unit

Inventory the GMS inventory-document and inventory-order subdomains together because they share goods, stock-log, and document Mappers; do not move either independently.

### Completed: GMS Inventory Document and Order Slice

- Completed the joint inventory: `GmsInventoryDocMapper` and `GmsInventoryDocItemMapper` remain in `com.money.mapper` because FIN reads the document Mapper and the TRADE stock Facade writes through both. `GmsGoodsMapper` and `GmsStockLogMapper` also remain compatible shared infrastructure because TRADE and other GMS inventory capabilities use them.
- Moved `GmsInventoryController`, `GmsInventoryDocService`, its implementation, `GmsInventoryOrderService`, and its implementation into the GMS logical boundary: the controller is in `feature.gms.interfaces.rest`; both service pairs are in `feature.gms.application.inventory`.
- Moved the exclusive `GmsInventoryOrderMapper` and `GmsInventoryOrderDetailMapper` to `feature.gms.infrastructure.persistence.mapper`. The existing GMS Mapper scan root discovers them; no scan configuration change was required.
- Preserved all `/gms/inventory` routes, controller signatures, Spring component names, transaction annotations, SQL, DTO/entity packages, and inventory update behavior.
- Verified `test-compile` and `CheckoutIntegrationTest`: 10 tests passed, 0 failures, 0 errors, against `money_pos_test`; source/test scans contain no imports of the former moved packages.

### Next Unit

Inventory the remaining GMS catalog and price/stock types as separate candidates. Do not move `GmsGoodsMapper`, `GmsStockLogMapper`, or the document Mappers until their TRADE, FIN, and GMS compatibility callers have an explicit replacement boundary.

### Completed: GMS Brand Strategy Presentation Slice

- Completed the catalog and price/stock caller inventory. Brand, category, goods, combo, price, stock, and stock-log types all retain production dependencies from TRADE, HOME, UMS, or shared Mapper consumers, so they are not safe package-only moves yet.
- Identified `GmsBrandConfigController` as the independent exception: no production type injects it, and its `/gms/brand/config` endpoints directly use the shared `SysBrandConfigMapper`.
- Moved only that controller to `com.money.feature.gms.interfaces.rest`. The shared SYS Mapper remains in `com.money.mapper`, because it is also used by POS and Excel import code.
- Preserved the controller class and component name, both routes, request/response shape, and strategy SQL behavior.

### Next Unit

Before moving a GMS catalog or price/stock service, define an explicit compatibility boundary for its existing TRADE, HOME, and UMS callers. In particular, do not move `GmsGoodsService`, `GmsBrandService`, `GmsGoodsCategoryService`, `GmsGoodsComboService`, `GmsGoodsPriceService`, or their shared Mappers as an arbitrary leaf slice.

### Completed: UMS Dependency Inventory and Member Log Slice

- Completed the UMS subdomain inventory. `UmsMemberService` is the shared member-profile/query entry used by TRADE and FIN. `UmsMemberAssetService` remains behind the TRADE-owned `MemberAssetFacade`; TRADE must continue using that facade for settlement and refund writes.
- `UmsMemberMapper`, `UmsMemberLogMapper`, and `UmsMemberBrandLevelMapper` remain shared compatibility Mappers because TRADE, FIN, HOME, or UMS import/profile code use them. `UmsRechargeOrderMapper` remains with the asset/recharge subdomain until that transaction group is moved together.
- Identified the read-only member-log route as the first contained UMS slice. Moved `UmsMemberLogController` to `com.money.feature.ums.interfaces.rest` and `UmsMemberLogService` with its implementation to `com.money.feature.ums.application.memberlog`; its Mapper stays compatible shared infrastructure.
- Preserved `/ums/member-log`, controller and service method signatures, Spring component names, query behavior, DTO/entity packages, and database contracts.
- Verified `test-compile` and `CheckoutIntegrationTest`: 10 tests passed, 0 failures, 0 errors, against `money_pos_test`; source/test scans contain no imports of the former moved packages.

### Next Unit

Inventory the member asset and recharge write path as one transaction group before moving it. Preserve `MemberAssetFacade` as TRADE's write-side entry and do not move the shared member, log, or recharge Mappers until all external consumers are mapped.

### Completed: UMS Member Asset and Recharge Slice

- Confirmed the transaction group: `UmsMemberAssetService` supplies member log reads and settlement/refund asset operations; `UmsMemberRechargeService` creates and voids recharge transactions. `UmsMemberAssetController` exposes the related asset and recharge routes.
- Moved both services to `com.money.feature.ums.application.memberasset` and the controller to `com.money.feature.ums.interfaces.rest`.
- Updated `UmsMemberServiceImpl` and the existing TRADE `MemberAssetFacade` to depend on the moved UMS services. The Facade remains the only TRADE checkout/refund caller of the asset service; no TRADE implementation or Mapper dependency was introduced.
- Retained `UmsMemberMapper`, `UmsMemberLogMapper`, and `UmsRechargeOrderMapper` in the shared compatibility package because member profile, import, FIN dashboard, TRADE support, and the moved services still use them. Transaction annotations and asset/recharge write behavior remain unchanged.
- Verified `test-compile` and `CheckoutIntegrationTest`: 10 tests passed, 0 failures, 0 errors, against `money_pos_test`; the suite includes settlement and refund member-asset scenarios. Source/test scans contain no imports of the former moved packages.

### Next Unit

Inventory the UMS member-profile and import subdomains together before moving either. `UmsMemberService` remains the shared interface used by TRADE and FIN, so any move must preserve that compatibility surface; do not move `UmsMemberMapper` or `UmsMemberBrandLevelMapper` while HOME, TRADE, and import code use them.

### Completed: UMS Member Profile and Import Slice

- Confirmed the contained compatibility surface: `UmsMemberController` and `UmsMemberImportController` use the UMS member application interface; `UmsMemberServiceImpl` orchestrates the profile, asset, recharge, and import subdomains. TRADE and FIN use only `UmsMemberService`.
- Moved `UmsMemberService`, its implementation, `UmsMemberProfileService`, and `UmsMemberImportService` to `com.money.feature.ums.application.member`; moved both controllers to `com.money.feature.ums.interfaces.rest`.
- Updated TRADE, FIN, and UMS controller imports to the relocated `UmsMemberService` interface. The `UmsMemberServiceImpl.MemberGoodsRankVO` compatibility type moved together with its implementation; its service and controller references were updated without changing the public method signature.
- Retained `UmsMemberMapper`, `UmsMemberBrandLevelMapper`, `UmsMemberLogMapper`, `GmsBrandMapper`, and coupon-related Mappers in the shared compatibility package because HOME, TRADE, FIN, GMS import, and the UMS subdomains still use them.
- Preserved all `/ums/member` routes, generated component names, method signatures, transaction annotations, DTO/entity packages, Excel import/export behavior, and database contracts.
- Verified `test-compile` and `CheckoutIntegrationTest`: 10 tests passed, 0 failures, 0 errors, against `money_pos_test`; source/test scans contain no imports of the former moved packages.

### Next Unit

Classify and move `UmsMemberPosController` with the TRADE presentation boundary: it exposes `/ums/member/pos-search` and coupon-rule routes but only depends on TRADE `PosService`, so it is not UMS-owned. Then reassess the remaining legacy UMS Mapper compatibility reasons and run the UMS functional regression gate.

### Completed: TRADE Member POS Presentation and UMS Compatibility Review

- Confirmed `UmsMemberPosController` has no callers and depends only on the TRADE-owned `PosService`; its `/ums/member/pos-search` and `/ums/member/coupon-rules` routes are TRADE presentation capabilities despite their UMS URL prefix.
- Moved that controller to `com.money.feature.trade.interfaces.rest`, preserving its class/component name, routes, DTO/entity types, and public methods.
- Reconfirmed every remaining UMS Mapper compatibility reason: `UmsMemberMapper` is used across UMS member subdomains; `UmsMemberLogMapper` is read by FIN and written by TRADE support and UMS; `UmsMemberBrandLevelMapper` is read by HOME and TRADE as well as UMS; `UmsRechargeOrderMapper` belongs to the moved asset/recharge services. All remain in `com.money.mapper` until a later shared-infrastructure boundary is introduced.
- Verified `test-compile` and `CheckoutIntegrationTest`: 10 tests passed, 0 failures, 0 errors, against `money_pos_test`; source/test scans contain no imports of the former controller package.

### Next Unit

Run the UMS functional regression gate for member profile, import/export, recharge/void, asset logs, POS member search, and coupon rules when an authenticated HTTP or desktop environment is available. It remains a Stage 2 acceptance requirement; no further UMS package move is necessary before that verification.

### Completed: GMS Catalog and Price/Stock Boundary Assessment (No Code Move)

- Reassessed the remaining GMS catalog and price/stock types. `GmsGoodsService` is the high-coupling product entry: TRADE checkout/POS/support, HOME, stock analysis, Excel operations, and GMS controllers use it. Its package move must therefore be a separately planned compatibility change.
- `GmsGoodsComboService`, `GmsGoodsPriceService`, and `GmsGoodsStockService` are internal helpers of `GmsGoodsService`, but their Mappers are also used by TRADE checkout/POS code. They are not safe independent package moves.
- Identified the safe first catalog slice: brand and category metadata controllers, service interfaces, and implementations. Their external consumers depend only on the service interfaces; moving the interfaces with compatibility-import updates does not add a TRADE/UMS internal dependency.
- `GmsBrandMapper` remains shared because UMS member import and GMS Excel use it. `GmsGoodsCategoryMapper` has only the category service and GMS Excel as callers, so it may move with this slice after updating the Excel controller import. Product, combo, price, stock, and log Mappers remain in the shared package.

### Next Unit

Move the GMS brand-and-category metadata slice: `GmsBrandController`, `GmsBrandService` and implementation, `GmsGoodsCategoryController`, `GmsGoodsCategoryService` and implementation, plus the category Mapper. Update GMS goods/Excel callers and TRADE/UMS interface imports; preserve routes, component names, transaction behavior, and shared `GmsBrandMapper` compatibility.

### Completed: GMS Catalog Metadata Slice

- Moved `GmsBrandController` and `GmsGoodsCategoryController` to `com.money.feature.gms.interfaces.rest`; moved `GmsBrandService`, `GmsGoodsCategoryService`, and their implementations to `com.money.feature.gms.application.catalog`.
- Moved `GmsGoodsCategoryMapper` to `com.money.feature.gms.infrastructure.persistence.mapper`. The existing GMS Mapper scan root discovers it; GMS Excel now imports the moved Mapper.
- Updated GMS product/Excel callers and TRADE/UMS interface callers to depend on the relocated brand/category service interfaces. Routes, Spring component names, public signatures, transaction annotations, DTO/entity packages, SQL, and Excel behavior remain unchanged.
- Retained `GmsBrandMapper` in `com.money.mapper` because UMS member import and GMS Excel still use it. Product, combo, price, stock, and log Mappers remain shared compatibility infrastructure.
- Verified `test-compile` and `CheckoutIntegrationTest`: 10 tests passed, 0 failures, 0 errors, against `money_pos_test`; source/test scans contain no imports of the former moved packages.

### Next Unit

Perform a no-change compatibility design for the high-coupling `GmsGoodsService` group before moving it. Its TRADE checkout/POS/support, HOME, GMS inventory/analysis, and Excel callers require an explicit interface boundary; do not move the product, combo, price, stock, or log Mappers independently.

### Completed: GMS Product Core Compatibility Design (No Code Move)

- `GmsGoodsService` is the temporary cross-feature product compatibility interface. TRADE checkout/POS/support, HOME, GMS stock analysis, GMS Excel, and the product controllers use it; several callers also use inherited `IService` methods such as `listByIds` and query chains. Replacing it with a narrower Facade would alter the established API and is out of scope for Stage 2.
- The service implementation is the GMS product-core transaction orchestrator: it coordinates brand/category counters, product persistence, member-price matrix, combo BOM, and stock updates. Its public `add`, `update`, and `delete` methods retain the existing class-level transaction boundary.
- `GmsGoodsComboService`, `GmsGoodsPriceService`, and `GmsGoodsStockService` are internal product-core helpers. `GmsGoodsExcelManager` and `GmsGoodsExcelController` form the same product-catalog compatibility surface because import/export calls the product and price services.
- Boundary decision: move the product service interface/implementation, three helpers, goods controller, Excel controller, and Excel manager together into GMS logical packages; retain `GmsGoodsMapper`, `GmsGoodsComboMapper`, `PosSkuLevelPriceMapper`, `GmsStockLogMapper`, and `GmsBrandMapper` in the shared Mapper package. Existing TRADE direct Mapper use is recorded compatibility debt and must not be expanded in this stage.
- `GmsStockLogService` is excluded from this slice: it is the existing TRADE batch-log write API and stays compatible until the shared stock-log persistence boundary is addressed.

### Next Unit

Move the GMS product-core logical slice with compatibility imports only: `GmsGoodsController`, `GmsGoodsService` and implementation, `GmsGoodsComboService`, `GmsGoodsPriceService`, `GmsGoodsStockService`, `GmsGoodsExcelManager`, and `GmsGoodsExcelController`. Preserve all routes, `IService` compatibility, transactions, SQL, Excel behavior, and shared Mapper packages; verify compilation and the Stage 0 suite before committing.

### Completed: GMS Product Core Logical Slice

- Moved `GmsGoodsController` and `GmsGoodsExcelController` to `com.money.feature.gms.interfaces.rest`.
- Moved `GmsGoodsService`, its implementation, `GmsGoodsComboService`, `GmsGoodsPriceService`, `GmsGoodsStockService`, and `GmsGoodsExcelManager` to `com.money.feature.gms.application.product`.
- Updated HOME, TRADE, GMS stock analysis, and compatible legacy callers to depend on the moved `GmsGoodsService` interface. Its `IService` inheritance, class-level transaction boundary, public methods, DTO/entity types, routes, SQL, and Excel behavior remain unchanged.
- Retained `GmsGoodsMapper`, `GmsGoodsComboMapper`, `PosSkuLevelPriceMapper`, `GmsStockLogMapper`, and `GmsBrandMapper` in the shared package. `GmsStockLogService` remains compatible because TRADE uses it for batch log writes; no new cross-feature Mapper dependency was introduced.
- Verified `test-compile` and `CheckoutIntegrationTest`: 10 tests passed, 0 failures, 0 errors, against `money_pos_test`; source/test scans contain no imports of the former moved packages.

### Next Unit

Run the GMS functional regression gate for catalog CRUD, category/brand selection, product Excel import/export, price matrix, combo stock behavior, inventory documents, turnover/stock analysis, and POS settlement/refund when authenticated HTTP or desktop verification is available. No further GMS production package move is required before that gate.

### Completed: GMS Automated Feature Regression

- Added `GmsFeatureIntegrationTest` for the relocated GMS components. It creates isolated brand/category fixtures, verifies the moved selection services, creates a product with member-price matrix, creates a combo product, verifies persisted price and BOM records, verifies combo stock propagation, then executes an inbound inventory document and verifies the product stock and document persistence.
- The test uses the test tenant and transaction rollback, so it leaves no fixture data in `money_pos_test`.
- Verified the new GMS test together with `CheckoutIntegrationTest`: 11 tests passed, 0 failures, 0 errors, against `money_pos_test`.
- Remaining acceptance scope is explicitly manual or authenticated-HTTP only: actual Excel upload/download, controller authorization and route smoke, turnover/stock-analysis screen exports, and interactive POS flow. These cannot be substituted by this service-level test while the desktop environment is unavailable.

### Next Unit

Run the remaining Stage 2 automatic closure checks: full `mvn test`, `mvn package`, and static Bean/Mapper/legacy-package scans. Keep the authenticated HTTP/desktop smoke list as the final manual acceptance gate.

### Completed: Stage 2 Static Discovery and Compatibility Check

- Scanned every Java import for a package vacated by a Stage 2 rename; no stale old-package import remains.
- Production Java simple-class names are unique, so the default Spring component naming strategy has no duplicate-name candidate after the package moves. The existing integration suites have also started the full Spring context after the moves.
- `MybatisConfig` scans the shared legacy Mapper package plus the FIN, GMS, and TRADE feature persistence packages. This covers every moved Mapper root.
- Reconfirmed the remaining legacy GMS and UMS Mappers have multiple active consumers and remain documented compatibility infrastructure rather than accidental incomplete moves.

### Next Unit

Run the Stage 2 full automatic closure: `mvn test`, `mvn package`, and the Stage 0 suite. Do not stage or modify the user's existing front-end and local configuration changes while performing those checks.

### Completed: Stage 2 Full Automatic Closure

- Full reactor `mvn test` passed against the isolated test configuration. The active source suites cover test-database safety, HOME snapshot characterization, FIN feature reads, GMS product core, and the 10-scenario TRADE checkout/refund suite.
- Full reactor `mvn package` passed and produced the local module JAR artifacts.
- The Stage 0 `CheckoutIntegrationTest` rerun passed: 10 tests, 0 failures, 0 errors. The final GMS product-core report passed: 1 test, 0 failures, 0 errors.
- A GMS test-fixture name was shortened to respect the existing database field length; this only corrects test data and does not change production behavior.

### Next Unit

Complete the remaining Stage 2 manual acceptance: HOME `/home/count` and `/home/charts`; TRADE POS listing, member lookup, settlement trial, order query, refund, and receipt print. Then update the ledger to state whether Stage 3 preconditions are met. Preserve the user's uncommitted front-end and local configuration changes throughout.

### Completed: Stage 2 Web Manual Acceptance (Hardware Exception Recorded)

- The user confirmed that the running front-end and back-end test environment passed the HOME `/home/count` and `/home/charts` checks, plus TRADE POS product/member lookup, settlement, order query, and refund flows.
- GMS and UMS web functional acceptance was already confirmed, completing the browser-visible business flows required by Stage 2.
- Receipt printing cannot be exercised in the current environment because no receipt printer is attached. This is a hardware-environment exception, not a known application failure; it remains the only unverified TRADE acceptance action and must be checked when a supported printer is available.
- After the Maven closure run, no Java back-end process or Vite/Node front-end process is listening. Maven test/package launches only temporary test servers and does not keep the web application running; this does not invalidate the previously completed web acceptance.

### Next Unit

Obtain a supported receipt printer for the final print-path check, or explicitly accept deferring that hardware-only check to Stage 3. Once that decision is recorded, update the ledger with the Stage 3 precondition status; do not include the user's unrelated uncommitted front-end or local configuration changes.

### Completed: Stage 2 Closure and Stage 3 Preconditions

- The user explicitly accepted deferring receipt-printer acceptance until compatible hardware is available. This records a hardware-only deferred acceptance item, not a production-code defect.
- All Stage 2 package-boundary, static-discovery, automatic-test, and available web-functional acceptance items are complete. The deferred printer check remains traceable for the later hardware-enabled validation.
- Stage 3 may begin. Preserve the existing local `dev` branch discipline: do not push or merge to `main` without the user's explicit request, and do not include the user's unrelated uncommitted front-end or local configuration changes in architecture commits.

### Completed: Stage 3 Execution Checklist Created

- Created `docs/MoneyPOS-Stage-3-Runtime-Capability-Checklist.md`, following the Stage 2 checklist format and the ledger-defined Stage 3 scope.
- The checklist deliberately starts with no completed Stage 3 code items. It prioritizes workspace initialization, embedded MariaDB guarding, and local-file capability; backup, printer, POS WebSocket, and Electron main-process changes remain separately deferred.
- The existing receipt-printer hardware acceptance remains traceable as a future environment check and does not block the other Stage 3 slices.

### Completed: Stage 3.0 Runtime Capability Baseline

- Recorded the local branch and dirty-worktree boundary: `dev` is 22 local commits ahead of `origin/dev`; the user's front-end, local startup/configuration, and WSL documentation changes remain excluded.
- Identified three supported runtime modes: desktop embedded mode is explicitly enabled by `--app.home` or `money.workspace.embedded=true`; IDE/WSL mode skips the Windows embedded MariaDB and uses development configuration; tests use the dedicated test profile.
- Added and passed `RuntimeCapabilityCharacterizationTest` (4 tests, 0 failures, 0 errors) without starting a real Windows MariaDB. It characterizes the explicit embedded-start condition, configured data-root directory layout, injected datasource/local-asset properties, and existing MariaDB port/database identity.
- Recorded the dependency baseline: workspace is used by application startup and backup paths; local-file storage and static resource serving consume the injected bucket; backup directly uses workspace and MariaDB guard; printer is used by FIN/TRADE controllers; WebSocket remains independent. The backup task currently reads `app.home/backups` while the backup service reads `app.data/backups`; this pre-existing path inconsistency is explicitly deferred to the Stage 3.3/3.4 backup scope. The next minimal task is Stage 3.1 workspace-initialization boundary design.

### Completed: Stage 3.1 Workspace Initialization Boundary

- Introduced `com.money.platform.runtime.workspace.RuntimeWorkspace` as the stable application/data-directory boundary and `RuntimeWorkspaceConfiguration` for pre-Spring configuration injection. The new runtime package does not import any `feature.*` type.
- Retained `AppWorkspace`, `WorkspaceEnv`, and `AppConfigInjector` as deprecated compatibility bridges. This preserves the user's uncommitted `QkMoneyApplication` startup change while moving the implementation and lets the later MariaDB slice replace its remaining legacy guard dependency safely.
- Updated backup service and task callers to use the public runtime workspace boundary without changing their existing path choices or backup semantics.
- Verified compilation and `RuntimeCapabilityCharacterizationTest`: 5 tests, 0 failures, 0 errors. The test covers the runtime boundary, directory layout, property injection, desktop-start condition, and legacy workspace delegation without starting Windows MariaDB.
- Verified the real development mode after rebuilding the reactor: the WSL-local MariaDB `money_pos` connection started successfully, Flyway reported no pending migration, and `/money-pos/actuator/health` returned `UP`, including the database `SELECT 1` check. The Vite front end also served its entry page on port 1520 and retains its proxy to `localhost:9101`.
- The isolated test-database variables remain unset in the current shell, so this slice's automated evidence is the 5-test no-database characterization suite; the established Stage 0 suite remains available for a later isolated `money_pos_test` run. No test run targeted the development database.
- Rollback is the single Stage 3.1 local commit: it removes the new `platform.runtime.workspace` boundary and restores the three legacy workspace implementations plus the two updated public callers. The next minimal task is Stage 3.2 embedded MariaDB guardian inventory and boundary design.

### Completed: Stage 3.2 Embedded MariaDB Guardian Boundary (Windows Acceptance Pending)

- Moved the embedded MariaDB lifecycle, first-run secret handling, data-directory identity verification, process start and shutdown hook into `com.money.platform.runtime.database.EmbeddedMariaDbGuardian`. Its public port (`9102`), database (`money_pos`), secret-file, `.wx_meta`, `db_data`, and datasource-injection contracts remain unchanged.
- `RuntimeWorkspace`, `RuntimeWorkspaceConfiguration`, and the existing backup service now consume the runtime guardian contract. The former `com.money.workspace.MariaDbGuardian` is a deprecated compatibility facade only; no Feature package directly imports the guardian implementation.
- Added `EmbeddedMariaDbGuardianCharacterizationTest`; together with `RuntimeCapabilityCharacterizationTest`, 7 tests passed with 0 failures and 0 errors. The tests cover normalized associated/non-associated data-directory decisions and actual temporary loopback-port detection without starting a native MariaDB engine.
- Aggregated `mvn -pl qk-money-app/money-app-biz -am package -DskipTests` passed. The rebuilt JAR was started in WSL development mode; Flyway reported `money_pos` current, the datasource connected to external `127.0.0.1:3306/money_pos`, and `/money-pos/actuator/health` returned `UP` including `SELECT 1`. Therefore IDE/WSL did not invoke the Windows engine.
- Windows packaged embedded-MariaDB acceptance remains deliberately open: it requires a Windows installation containing `mariadb/bin/mysqld.exe` and `mysql_install_db.exe`, started with the existing embedded switch. Validate first initialization, associated-instance reuse and rejection of an unrelated process occupying port `9102`. This is an environment gap, not a known WSL-development failure.
- Rollback is the single Stage 3.2 local commit: restore the former guardian implementation and its prior callers. The next minimal task is Stage 3.3 local-file capability inventory; do not mix backup workflow changes into that slice.

### Completed: Stage 3.3 Local File Capability Boundary

- Added `com.money.platform.runtime.file.RuntimeFileStorage` as the single runtime locator for `assets`, `logs`, `backups`, `db_data`, data-root metadata files, and the existing physical `local.bucket` value. It depends only on the runtime workspace boundary, never on a Feature package.
- Moved standard-directory creation, embedded MariaDB data/metadata locations, runtime asset-property injection, backup service asset/backup roots, and nightly backup cleanup to this locator. This also resolves the prior cleanup-path mismatch: automatic cleanup now reads the same `app.data/backups` directory where the backup service writes ZIP files.
- The backup archive name, manifest, SQL dump/import commands, restore ordering, upload object-key format, static URL contract, and deletion policies were not changed. Direct `File` usage remaining in backup code is limited to those business-format operations and is intentionally deferred to the Stage 3.4 backup review.
- Added `RuntimeFileStorageTest`; together with the workspace and MariaDB characterization suites, 8 tests passed with 0 failures and 0 errors. The test uses an isolated temporary data root and verifies every standard runtime directory remains below it.
- Rollback is the single Stage 3.3 local commit. The next Stage 3 work is the explicitly deferred 3.4 capability inventory: backup/recovery, printer hardware, POS WebSocket and Electron contract, each without protocol or behavior changes.

### Completed: Stage 3.4 Deferred Runtime Capability Inventory (Printer Hardware Pending)

- Recorded the backup/recovery contract without changing it: controller SSE/download/restore routes, ZIP contents, Manifest and SQL tooling, protection backup, shadow-database verification, atomic switch, and scheduled cleanup remain as implemented. Future backup work must be a dedicated slice rather than an incidental runtime-path change.
- Recorded the printer contract: TRADE invokes the receipt/drawer path, FIN invokes shift-handover printing, and `PosPrinterService` sends ESC/POS bytes to the operating system's default print service. No compatible receipt printer is present. Also, the refund flow currently has no caller to the printer service, so a post-refund receipt requires its own requirements/implementation slice before it can become a hardware acceptance item.
- Recorded the POS customer-display protocol: `/money-pos/ws/pos-sync` on port `9101`, broadcast forwarding, and the existing `IDLE`/`CASHIER_UPDATE`/`CHECKOUT_OPEN`/`PAY_SUCCESS` JSON states and fields. No endpoint, message, reconnect, or display behavior changed.
- Recorded the Electron main-process contract: `main.cjs` launches the packaged backend with `--app.home`, polls `/money-pos/actuator/health`, creates windows only after health succeeds, and terminates the child backend on quit. No Electron source was changed.
- This inventory has no production-code change. The next Stage 3 work is 3.5 closure verification; keep Windows embedded-MariaDB acceptance and receipt-printer hardware acceptance explicitly open.

### Completed: Stage 3.5 Automated Closure Verification

- Dependency scan passed for the Stage 3 boundary: `platform.runtime` has no `feature.*` import, and Feature packages do not import `platform.runtime`. Legacy workspace compatibility classes only delegate to the runtime boundary. Seven pre-existing Controllers still import Mapper types; this is recorded as a Stage 4 architectural-rule baseline, not changed during Stage 3.
- The isolated `money_pos_test` database was created and granted to the development application account. The full reactor `mvn test` passed with 8 test classes and 21 tests (0 failures / 0 errors); this includes 8 runtime characterization tests and the Stage 0 `CheckoutIntegrationTest` with 10 tests covering checkout, coupons, refunds, stock and sale-out documents. Flyway initialized only `money_pos_test`; development database `money_pos` was not used or modified.
- Full reactor `mvn package -DskipTests` passed after the test run. The current WSL development backend remains healthy at `/money-pos/actuator/health` with external MariaDB `127.0.0.1:3306/money_pos`.
- Windows embedded-MariaDB acceptance and physical printer acceptance remain open environment items. They are traceable exceptions rather than code failures: the former requires a packaged Windows runtime with its MariaDB engine, and the latter requires compatible receipt-printer hardware. Stage 4 may begin with the recorded controller-to-Mapper baseline, while those environment items remain separately executable.

### Completed: Stage 4.0 Regression-Prevention Baseline

- Added `MoneyPOS-Stage-4-Regression-Prevention-Checklist.md` as the executable Stage 4 plan. It defines a gradual rule strategy: new violations are prevented only after the corresponding scan is stable; historical debt remains explicitly baselined and is migrated in safe business slices.
- Read-only source scan found 27 Controllers and 7 historical Controller-to-Mapper dependencies: `SysStrategyController`, `GmsBrandConfigController`, `GmsGoodsExcelController`, `GmsStockLogController`, `PosCouponRuleController`, `UmsMemberController`, and `UmsMemberImportController`. No Feature imports another Feature's `ServiceImpl` or Mapper, and `platform` imports no `feature` package.
- Entity-contract enforcement remains intentionally deferred: 62 Feature files still import legacy shared `com.money.entity` types, which have no reliable per-Feature physical ownership marker. Establish an ownership map before treating those imports as violations.
- No production code, database schema, front end, tests or build behavior changed in 4.0. The immediate next action is 4.1: publish the short architecture collaboration guide, then add a non-blocking scan report before any blocking gate or Controller migration.

### Completed: Stage 4.1 Architecture Collaboration Guide

- Added `MoneyPOS-Architecture-Collaboration-Guide.md` as the single, developer-facing dependency guide. It defines the Controller/application/infrastructure/runtime responsibilities; permits stable Feature service interfaces and the established TRADE facades; prohibits new Controller-to-Mapper, cross-Feature `ServiceImpl`/Mapper/internal-helper and `platform`-to-`feature` dependencies.
- The guide records the shared `com.money.entity` transition rule: new cross-domain contracts use scenario DTOs; any temporary shared-Entity use must carry a compatibility reason and replacement plan until 4.2 establishes ownership. The seven Controller baseline files are migration targets, not precedent for new dependencies.
- Added a small review checklist covering package ownership, forbidden imports, Entity contracts, test scope and ledger updates. This is documentation only; no production behavior, database schema, routes, tests or build configuration changed. The immediate next action is 4.2: create the Entity ownership map and DTO migration priority.

### Completed: Stage 4.2 Entity Ownership and DTO Priority

- Added `MoneyPOS-Entity-Ownership-and-DTO-Plan.md`, a logical ownership table for all 37 shared Entity types. It assigns goods/inventory and level prices to GMS; members, coupons and member transaction history to UMS; orders/payments/refunds to TRADE; the daily summary read model to HOME; and configuration/reference entities to SYS. The source packages, persistence mapping and database contracts remain unchanged.
- Set the migration order from observed contracts: P0 replaces the `PosCouponRule` entity returned through the TRADE POS service and UMS coupon-rule route; P1 narrows TRADE reads of GMS goods and UMS members currently exposed through `IService<Entity>`; P2 converts FIN/HOME report assemblers; P3 leaves internal/unused compatibility types alone. The existing transaction write-side facades remain intact.
- Recorded two adjacent debts for future scoped work: POS-prefixed Entity names do not determine ownership, and `UmsMemberServiceImpl.MemberGoodsRankVO` leaks an implementation nested type. No code, route, schema, mapper, Entity package or runtime behavior changed. The immediate next action is 4.3: turn the established scans into a non-blocking, versioned report.

### Completed: Stage 4.3 Non-Blocking Architecture Scan

- Added `scripts/architecture-scan.sh`, a deterministic report-only scanner for the four Stage 4 evidence categories. It scans the business Java source tree and always exits successfully after printing Controller-to-Mapper, cross-Feature `ServiceImpl`/Mapper, `platform`-to-`feature`, and shared Entity import results; it is deliberately not wired into Maven or CI.
- Added `MoneyPOS-Architecture-Scan-Baseline-v1.md`. The versioned baseline records 7 Controller-Mapper files, 0 cross-Feature implementation/Mapper imports, 0 platform-to-Feature imports and 62 Feature files importing the shared Entity package. It gives the seven historical files and Entity debt explicit, reviewable treatment instead of silently allowing them.
- Ran the report against the current source before recording the baseline. No production code, database, route, test or build behavior changed. Two stable migration slices plus the P0 DTO work are required before considering an additions-only gate. The immediate next action is 4.4: choose and migrate one low-coupling Controller-Mapper baseline file.

### Completed: Stage 4.4 First Controller-to-Mapper Migration

- Selected `SysStrategyController` as the first low-coupling slice: it has two routes, one Mapper and no production caller, while FIN/GMS only read the same global strategy through the Mapper. Added `SysStrategyService` and `SysStrategyServiceImpl` in the existing business module because the Mapper remains there; moving either to the SYS Maven module would broaden the module dependency scope.
- Preserved `/sys/strategy/get` and `/sys/strategy/save`, the `SysStrategy` request/response type, empty-strategy fallback, global tenant ID `0` on first insert, and update-by-existing-ID behavior. The Controller now delegates to the service and imports no Mapper.
- Added and passed `SysStrategyServiceIntegrationTest` against `money_pos_test` (1 test, 0 failures / 0 errors). The non-blocking scan now reports 6 outstanding Controller-Mapper files, 0 cross-Feature `ServiceImpl`/Mapper imports, 0 platform-to-Feature imports and 62 shared Entity importers. The v1 baseline intentionally retains the original 7 for trend comparison. The immediate next action is 4.5: run the full test/build closure and publish the current scan result.

### Completed: Stage 4.5 First Migration Closure

- Full reactor `mvn test` passed against the isolated `money_pos_test`: 9 current test classes and 23 tests, all with 0 failures / 0 errors. This includes the Stage 0 checkout/refund scenarios, FIN/GMS/HOME coverage, runtime characterization and the new strategy service integration test. `mvn package -DskipTests` also passed.
- Published `MoneyPOS-Architecture-Scan-Report-Stage4.5.md`. The current scan is 6 Controller-Mapper files (one lower than v1), 0 cross-Feature implementation/Mapper imports, 0 platform-to-Feature imports and 62 shared Entity importers. The report gives every remaining Controller-Mapper item a concrete next slice rather than treating the baseline as a permanent exemption.
- The rule gate remains report-only: only one Controller migration has completed and the P0 coupon-rule DTO contract is not yet migrated. No production database, route, shared Entity package or front end changed during closure. The next smallest safe implementation is the read-only `GmsStockLogController` → GMS inventory-log query-service slice; after that second stable sample, reassess additions-only gate readiness.

### Completed: Stage 4.5 Second Migration Closure

- Added `GmsStockLogQueryService` and its implementation under `feature.gms.application.inventory`; it owns the existing paged inventory-log query. `GmsStockLogController` now delegates to it and no longer imports `GmsStockLogMapper`. The endpoint, `GmsStockLogQueryDTO`, optional barcode parameter, `PageVO<GmsStockLog>` response, exact barcode match, name/type/order filters and descending time order remain unchanged.
- Kept the existing shared `GmsStockLogService` untouched because TRADE write-side services use it for batch log persistence. Added and passed `GmsStockLogQueryServiceIntegrationTest`, which verifies exact barcode plus the existing name/type/order filters against the isolated database.
- Full reactor `mvn test` passed with 10 current test classes and 24 tests (0 failures / 0 errors); `mvn package -DskipTests` passed. The scan now reports 5 Controller-Mapper files, 0 cross-Feature implementation/Mapper imports and 0 platform-to-Feature imports. Shared Entity importer count rose from 62 to 64 solely because the two new GMS query-boundary types lawfully import their own `GmsStockLog` Entity; this is not a cross-domain contract increase.
- Two stable Controller migrations now exist, but the P0 coupon-rule DTO contract remains before an additions-only gate can be enabled. The next smallest safe task is the P0 `PosCouponRule` DTO slice across `PosService` and `/ums/member/coupon-rules`.

### Completed: GMS and UMS Manual Functional Regression

- The user completed the GMS acceptance flow in the running front-end and back-end test environment: brand/category and product maintenance, level pricing, combo stock propagation, inbound/outbound/check inventory documents and stock logs, product Excel import/export, inventory analysis/turnover views and exports, plus POS sale and full-refund stock restoration all passed.
- The user completed the UMS acceptance flow: member maintenance and POS search, balance recharge and void, asset logs, voucher issue/use, refund asset restoration, member import/export, and coupon-rule retrieval and use all passed.
- This closes the GMS `2.4.9` and UMS `2.5.4` functional regression gates. The remaining Stage 2 work is cross-feature closure verification and the still-separate HOME/TRADE manual smoke items.

### Completed: Stage 4 P0 Coupon-Rule DTO Contract

- Replaced the persistence-entity return contract of `PosService.getValidCouponRules()` and `/ums/member/coupon-rules` with `CouponRuleSummary`. The DTO retains the five fields consumed by the member/POS screens (`id`, `name`, `thresholdAmount`, `discountAmount`, `status`) and deliberately excludes persistence metadata. The route, HTTP method and JSON field names are unchanged, so no front-end change is required.
- Added `UmsMemberPosControllerIntegrationTest` against `money_pos_test` to verify the coupon-rule endpoint returns the intended DTO values. Full reactor `mvn test` passed with 11 current test classes and 25 tests (0 failures / 0 errors); `mvn package -DskipTests` passed.
- The architecture scan is 5 Controller-Mapper files, 0 cross-Feature `ServiceImpl`/Mapper imports, 0 `platform → feature` imports and 63 shared Entity importers. The shared Entity count decreased by one because this public contract no longer imports `PosCouponRule`; it remains a non-blocking compatibility metric.
- The two Controller migration samples and P0 DTO contract are now complete. The next smallest safe task is to make the first three scan categories additions-only: compare them with the checked-in baseline and fail only for newly introduced violations, while keeping the shared-Entity rule report-only until P1.

### Completed: Stage 4 Additions-Only Local Architecture Gate

- Extended `scripts/architecture-scan.sh` with an explicit `--check-new` mode. Its default invocation remains report-only and always returns success; the gate mode compares the three structural categories with the checked-in v1 baseline and returns exit code 1 only for a new Controller-Mapper file, cross-Feature `ServiceImpl`/Mapper import, or `platform → feature` import.
- The seven original Controller-Mapper paths are a fixed allow-list. Completed migrations are permitted to reduce that list but never enlarge it. The clean v1 baselines for the other two structural rules allow no findings. Shared `com.money.entity` imports are printed in both modes but remain non-blocking until P1.
- Verified `bash -n`, default report mode and `--check-new` against current source: the report is 5/0/0/63 and the additions-only gate passes. An unknown option correctly returns exit code 2. The gate is intentionally local-only: it is not wired into Maven or CI. The next action is an independent development-flow review before deciding whether CI should invoke it.

### Completed: Stage 4 PosCouponRuleController Migration

- Added `CouponRuleManagementService` under `feature.trade.application.coupon` and moved the former controller-owned data access into it: paged name filtering ordered by creation time, rule create/update/delete, and the member's `UNUSED` coupon grouping by rule. `PosCouponRuleController` now delegates every route and imports no Mapper.
- Preserved the `/pos/couponRule` route family, `PageVO<PosCouponRule>` management response, `PosCouponRule` request bodies, existing map keys (`ruleId`, `name`, `thresholdAmount`, `discountAmount`, `ownedCount`) and empty-list behavior. This slice intentionally does not broaden the P0 read-route DTO work into a management-endpoint contract rewrite.
- Added and passed `PosCouponRuleControllerIntegrationTest` against `money_pos_test`, covering list, create, update, delete and member-coupon aggregation. Full reactor `mvn test` passed with 12 current test classes and 26 tests (0 failures / 0 errors); `mvn package -DskipTests` and `architecture-scan.sh --check-new` passed.
- The structural scan is now 4 Controller-Mapper files, 0 cross-Feature `ServiceImpl`/Mapper imports and 0 `platform → feature` imports. Shared Entity importer count is 65: the two new TRADE application service types lawfully use their own coupon entities as internal persistence implementation, so this remains tracked non-blocking debt. The next smallest task is to inventory the remaining four Controller-Mapper files before selecting the next safe slice.

### Completed: Stage 4 Remaining Controller-Mapper Inventory

- Reviewed the four remaining files and published `MoneyPOS-Stage4-Remaining-Controller-Inventory.md`. `GmsGoodsExcelController` has three direct Mappers plus dynamic Excel, price matrix and dictionary behavior; `UmsMemberImportController` has five direct Mappers plus cross-feature reference data, assets and dynamic Excel. Both require dedicated boundary work rather than a mechanical Controller wrapper.
- `GmsBrandConfigController` has only two routes and one SYS Mapper, but its persistence type belongs to SYS while its route belongs to GMS; it remains a medium-risk slice until a narrow GMS-to-SYS configuration contract is designed.
- Selected the three read-only rank routes in `UmsMemberController` as the next safe slice. They are the only remaining same-Feature, single-Mapper, no-write candidate, and the controller already delegates its other membership routes to `UmsMemberService`. The next action is to add the rank-query methods to that service and remove the Controller's `UmsMemberMapper` dependency without changing routes or `MemberRankVO` output.

### Completed: Stage 4 UMS Member-Rank Controller Migration

- Added `getTopConsumeMembers()`, `getTopBalanceMembers()` and `getTopFrequencyMembers()` to `UmsMemberService`; its existing implementation delegates the unchanged SQL queries through its own inherited `UmsMemberMapper`. `UmsMemberController` now delegates all three rank routes to the service and no longer imports a Mapper.
- Preserved `/ums/member/rank/consume`, `/rank/balance` and `/rank/frequency`, their `umsMember:list` permission checks and the existing `MemberRankVO` output fields. The pre-existing `MemberGoodsRankVO` implementation-nested type is unrelated profile DTO debt and remains outside this small slice.
- Added and passed `UmsMemberControllerRankIntegrationTest` against `money_pos_test`, validating consumption amount, balance amount and visit frequency rank fields. Full reactor `mvn test` passed with 13 current test classes and 27 tests (0 failures / 0 errors); `mvn package -DskipTests` and `architecture-scan.sh --check-new` passed.
- The scan now reports 3 Controller-Mapper files, 0 cross-Feature `ServiceImpl`/Mapper imports, 0 `platform → feature` imports and 65 shared Entity importers. The next smallest task is a narrow GMS-to-SYS brand configuration service contract for `GmsBrandConfigController`; the Excel and member-import controllers remain intentionally deferred.

### Completed: Stage 4 GMS-to-SYS Brand-Configuration Contract Design

- Published `MoneyPOS-Stage4-Gms-Sys-Brand-Config-Contract.md`. The design separates SYS persistence ownership from GMS route behavior: a SYS service is the only Mapper consumer, while a GMS application service consumes narrow policy DTOs and preserves the route's default/JSON mapping.
- Captured the compatibility-sensitive behavior from the running front end and current controller: unconfigured brands return `couponEnabled=true` plus `levelCodes=null`; configured empty levels return an empty array; non-empty levels split only on commas; existing records update only the switch and code string; new records insert as submitted.
- `GoodsPosFacade` and `GmsGoodsExcelManager` also read SYS brand configuration, but those existing uses are deliberately outside this Controller migration. The next action is to implement the two services, migrate `GmsBrandConfigController`, add the specified integration test and run the Stage 4 closure checks.

### Completed: Stage 4 GMS Brand-Configuration Controller Migration

- Added neutral API DTOs `BrandPricingPolicy` and `BrandPricingPolicyView`; added `SysBrandConfigService` as the persistence owner for the brand-config Mapper; and added `GmsBrandPricingConfigService` as the GMS route/application boundary. `GmsBrandConfigController` now depends only on the GMS service and imports neither `SysBrandConfig` nor `SysBrandConfigMapper`.
- Preserved `GET`/`POST /gms/brand/config` and the front-end's `brand`, `couponEnabled`, `levelCodes` JSON fields. The GMS service preserves the exact legacy outcomes: missing config is `true/null`, configured empty codes are an empty array, non-empty codes split on commas, and existing persistence rows retain identity/tenant/audit fields while only switch and code string are updated.
- Added and passed `GmsBrandConfigControllerIntegrationTest` against `money_pos_test`, covering missing configuration, first insert, overwrite and empty-level compatibility. Full reactor `mvn test` passed with 14 current test classes and 28 tests (0 failures / 0 errors); `mvn package -DskipTests` and `architecture-scan.sh --check-new` passed.
- The scan now reports 2 Controller-Mapper files, 0 cross-Feature `ServiceImpl`/Mapper imports, 0 `platform → feature` imports and 64 shared Entity importers. The remaining direct Controller Mapper imports are the intentionally deferred GMS goods Excel and UMS member-import batch boundaries. The next smallest task is to inventory and divide the GMS Excel template/export/import behaviors before selecting one safe sub-slice.

### Completed: Stage 4 GMS Goods Excel Inventory

- Reviewed the three `/gms/goods` Excel routes and published `MoneyPOS-Stage4-Gms-Goods-Excel-Inventory.md`. The import route already delegates to transactional `GmsGoodsExcelManager`; it creates reference data when necessary and updates goods, stock and membership-price rows, so it is deliberately excluded from this Controller-Mapper migration.
- The template and full-export routes are both read-only but share the exact dynamic header contract: eleven fixed columns followed by member-price columns derived from `memberType` excluding `MEMBER`. Splitting them would duplicate that contract and risk template/export drift. The selected safe slice therefore moves both routes as one GMS read service, while keeping import untouched.
- Confirmed that category/brand reads can use the existing GMS services, member-type reads can use `SysDictDetailService.listByDict`, and existing product/price services already provide the export read model. The SYS dictionary service's Entity-returning compatibility signature is recorded P1 work and is not broadened in this Controller migration.
- The next smallest task is to implement `GmsGoodsExcelReadService`, delegate the template/export routes, add isolated workbook integration tests, and then run the Stage 4 closure commands. A successful migration should reduce Controller-Mapper findings from 2 to 1 without changing any front-end API or import behavior.

### Completed: Stage 4 GMS Goods Excel Read-Service Migration

- Added `GmsGoodsExcelReadService` under `feature.gms.application.product`. It owns the two read-only workbooks: the import template and the full product export. It reads categories/brands through the existing GMS services, member types through `SysDictDetailService`, goods through `GmsGoodsService`, and the batch price matrix through `GmsGoodsPriceService`.
- `GmsGoodsExcelController` now has only `GmsGoodsExcelReadService` and the existing `GmsGoodsExcelManager` as dependencies. Its GET template/export routes delegate directly to the read service; its POST import route remains unchanged and continues to use the existing transactional manager. Routes, upload field, response MIME/header encoding, sheet/file names, dynamic headers, dropdowns, sample row and export field mapping were preserved.
- Added `GmsGoodsExcelReadServiceIntegrationTest`. It opens the generated workbooks and verifies that template/export share the dynamic header, template validation data is present, and export preserves category, brand, sale status, discount status and member price. A separate empty-goods case verifies no price-matrix lookup occurs and a header-only workbook is still generated.
- Full isolated `money_pos_test` verification passed with 15 current test classes and 30 tests (0 failures / 0 errors). `mvn package -DskipTests` and `architecture-scan.sh --check-new` passed. The structural scan is now 1 Controller-Mapper file, 0 cross-Feature `ServiceImpl`/Mapper imports, 0 `platform → feature` imports and 64 shared Entity importers; only `UmsMemberImportController` remains. The next smallest task is to inventory its template, export, import and batch-coupon behaviors before selecting a safe migration slice.

### Completed: Stage 4 UMS Member-Import Boundary Inventory

- Published `MoneyPOS-Stage4-Ums-Member-Import-Inventory.md` after reviewing all four routes. `POST /ums/member/import` and `POST /ums/member/batch-issue-voucher` already delegate to `UmsMemberService` and its transaction-bearing import/asset operations; both are intentionally excluded from the Controller migration.
- Selected `GET /ums/member/template` as the first safe slice. It is read-only and needs only GMS brand names plus SYS member-type labels. A dedicated UMS template service can use the existing GMS and SYS service interfaces while preserving the dynamic brand-column contract used by the importer.
- The full member-asset export remains a follow-up slice. It combines UMS master data and brand levels with TRADE-owned unused-coupon counts, so it requires a narrow TRADE aggregation contract returning member-ID-to-count data rather than a new UMS dependency on `PosMemberCouponMapper` or its Entity. The template service should own shared header metadata so export cannot drift from the import template.
- The next smallest task is to implement the template-only service and its workbook regression test. This does not yet reduce the last Controller-Mapper scan finding, but it establishes the shared metadata boundary needed for the later, safe export migration.

### Completed: Stage 4 UMS Member Excel Template Migration

- Added `UmsMemberExcelTemplateService` and delegated `GET /ums/member/template` to it. The service owns the fixed five member columns, dynamic brand columns, member-level dropdowns, sample row, worksheet and encoded response headers; `POST /ums/member/import` and batch voucher issue retain their existing UMS transaction services unchanged.
- The same service provides the dynamic export header and level-code-to-Chinese-name map, so the existing export route now shares the template contract instead of maintaining a second GMS/SYS Mapper query. It uses `GmsBrandService.getBrandSelect()` and the new `SysDictDetailService.getValueToCnDescMap()` narrow, ordered map operation; this avoids exposing GMS or SYS persistence Entities to the new UMS service.
- Added and passed `UmsMemberExcelTemplateServiceIntegrationTest`, which opens the generated workbook and verifies dynamic brand columns, the member-level dropdown, sample row, worksheet and response header. Full isolated `money_pos_test` verification passed with 16 current test classes and 31 tests (0 failures / 0 errors); `mvn package -DskipTests` and `architecture-scan.sh --check-new` passed.
- The scan remains 1 Controller-Mapper file, 0 cross-Feature `ServiceImpl`/Mapper imports, 0 `platform → feature` imports and 64 shared Entity importers. The remaining direct Mapper use is solely the member asset export path. The next smallest task is to design a TRADE-owned unused-coupon-count aggregation contract, then migrate that export as a read-only UMS service.

### Completed: Stage 4 UMS Member Asset Excel Export Migration

- Added `MemberCouponQueryService` in TRADE. Its implementation alone uses `PosMemberCouponMapper` and returns only a `Map<Long, Long>` of member IDs to `UNUSED` coupon counts; it exposes neither the coupon entity nor its Mapper to UMS.
- Added `UmsMemberAssetExcelExportService` and delegated `GET /ums/member/export` to it. The service keeps UMS-owned member and brand-level reads internal, consumes GMS brand selection DTOs and the shared template metadata, and writes the same `门店全量会员资产大表.xlsx` / `老会员数据` output. Import and batch voucher issuance routes are untouched.
- Added and passed `UmsMemberAssetExcelExportServiceIntegrationTest` against `money_pos_test`, verifying a real member's balance/coupon assets, brand-level translation, and that one `UNUSED` plus one `USED` coupon exports as one remaining voucher. The Controller-Mapper scan is now 0, with cross-Feature `ServiceImpl`/Mapper and `platform → feature` scans also at 0; shared Entity imports are 65 and remain tracked, non-blocking compatibility debt.
- The next smallest task is to run the Stage 4 full verification and reconcile the checklist's remaining non-code acceptance items before declaring the stage implementation complete.

### Completed: Stage 4 Closure Review

- Reconciled every implementation entry in `MoneyPOS-Stage-4-Regression-Prevention-Checklist.md`: sections 4.0 through 4.6.6c are complete, with no unchecked Stage 4 implementation item remaining. The final isolated verification has 17 test classes and 32 tests with zero failures/errors; `mvn package -DskipTests` and `architecture-scan.sh --check-new` passed.
- The current structural scan is 0 Controller→Mapper imports, 0 cross-Feature `ServiceImpl`/Mapper imports and 0 `platform → feature` imports. The original seven Controller findings have therefore been removed without changing public routes, database tables or Flyway scripts.
- Stage 4 implementation is closed. Two deliberate, non-blocking follow-ups remain outside its scope: the P1 shared-Entity-to-snapshot-DTO migration (65 shared Entity importers remain report-only), and a separately authorized decision on Maven/CI integration for the local architecture gate. The Stage 3 Windows embedded-MariaDB and physical-printer exceptions also remain independent.

### Completed: P1.5.5 Member Asset Command Migration

- Added API-neutral settlement and refund commands plus handler interfaces. TRADE now converts its already-calculated checkout and refund results into those commands; no `NormalizedPaymentResult`, persistence Entity or Mapper crosses the command boundary.
- Moved member consumption, voucher FIFO conditional update and restoration, balance debit/refund, asset logs and last-visit update to UMS command handlers. Deleted TRADE's `PosAssetActionService`; `MemberAssetFacade` contains no direct UMS Mapper or Entity write.
- Existing isolated `mvn test` passed after the migration. P1.5.6 remains to add a genuine competing-voucher regression and repeat the complete validation/architecture-gate closure.

### Completed: P1.5.6 Member Asset Command Closure

- Extended `CheckoutIntegrationTest` with a real two-thread, independent-transaction race for one member voucher. Exactly one checkout succeeds; the other rolls back without a second order, stock deduction, member consumption or voucher use. The test supplies the same tenant/security context used by the normal integration fixture rather than bypassing the checkout pipeline.
- Added explicit FIFO verification for three dated vouchers and an idempotency verification for a repeated checkout `reqId`. Together with existing balance, full/partial refund and insufficient-voucher tests, the command boundary retains the previous transaction and asset behavior.
- Final isolated verification passed: 18 test classes / 39 tests, zero failures/errors; `mvn package -DskipTests` and `scripts/architecture-scan.sh --check-new` passed. P1.5 is closed; P1.6 remains for the broader P1 call-surface and ownership review.

### Completed: P1.6.1 Call-Surface Review and Additions-Only Gate

- Published `MoneyPOS-P1.6-Call-Surface-Review.md` and reconciled the ownership plan. It distinguishes completed P1 contracts from the remaining inventory write boundary, pricing/benefit compatibility calls, POS/member brand display calls, and separately scoped FIN/HOME read models. P1 is therefore not prematurely closed.
- Extended `architecture-scan.sh --check-new` with a fourth additions-only check: direct imports of another Feature's implementation package. The nine existing cross-Feature imports are an explicit baseline; any new one fails and must be expressed through `money-app-api` scene contracts instead. Shared Entity imports remain non-blocking because 68 file imports include lawful owner-internal persistence work.
- Verified `bash -n`, the new gate against the baseline, and unknown-option exit behavior. The gate reports 0 Controller→Mapper, 0 cross-Feature `ServiceImpl`/Mapper, 9 baseline cross-Feature implementation imports and 0 platform→feature imports. The next smallest implementation task is P1.6.2 / P1.4c: move TRADE's GMS stock writes behind inventory commands.

### Completed: P1.6.2 / P1.4c GMS Stock Command Migration

- Added API-neutral `SaleStockCommand` and `RefundStockCommand` boundaries with line snapshots and GMS handlers. `GoodsStockFacade` now only adapts existing TRADE DTOs; `PosInventoryActionService` and all direct GMS Entity/Mapper/service writes were removed from TRADE.
- GMS owns sale stock deduction, package allocation and component traversal, stock logs, sale-out documents, refund return documents and cost calculation. The handlers deliberately join the existing `CheckoutOrchestrator` and refund transactions, preserving all-or-nothing order, stock and member-asset behavior.
- Added a checkout-to-refund package regression that verifies both the package quota and its physical component decrement and restore. Stage 0 `CheckoutIntegrationTest`, isolated full `mvn test`, `mvn package -DskipTests`, and the architecture gate passed. The cross-Feature implementation baseline fell from 9 to 8; shared Entity imports fell from 68 to 67 and remain non-blocking owner-internal debt.

### Completed: P1.6.3 Checkout Pricing Benefit Query

- Added API-neutral `CheckoutPricingBenefitQuery` and `CheckoutPricingBenefitSnapshot`. The snapshot deliberately contains only the member's brand-level mapping and the selected voucher rule's threshold/discount amounts; it exposes neither UMS Entities nor Mappers.
- Moved the matching reads to UMS. `PosCalculationEngine` now obtains one benefit snapshot for each trial calculation and retains the existing member-price, full-reduction threshold and missing-rule behavior.
- Added `CheckoutPricingBenefitQueryServiceIntegrationTest`, including tenant/security fixture context and assertions for brand-key normalization, voucher amounts and empty requests. Full verification passed: 19 test classes / 41 tests, `mvn package -DskipTests`, `scripts/architecture-scan.sh --check-new`, and `git diff --check`. The scan remains at 0 Controller→Mapper, 0 cross-Feature ServiceImpl/Mapper, 8 baseline cross-Feature implementation imports and 67 report-only shared Entity importers.
- The next smallest task is P1.6.4: provide narrow UMS/GMS queries for TRADE POS member benefit and brand-name display.

### Completed: P1.6.4 POS Member Display Queries

- Added API-neutral `PosMemberBenefitQuery` with coupon-rule and member-benefit snapshots. UMS alone reads the `UNUSED` member vouchers and coupon rules, returning only display fields and per-rule counts; TRADE no longer imports their Mapper or Entity.
- Added GMS-owned `BrandNameQuery`, which translates only requested numeric brand IDs to names. `PosServiceImpl` requests the IDs found in the returned member profiles, retains the established dictionary translation and unknown-brand fallback, and no longer depends on `GmsBrandService` or `GmsBrand`.
- Extended the POS member-controller integration regression to verify the stable coupon-rule endpoint plus a real member's brand name, level, voucher count and rule summary. Full verification passed: 19 test classes / 41 tests, `mvn package -DskipTests`, `scripts/architecture-scan.sh --check-new`, and `git diff --check`. The gate remains at 0 Controller→Mapper, 0 cross-Feature ServiceImpl/Mapper, 7 baseline cross-Feature implementation imports and 69 report-only shared Entity importers.
- The next smallest task is P1.6.5: replace UMS member profile/template/export reads of GMS brand services with the brand selection query contract.

### Completed: P1.6.5 UMS Brand Selection Query and P1 Closure

- Added GMS-owned `BrandSelectionQuery` / `BrandSelectionSnapshot`, carrying only brand ID and name. The existing GMS query handler now supports both requested-ID name translation and the dynamic full selection list without exposing `GmsBrand`.
- Migrated UMS member profile, Excel template, asset export and workbook import to the selection/name contracts. UMS no longer imports `GmsBrandService`, `GmsBrandMapper` or `GmsBrand`; templates, exports and imports use the same list, preventing dynamic brand-column drift.
- Added a real workbook-import regression that verifies a dynamic brand column persists its level and that the imported member profile still renders the expected brand/level text. Existing template and asset-export workbook regressions remain green. Full verification passed: 20 test classes / 42 tests, `mvn package -DskipTests`, `scripts/architecture-scan.sh --check-new`, and `git diff --check`. Structural baseline is now 0 Controller→Mapper, 0 cross-Feature ServiceImpl/Mapper, 4 cross-Feature implementation imports and 69 report-only shared Entity importers.
- P1 is closed: every identified TRADE↔UMS/GMS operational scenario now collaborates through scene contracts. FIN/HOME report read models and Maven physical-split reassessment remain separately scoped P2/future work.

### Completed: P2.0 FIN/HOME Report Read-Model Inventory

- Published `MoneyPOS-P2-Report-Read-Model-Checklist.md` and `MoneyPOS-P2.0-Report-Read-Model-Inventory.md`. The baseline separates HOME's self-owned `OmsDailySummary` writes from its cross-domain read inputs, and separates FIN dashboard, handover, analysis, risk and waterfall formulas rather than treating them as one migration.
- Identified the current four cross-Feature implementation imports as FIN→UMS and HOME→GMS. The inventory also records direct TRADE/GMS/UMS Mapper and Entity read surfaces that the implementation-import gate intentionally does not count.
- No production code, route, SQL formula, table or report behavior changed. P2.1 is the next safe slice: replace the two HOME calls to GMS inventory valuation with one GMS-owned single-value query contract.

### Completed: P2.1 HOME Inventory Valuation Query

- Added API-neutral `InventoryValuationQuery`, implemented inside GMS by delegating to the existing stock valuation service. The formula remains exactly `SUM(stock * purchase_price)` for `stock > 0`, with zero fallback and the existing GMS error behavior.
- Replaced all three HOME call sites across `HomeServiceImpl` and `DecisionEngineServiceImpl`. HOME now consumes only the single valuation value; the `/home/count` response, HOME daily snapshot generation and `HomeService.homeCount()` retain their fields and timing.
- Extended `HomeCountSnapshotCharacterizationTest` to assert that the endpoint response, stored daily snapshot and statistics service agree with the GMS contract. Full verification passed: 20 test classes / 42 tests, `mvn package -DskipTests`, `scripts/architecture-scan.sh --check-new`, and `git diff --check`. Cross-Feature implementation imports fell from 4 to 2; shared Entity importers remain 69 and report-only.
- The next smallest task is P2.2: inventory HOME's remaining TRADE order aggregation and UMS membership-chart reads before defining their independent snapshots.

### Completed: P2.2 HOME Order and Membership Read-Snapshot Design

- Published `MoneyPOS-P2.2-Home-Read-Snapshot-Design.md` and updated the P2 checklist/baseline. The design records the existing behavior for the service-level order counts, HOME daily snapshot, comprehensive dashboard comparisons, sales trend, brand pie and member-level chart before any production migration.
- It deliberately separates five TRADE query methods because the existing order states, date boundaries and amount formulas differ. HOME will retain time-range conversion, DTO assembly, trends, alert behavior and all `OmsDailySummary` compensation/write timing.
- UMS will own the active member brand-level counts and use the existing GMS `BrandNameQuery` for narrow brand-name translation; this removes the intended HOME→UMS Mapper dependency without exposing UMS entities.
- No production code, route, SQL, database object or report output changed. The next smallest task is P2.2.1: introduce TRADE `HomeOrderReadQuery.summarizeHomeCount`, migrate only the four `HomeService.homeCount()` order aggregations, and characterize their status/zero/time-boundary behavior.

### Completed: P2.2.1 HOME Count Order Read Query

- Added API-neutral `HomeOrderReadQuery` and immutable `HomeOrderReadSnapshot`. TRADE owns the existing `OmsOrderMapper` aggregate, including its `PAID`/`PARTIAL_REFUNDED`/`REFUNDED` financial-status set, final-sales-to-pay fallback, zero defaults and right-open time boundaries.
- Migrated all four `HomeService.homeCount()` aggregates (today, month, year and total). HOME now only converts the narrow snapshot into its existing `OrderCountVO`; it no longer imports TRADE's order Entity, Mapper or query wrapper for this entry.
- Extended `HomeCountSnapshotCharacterizationTest` with real test-database orders. It verifies included/excluded statuses, the midnight boundary, amounts, costs, profits and agreement between the TRADE contract and HOME output. The test runs inside a rollback transaction.
- The next smallest task is P2.2.2: replace TRADE reads in HOME daily snapshot generation and the comprehensive dashboard while retaining HOME's snapshot write, compensation and alert timing.

### Completed: P2.2.2 HOME Daily Snapshot and Comprehensive Dashboard Order Reads

- Extended TRADE `HomeOrderReadQuery` with separate immutable daily-snapshot and comprehensive-dashboard aggregates. Their existing state sets remain deliberately different: the daily snapshot uses `PAID`/`PARTIAL_REFUNDED`, while the comprehensive dashboard uses `PAID`/`COMPLETED`/`PARTIAL_REFUNDED`.
- `DecisionEngineServiceImpl` no longer imports `OmsOrderAnalysisMapper` or embeds an `oms_order` JDBC query. It retains its own daily-summary writes, seven-day compensation, average/alert calculations, UMS new-member count query, inventory valuation query and response DTO assembly.
- Extended the HOME characterization regression with real test orders to verify both state sets, the persisted snapshot's sales/profit/order count, and the comprehensive month output. The test remains transaction-rolled-back.
- The next smallest task is P2.2.3: migrate the HOME sales-trend and brand-sales charts to TRADE-owned reads while retaining the `today` seven-day display behavior and all four selected time ranges.

### Completed: P2.2.3 HOME Sales Trend and Brand Chart Reads

- Extended TRADE `HomeOrderReadQuery` with trend-point and brand-sales snapshots. The TRADE implementation keeps the existing `OmsOrderDetailMapper` SQL inside its owned boundary, including its status sets, right-open time ranges, trend grouping and brand revenue's returned-quantity deduction.
- `HomeServiceImpl` now maps only API snapshots to its existing `TrendChartVO` and `BrandPieVO`; it no longer imports the order-detail Mapper. The `today` seven-day trend rule and all `today`/`month`/`year`/`total` ranges are unchanged.
- Extended HOME characterization coverage with real order and order-detail fixtures. It verifies the 20 sales trend increment, 10 net brand-revenue increment after one returned item, and exact HOME-to-TRADE output correspondence in every time range.
- The next smallest task is P2.2.4: move the member-level chart to a UMS-owned query contract while retaining the brand-name, level-code and member-count response fields.

### Completed: P2.2.4 HOME Member-Level Distribution Query

- Added API-neutral `HomeMemberDistributionQuery` and its immutable snapshot. UMS owns the aggregation of active members by brand and level, while the existing GMS `BrandNameQuery` translates requested numeric brand IDs without exposing GMS entities to HOME.
- `HomeServiceImpl` no longer imports `UmsMemberBrandLevelMapper`; it maps only the UMS snapshot to the existing `MemberBarVO`. The member-level chart remains a current-state view and deliberately ignores the selected order-chart range.
- Added an integration regression with a unique GMS brand, one active member and one logically deleted member. It verifies the resolved brand name, level code, count excluding the deleted member, and the same bar output for `today` and `month`.
- The next smallest task is P2.2.5: complete the HOME report-snapshot acceptance review with endpoint-output, full automated and architecture-gate verification.

### Completed: P2.2.5 HOME Report Snapshot Acceptance

- The HOME controller characterization suite now enters `GET /home/charts` through `HomeController`, covering `today`, `month`, `year` and `total`; the existing count-controller regression continues to verify the `/home/count` response shape, daily snapshot create/update behavior and inventory value.
- The completed P2.2 boundary is: HOME only assembles its page data and owns `OmsDailySummary`; TRADE owns all HOME order/line read formulas through `HomeOrderReadQuery`; UMS owns active member-level distribution through `HomeMemberDistributionQuery`; GMS supplies only inventory valuation and requested brand names through narrow contracts.
- No route, HTTP field, database object, Flyway history, page component, day-snapshot write timing or alert timing changed. Full Maven regression, packaging and the additions-only architecture gate passed. Prior manual front-end verification remains applicable because the routes and fields are unchanged.
- The next smallest task is P2.3: inventory FIN financial-dashboard reads by their TRADE order/payment, GMS inventory-document and UMS member-asset owners before introducing any FIN snapshot contracts.

### Completed: P2.3 FIN Financial-Dashboard Read-Snapshot Design

- Published `MoneyPOS-P2.3-Finance-Dashboard-Read-Snapshot-Design.md` and updated the P2 checklist/baseline. The design fixes the existing daily closed time boundaries, three distinct TRADE financial formulas, GMS negative-inventory-loss rule, UMS recharge/reversal and positive-balance rules, plus the asset-dashboard zero fallbacks before any production migration.
- Defined three owner-specific API query boundaries: TRADE order/payment, GMS financial inventory documents and UMS member assets. FIN retains only date handling and `FinanceDashboardAssembler` presentation calculations; the current cross-owner `FinanceReportMapper` asset reads move into their respective P2.3 owner slices, while its TRADE/GMS waterfall query remains P2.4 work.
- No production source, route, SQL, database object or financial result changed in this design step. The first implementation slice is deliberately GMS inventory documents because it is read-only, has no cross-owner join, and affects only the already-isolated inventory-loss portion of gross profit.

### Completed: P2.3.1 GMS Financial Inventory-Document Snapshot

- Added API-neutral `FinanceInventoryDocumentQuery` and `FinanceInventoryDocumentSnapshot`. GMS owns the implementation and retains its `GmsInventoryDocMapper`, legacy `OUTBOUND`/`CHECK` type filter and closed calendar-day boundary; the snapshot exposes only document type and total amount.
- `FinanceDashboardServiceImpl` now depends on the GMS query contract, while `FinanceDashboardAssembler` consumes the narrow snapshot. FIN no longer imports or reads `GmsInventoryDoc` or `GmsInventoryDocMapper`; routes, date parsing and the rule that only a negative selected document amount reduces gross profit remain unchanged.
- Extended `FinanceFeatureIntegrationTest` with a transaction-rolled-back characterization: an `OUTBOUND -5.50` document reduces gross profit by `5.50`, while a positive `CHECK` and an excluded `INBOUND` do not affect that loss. The targeted FIN regression passed; the current full Maven run passed 46 tests with zero failures/errors, as did `mvn package -DskipTests`, `git diff --check` and `architecture-scan.sh --check-new`.
- The architecture scan remains 0 Controller→Mapper, 0 cross-Feature ServiceImpl/Mapper and 0 platform→feature additions. It still reports the two pre-existing cross-Feature implementation imports and 70 report-only shared-Entity importers. The next smallest task is P2.3.2: replace FIN's UMS member logs, balance service and asset-composition SQL with UMS-owned member-asset snapshots.

### Completed: P2.3.2 UMS Financial Member-Asset Snapshot

- Added API-neutral `FinanceMemberAssetQuery` with immutable recharge-entry, daily-recharge-total and principal/gift-asset snapshots. UMS retains member-log and member Mapper access; FIN now receives only the values needed for external income, seven-day recharge trend, positive-balance debt and asset ratio presentation.
- Preserved all compatibility-sensitive behavior: only `RECHARGE` and `REVERSAL` logs participate, calendar days remain closed ranges, negative balance is excluded from debt, and asset composition still ignores the tenant-line interceptor while filtering `deleted = 0`. The now-unused cross-owner member-asset SQL was removed from `FinanceReportMapper`.
- Extended `FinanceFeatureIntegrationTest` with a rolled-back member fixture. It proves that a `+11` recharge and `-2` reversal add `9` to external income and today's trend, a `17` balance adds to debt, and a `17` principal / `3` gift addition is reflected in both UMS composition and FIN's rounded ratio. The targeted FIN suite and full Maven run passed (47 tests, zero failures/errors); package, diff check and architecture gate passed.
- FIN→UMS is no longer an implementation import. The scan now has 0 Controller→Mapper, 0 cross-Feature ServiceImpl/Mapper and 1 remaining cross-Feature implementation import (`TRADE→FIN`), with 71 report-only shared-Entity importers. The next smallest task is P2.3.3: move FIN's TRADE order/payment reads behind owner-specific snapshots.

### Completed: P2.3.3 TRADE Financial Order/Payment Snapshot

- Added the API-neutral `FinanceOrderPaymentQuery` and immutable order-metric, payment-summary, refund-base, channel-discount and asset-order snapshots. TRADE retains `OmsOrderMapper` and `OmsOrderPayMapper`; FIN now receives only the inputs its dashboard assembler needs.
- Preserved the existing financial-status set (`PAID`, `PARTIAL_REFUNDED`, `REFUNDED`) and all closed calendar-day boundaries. Payment summaries retain the full-refund zero rule plus `net_amount` with `pay_amount` fallback; refund trend remains `SUM(pay_amount) - SUM(final_sales_amount)` clamped to zero; channel discounts still group `actual_coupon_deduct` and `use_voucher_amount` by day.
- Extended `FinanceFeatureIntegrationTest` with rolled-back paid and fully-refunded orders/payments. It verifies core amounts, the distinct refund formulas, full-refund payment exclusion, cash channel input, coupon/voucher aggregates, and the order-side asset overview.
- The next smallest task is P2.3.4: remove any remaining FIN-dashboard cross-domain Mapper/Entity dependency, then perform the controlled-data, full-regression, packaging and additions-only architecture-gate closure.

### Completed: P2.3.4 FIN Dashboard Assembly Closure

- Removed the now-unused `FinanceReportMapper.getTodayAssetSummary()` cross-owner `oms_order` SQL. `FinanceReportMapper` retains only the explicitly deferred TRADE/GMS waterfall query for P2.4; the dashboard has no direct cross-domain Mapper or Entity dependency.
- The FIN dashboard now assembles all order/payment, inventory-document and member-asset inputs exclusively from the three API query contracts. Existing controlled fixtures cover core amounts, payment channels, full-refund net-payment exclusion, refund/recharge trends, channel discounts, inventory loss, debt and asset ratios.
- Full Maven regression, package build, whitespace validation and the additions-only architecture scan are the closure evidence. The next smallest task is P2.4: inventory and design the first owner-specific FIN specialist-report query without combining report formulas.

### Completed: P2.4.0 FIN Specialist-Report Inventory and First Contract Design

- Published `MoneyPOS-P2.4-Finance-Specialist-Report-Read-Model-Design.md`. It inventories the independent FIN specialist-report surfaces: risk control, shift handover, profit ranking, campaign review, operating analysis, traffic, audit and the TRADE/GMS waterfall. Their state sets, ranges, joins and formulas are explicitly not merged.
- Selected risk control as the first implementation slice because both reads are TRADE-owned `oms_order` audit projections with no GMS/UMS join and no transaction impact. Designed Java-8-compatible `FinanceRiskQuery` snapshots for cashier aggregates and abnormal-order rows; FIN will retain date parsing, loss/manual-discount/refund card aggregation and its existing response fields.
- No production source, SQL, route, database object, page field or test behavior changed in this design slice. The next smallest task is P2.4.1: implement only the TRADE risk query contract and migrate FIN's two audit Mapper reads.

### Completed: P2.4.1 TRADE Risk-Control Audit Snapshots

- Added API-neutral `FinanceRiskQuery`, `FinanceCashierRiskSnapshot` and `FinanceAbnormalOrderSnapshot`. TRADE owns the implementation and retains `OmsOrderAuditMapper`; FIN now consumes only the snapshots and maps them back to its unchanged `Map<String, Object>` response fields.
- Preserved FIN-owned closed date parsing, the source SQL's intentionally unfiltered order-state behavior, all three abnormal-order predicates, ascending-profit 50-row cap, and the card calculations for abnormal count, negative-profit loss, manual discount and refund count.
- Extended `FinanceFeatureIntegrationTest` with rolled-back negative-profit, large-manual-discount and refunded orders. It verifies TRADE snapshots, FIN cards and existing abnormal-row fields. The next smallest task is P2.4.2: design and migrate the shift-handover payment, discount and brand-contribution snapshots without changing their separate formulas.

### Completed: P2.4.2 FIN Shift-Handover Snapshots

- Added API-neutral TRADE `FinanceShiftHandoverQuery` with immutable payment, discount and brand-ID contribution snapshots. TRADE retains its order/payment/detail Mappers; `FinanceShiftServiceImpl` no longer directly imports any of them.
- Explicitly separated ownership of the legacy brand join: TRADE aggregates only `brandId`, revenue and coupon consumption from order details; FIN asks the existing GMS `BrandNameQuery` for display names and retains the established `无品牌/未知` fallback. No FIN DTO crosses into TRADE.
- Preserved all three independent formulas and filters: payment net amounts with full-refund zeroing, discount/refund aggregation and returned-quantity coupon allocation. A rolled-back FIN regression verifies cash, four discount fields and GMS brand-name resolution. The next smallest task is P2.4.3: migrate profit ranking and campaign review as separate TRADE query contracts.

### Completed: P2.4.3 FIN Profit Ranking and Campaign Review Snapshots

- Added API-neutral TRADE `FinanceProfitQuery` with separate immutable profit-ranking and campaign-review snapshots. FIN's profit service no longer imports order-detail or order-analysis Mappers, nor a TRADE marketing DTO.
- Preserved the intentionally different source formulas: profit ranking starts 30 days ago and includes `REFUNDED` order details after return-quantity adjustment; campaign review uses a closed three-month range with only `PAID`/`PARTIAL_REFUNDED` orders and separate voucher/member-coupon aggregates. FIN retains ROI rounding and descending sort.
- Extended the rolled-back FIN regression with a unique campaign order/detail, asserting ranking values, campaign discount/revenue/count and the rounded ROI. The next smallest task is P2.4.4: inventory and design the first operating-analysis, traffic or profit-audit owner query without mixing its SYS strategy reads or report formulas.

### Completed: P2.4.4 FIN Operating Analysis, Traffic and Profit-Audit Inventory

- Inventoried the remaining `OmsSalesAnalysisServiceImpl` direct reads by entry rather than treating them as one report: period metrics, dashboard charts, traffic, category/goods trends and profit audit have different owners, defaults, states, grouping and pagination behavior.
- Selected TRADE-owned period operating metrics as the first implementation slice. The proposed `FinanceOperatingAnalysisQuery` returns only immutable period, count and amount snapshots; FIN continues to parse ranges and calculate/display performance and summary-card fields.
- Explicitly deferred sales-dashboard charts, TRADE/SYS traffic composition, GMS display-name translation, and the TRADE profit-audit page. No source, SQL, route, database object or page field changed in this design-only task. The next smallest task is P2.4.4.1: implement and migrate the TRADE period metrics for FIN performance and summary cards.

### Completed: P2.4.4.1 FIN Operating Period-Metric Snapshots

- Added API-neutral TRADE `FinanceOperatingAnalysisQuery` and immutable `FinanceOperatingMetricSnapshot`. TRADE alone retains `OmsOrderAnalysisMapper` and converts its existing daily/weekly/monthly atomic results to the narrow API projection.
- Migrated only FIN `getPerformanceReport` and `countOrderAndSales`; FIN retains string/default range parsing, reverse performance display, customer-unit-price rounding, and sales/cost/profit total assembly. The sales dashboard remains deliberately on its own, not partially migrated.
- Extended the rolled-back FIN regression with day-start/day-end orders, `PAID` and `PARTIAL_REFUNDED` inclusions, `REFUNDED` and next-day exclusions, and daily/weekly/monthly assertions. It verifies the TRADE values and FIN performance/card output. The next smallest task is P2.4.4.2: design the separate sales-dashboard chart slices.

### Completed: P2.4.4.2 FIN Sales-Dashboard Query-Slice Design

- Split the single dashboard into four non-interchangeable inputs: the existing TRADE daily period metrics, TRADE order-detail goods ranking, TRADE brand-ID revenue, and TRADE order-history member/guest daily metrics. FIN retains date defaults, closed-range parsing, zero filling, chart labels and all card/ASP presentation calculations.
- Recorded that goods names are transaction-time TRADE facts, while brand display names belong to GMS. The implementation will return only brand IDs and amounts from TRADE, then use the existing GMS `BrandNameQuery` with the legacy `无品牌/未知` fallback; it will not read UMS current membership state because the historical member predicate is stored on orders.
- No production source, SQL, route, database object, page field or test behavior changed. The next smallest task is P2.4.4.2.1: implement the separate dashboard snapshots and migrate only this FIN entry.

### Completed: P2.4.4.2.1 FIN Sales-Dashboard Snapshots

- Added API-neutral TRADE `FinanceSalesDashboardQuery` and immutable snapshots for top-goods, brand-ID sales and daily member/guest metrics. TRADE retains `OmsOrderAnalysisMapper`; its brand aggregation now returns only `brand_id` and amount, with no `gms_brand` join.
- Migrated only `OmsSalesAnalysisServiceImpl.getSalesDashboard`. It reuses the existing TRADE daily-period query, maps top-goods facts to the unchanged response DTO, translates brand IDs through GMS `BrandNameQuery`, and retains the `无品牌/未知` fallback. FIN's assembler still owns date filling, card rules and ASP curves.
- Extended the rolled-back FIN regression with paid, partial-refunded, refunded, returned-to-zero, known-brand, null-brand, member and guest fixtures. It verifies owner snapshots and the unchanged dashboard totals, ranks, names and trends. The next smallest task is P2.4.4.3: design the separate TRADE/SYS traffic query slices.

### Completed: P2.4.4.3 FIN Traffic Query-Slice Design

- Separated the three TRADE order aggregates (hour, MySQL weekday and day-of-month) from SYS's global traffic thresholds and weekly/monthly windows. The design records each closed range, `PAID`/`PARTIAL_REFUNDED` state set, divisor and grouping key rather than treating traffic as one generic metric.
- FIN remains responsible for the external-to-MySQL weekday conversion, the fixed 28-day hourly window, strategy defaults, 24-hour zero fill, sample fields and `OUT`/`STAY` predicate. TRADE receives the FIN-owned divisor so it preserves the existing SQL average calculation; SYS supplies only four immutable strategy values.
- No production source, SQL, route, database object, page field or test behavior changed. The next smallest task is P2.4.4.3.1: implement and migrate the two owner contracts for FIN traffic.

### Completed: P2.4.4.3.1 FIN Traffic Snapshots

- Added API-neutral TRADE `FinanceTrafficQuery` with immutable hourly/time-key snapshots, and SYS `FinanceTrafficStrategyQuery` with the four required global strategy values. TRADE retains `OmsOrderTrafficMapper` and its database-side divisor calculation; SYS retains `SysStrategyMapper` and its global read semantics.
- Migrated all three FIN traffic methods. FIN retains the 28/90/180 defaults, `now` ranges, MySQL weekday mapping, divisors, zero behavior, response DTOs and `OUT`/`STAY` predicate, while no longer directly accesses the TRADE traffic or SYS strategy Mapper/Entity.
- Extended the rolled-back FIN regression with paid, partial-refunded and refunded orders plus a temporary global strategy. It verifies owner filtering, FIN hourly suggestion/sample values and weekly/monthly strategy divisors. The next smallest task is P2.4.4.4: design the category-sales and top-goods-trend slices.

### Completed: P2.4.4.4 FIN Category-Sales and Goods-Trend Query-Slice Design

- Separated category sales from selected-goods trends despite their shared order-detail source. Category aggregation returns TRADE category IDs and amounts while GMS owns current category labels; selected-goods trends retain transaction-time detail names and never need a GMS lookup.
- Recorded the compatibility-sensitive difference in return treatment: category sales sums net quantity before its positive `HAVING` test, while daily selected-goods trends sum `GREATEST(net quantity, 0)`. FIN keeps date parsing, empty-ID behavior, name fallbacks and continuous daily arrays.
- No production source, SQL, route, database object, page field or test behavior changed. The next smallest task is P2.4.4.4.1: implement and migrate the two TRADE/GMS query boundaries.

### Completed: P2.4.4.4.1 FIN Category-Sales and Goods-Trend Snapshots

- Added Java 8 API-neutral TRADE `FinanceProductAnalysisQuery` with immutable category-sales and daily-goods snapshots, plus GMS `GoodsCategoryNameQuery` for bulk category-name translation. TRADE retains the order-detail SQL; its category aggregation now returns only `category_id` and numeric facts, without a GMS table join.
- Migrated `OmsSalesAnalysisServiceImpl.getCategorySales` and `getTopGoodsTrend`. FIN retains closed-range/default date parsing, TRADE SQL order, `未分类` and `商品 ID:<id>` fallbacks, and the existing per-day zero-filled chart arrays; GMS names are applied only to category display rows, never to historical detail names.
- Extended the FIN regression with paid, partial-refunded, fully-refunded and fully-returned detail rows across two days. It verifies state filtering, both return formulas, category-name and missing-category display behavior, historical product name, missing-product fallback and date filling. The next smallest task is P2.4.4.5: migrate FIN profit audit while retaining its pagination and audit filters.

### Completed: P2.4.4.5 FIN Profit-Audit Page Snapshots

- Added Java 8 API-neutral TRADE `FinanceProfitAuditQuery` with immutable page and row snapshots. TRADE retains `OmsOrderAuditMapper` and its detail-price SQL; FIN no longer imports or calls that mapper, MyBatis `Page`, or `PageUtil` for this entry.
- Preserved the three audit states, exact order-number filter, `ANOMALY`-only missing-cost/negative-margin predicate, original calculated fields, page metadata, and `create_time DESC` ordering without inventing a secondary tie-breaker. FIN maps owner snapshots back to the unchanged `PageVO<ProfitAuditVO>` response.
- Extended the rolled-back FIN regression with paid, partial-refunded and refunded rows. It verifies order-number filtering, missing cost, negative unit profit, `ANOMALY` exclusion of normal rows, page size and FIN-to-TRADE page consistency. The next smallest task is P2.4.5: migrate the daily waterfall while keeping separate TRADE/GMS inputs and FIN formulas.

### Completed: P2.4.5 FIN Daily-Waterfall Owner Snapshots

- Added Java 8 API-neutral TRADE `FinanceWaterfallOrderQuery` and GMS `FinanceWaterfallInventoryQuery`, each with immutable daily snapshots. TRADE owns the financial-order state and amount formulas; GMS owns the `INBOUND` inventory-document procurement aggregation. No cross-owner Mapper was created.
- Deleted FIN `FinanceReportMapper` and migrated `FinanceReportServiceImpl` to merge owner snapshots by the original date key in descending order. FIN retains null-query short-circuiting, zero values for absent owner inputs, and the existing `FinanceWaterfallVO` fields; routes, tables and transaction boundaries are unchanged.
- Extended the rolled-back FIN regression with paid, partial-refunded and refunded orders plus inbound and excluded outbound inventory documents. It verifies owner formulas, state and document filters, closed range, totals and FIN merge output. The next smallest task is P2.5: final FIN/HOME acceptance and architecture-debt review.

### Completed: P2.5 FIN/HOME Final Acceptance and Architecture-Debt Review

- Closed the two read-side residues found during final review: FIN marketing ROI now maps TRADE campaign snapshots rather than directly calling `OmsOrderAnalysisMapper`; HOME's daily new-member count now comes from UMS `HomeDailyMemberQuery` rather than a `JdbcTemplate` read of `ums_member`. HOME retains only its own `OmsDailySummary` Entity/Mapper and write-on-read behavior.
- Extended FIN/HOME integration regression for the ROI rule-type/amount mapping and the UMS-owned daily new-member count. The isolated FIN/HOME suite, full Maven test suite, package build, additions-only architecture gate and whitespace check passed.
- Final scan reports no Controller → Mapper imports, no cross-Feature `ServiceImpl`/Mapper imports and no new structural findings. It records the pre-existing TRADE → FIN controller compatibility import and 73 shared-Entity importing Feature files as non-blocking, separately owned debt; P2 does not authorize Entity physical moves. Final evidence is in `MoneyPOS-P2-Final-Acceptance-Review.md`. P2 is complete; any Entity-ownership convergence is a new independent task.

### Completed: Post-P2 TRADE → FIN `OmsOrderController` Compatibility-Call Inventory and Design

- Inventoried the sole baseline TRADE → FIN implementation import in `OmsOrderController`. It is limited to the OMS statistics and profit-audit routes; their route names, permission keys, request binding, response DTOs and frontend callers are recorded in `MoneyPOS-Post-P2-Trade-Fin-Controller-Compatibility-Design.md`.
- Designed two independent TRADE application adapters over the existing TRADE-owned `FinanceOperatingAnalysisQuery` and `FinanceProfitAuditQuery`. This preserves the different aggregate versus paged-audit formulas and lets FIN retain its own `/oms/analysis/*` report composition without remaining a dependency of the TRADE controller.
- This is a design-only debt task: no production source, SQL, route, database object, page field or test behavior changed. The next smallest task is implementation of the two TRADE controller-compatibility adapters and replacement of the `OmsOrderController` FIN injection.

### Completed: P2.6 TRADE OMS Controller Compatibility Adapters

- Added separate TRADE application adapters for the existing OMS statistics and profit-audit routes. They consume the established TRADE-owned period-metric and profit-audit contracts, respectively; no FIN Entity, Mapper or implementation service is imported by TRADE.
- Replaced `OmsOrderController`'s FIN `OmsSalesAnalysisService` injection while retaining both route paths, permission keys, parameter binding and legacy `OrderCountVO`/`PageVO<ProfitAuditVO>` responses. FIN's `/oms/analysis/*` reporting service remains unchanged.
- Extended the isolated regression to exercise both TRADE adapters against closed-range operating metrics and paged `ANOMALY` audit output. The next smallest task is post-P2 Entity ownership convergence: inventory the first shared Entity whose physical package can be replaced by owner-local persistence and a narrow API DTO.

### Completed: Post-P2 Architecture-Debt Backlog Baseline

- Added `MoneyPOS-Architecture-Debt-Backlog.md` to give all remaining architecture work stable `AD-*` identifiers rather than extending the completed P2 sequence. It distinguishes closed read-side work from the uncompleted HOME `DecisionEngine` write boundary, shared Entity physical ownership, API implementation-type leakage, CI governance, module-split prerequisites and Java baseline assessment.
- `DecisionEngine` is explicitly classified as partially complete: P2 closed its cross-owner read inputs, but HOME still performs compensation and same-day `OmsDailySummary` insert/update during the dashboard read path. Its write-boundary behavior was deliberately preserved by P2 and is now tracked as AD-1; no source or runtime behavior changed in this documentation task.
- Environment-only Windows embedded-MariaDB and receipt-printer acceptance remain separately identified as `ENV-*` items, not disguised as source-code architecture work. The next smallest task is AD-1.1: characterize the HOME snapshot write timing, idempotency and concurrency window before proposing a command-boundary change.

### Completed: AD-1.1 HOME `DecisionEngine` Write Characterization

- Confirmed that `GET /home/count` is the sole production trigger of `DecisionEngine`: it compensates only missing `today-1` through `today-7` snapshots, always regenerates today's snapshot, then reads the prior-seven-day average for alerts. It has no scheduler or `@Transactional` boundary.
- Added HOME characterization coverage for same-day create/overwrite idempotency and for the strict seven-day compensation window. Existing inputs and response fields remain unchanged; no production write logic, route, schema or Flyway was modified.
- Recorded the actual concurrency posture: `uk_record_date` prevents duplicate rows, but the check-then-insert path has no lock/retry and concurrent updates use last-writer-wins semantics. The next smallest task is AD-1.2: design an explicit HOME snapshot command model and a compatibility migration plan before changing that behavior.

### Completed: AD-1.2 HOME Daily-Snapshot Command Model Design

- Designed HOME-local assembler, command, writer and dashboard-query responsibilities. Historical compensation becomes an atomic insert-if-absent and today's refresh an atomic upsert over the existing `uk_record_date`; no new Flyway, cross-Feature command or JVM-only lock is proposed.
- Defined a safe two-stage migration: AD-1.3a extracts commands and removes the check-then-insert race while preserving GET-triggered refresh exactly; AD-1.3b makes GET read-only only after the user chooses and authorizes a freshness strategy (scheduler or transaction event).
- Preserved all current report semantics, including seven-day compensation, today overwrite, order states/boundaries, alert window and the historical use of current inventory valuation. No production source, SQL, route, schema, Flyway or test behavior changed in this design-only task. The next smallest task is AD-1.3a: implement HOME command/query separation and atomic daily-snapshot writes without changing GET triggering.

### Completed: AD-1.3a HOME Command/Query Split and Atomic Daily-Snapshot Writes

- Implemented HOME-local immutable snapshot assembly, command service, atomic writer and dashboard query service. The `DecisionEngine` now only preserves the existing GET compatibility sequence while command and query responsibilities are isolated.
- Historical compensation uses an atomic insert-if-absent on the existing `uk_record_date`; today's refresh uses an atomic upsert. The refresh updates only snapshot-owned metrics and preserves `member_recharge`.
- Extended HOME characterization coverage for that preservation and ran it against `money_pos_test`. No route, response field, database table, Flyway, transaction boundary of unrelated flows or GET trigger timing changed. `AD-1.3b` remains intentionally open pending a user-selected scheduler or transaction-event freshness strategy.

### Completed: AD-1.3b HOME Scheduled Snapshot Refresh and Read-Only GET

- Selected and implemented the bounded-staleness scheduler strategy: refresh once after application readiness and every five minutes thereafter, including the existing seven-day missing-snapshot compensation. `GET /home/count` now performs no snapshot command.
- Added an overlap guard and failure handling: concurrent triggers are skipped, a failing assembly/write logs the failure and releases the next run, while the existing atomic writer leaves the prior valid row intact. Read-only dashboard fallback supplies zero values if no row is available yet.
- Fixed the tenant interceptor's background-thread behavior to use the configured default tenant when no HTTP request exists, which allows startup and scheduled work to operate correctly. HOME integration and task-level concurrency/recovery coverage verify the new boundary.

### Completed: AD-3.1 Member-Profile API DTO Isolation

- Re-audited the current member-profile ranking path and found one implementation-type leak: `UmsMemberService.getTop20Goods()` and `UmsMemberController` publicly named `UmsMemberServiceImpl.MemberGoodsRankVO`.
- Replaced that nested implementation DTO with the API-module top-level `MemberGoodsRankVO`; the route, SQL ranking semantics and `goodsName` / `buyCount` JSON fields remain unchanged. Integration coverage exercises the exposed service contract and its fields; the controller compiles directly against the same top-level DTO.
- No Entity, Mapper ownership, database table, Flyway or cross-Feature command flow changed. The next smallest task is AD-3.2: audit actual cross-Feature `IService<Entity>` call sites before proposing any replacement contracts.

### Completed: AD-3.2 Cross-Feature `IService<Entity>` Call Audit

- Audited 20 current `IService<Entity>` interfaces and their production consumers. No UMS or TRADE service exposes an actual cross-Feature entity-service call after P1/P2; same-Feature use remains legitimate persistence implementation.
- Found two remaining risks: business Features receive `SysDictDetail` from the SYS dictionary service, and the legacy POS goods facade calls `GmsGoodsService.lambdaQuery()` and reads `GmsGoods`. The complete ownership and caller matrix is recorded in `MoneyPOS-AD-3.2-IService-Entity-Call-Audit.md`.
- Chose the smallest next implementation slice: AD-3.3 replaces the SYS dictionary entity reads with the already available value-to-description map contract. The GMS→POS search contract remains a separate AD-3.4 design task, preventing product-search and dictionary semantics from being mixed.

### Completed: AD-3.3 SYS Dictionary Read-Contract Migration

- Replaced TRADE, GMS and UMS consumption of `SysDictDetail` and its mapper with the existing SYS value-to-description map contract. This includes both display translation and the Excel import name-to-code reverse maps.
- Preserved payment lookup case-insensitivity, order-status fallback, member-type filtering and GMS Excel price-column ordering. SYS management-side Entity access remains inside SYS.
- Feature-source verification confirms no remaining `listByDict()`, `SysDictDetail` or `SysDictDetailMapper` use. GMS/UMS import and template tests plus TRADE checkout regression cover the migrated semantics. The next smallest task is AD-3.4: design the GMS-owned POS product-search snapshot.

### Completed: AD-3.4 GMS→POS Goods-Search Snapshot Design

- Audited the legacy `/gms/goods/pos-search` Facade against the existing `PosGoodsCatalogQuery`. The latter belongs to a different TRADE POS response and cannot be reused: it has a smaller field set, uppercases mnemonic input, omits the legacy status expression and does not carry the compatibility route's response shape.
- Published `MoneyPOS-AD-3.4-Gms-Pos-Goods-Search-Snapshot-Design.md`, defining a separate GMS-owned, Java 8 immutable snapshot and a narrow query port. It fixes the legacy fields, raw keyword behavior, level-price ownership, response adaptation and the explicit exclusion of SYS brand-coupon policy.
- The inventory records that the ungrouped legacy MyBatis expression applies `SALE` only to the mnemonic-code arm under SQL precedence. This is an externally observable compatibility fact, so the next implementation must characterize and preserve it rather than silently turn this boundary refactor into a product filter change. The next smallest task is AD-3.4.1: implement the GMS snapshot and migrate the Facade's GMS input.

### Completed: AD-3.4.1 GMS→POS Goods-Search Snapshot Migration

- Added the Java 8, Entity-free `LegacyPosGoodsSearchQuery` and immutable `LegacyPosGoodsSearchSnapshot` in `money-app-api`. GMS implements the contract internally through `GmsGoodsMapper` and `GmsGoodsPriceService`; the existing `PosGoodsCatalogQuery` remains unchanged for the separate TRADE POS catalog path.
- Migrated `GoodsPosFacade` to consume only the new query snapshot, map it field-by-field to the unchanged `GmsGoodsVO`, and retain its existing SYS brand coupon-policy sanitization. It no longer imports `GmsGoodsService`, `GmsGoods`, `PosSkuLevelPrice`, or `PosSkuLevelPriceMapper`, and no longer calls `lambdaQuery()`.
- Added `GoodsPosFacadeIntegrationTest` against `money_pos_test`. It locks the legacy ungrouped predicate (non-`SALE` barcode/name matches remain visible while non-`SALE` mnemonic matches do not), raw lowercase mnemonic input, empty-keyword `LIKE '%%'` behavior, GMS-owned level price/coupon values, and Facade coupon zeroing when the brand policy is disabled.
- No route, response field, database object, Flyway migration, or transaction boundary changed. The next smallest task is AD-2.1: rebuild the shared-Entity consumer matrix from current source and select one low-risk, single-owner physical-ownership slice before moving any Entity.

### Completed: AD-2.1 Shared-Entity Consumer Re-inventory and First-Slice Selection

- Published `MoneyPOS-AD-2.1-Shared-Entity-Consumer-Inventory.md` from the current source baseline. The scan has 75 Feature files importing shared entities; each of the 29 actually imported Entity types is classified by logical owner and as lawful owner-local persistence, a recorded cross-owner leak, or a legacy compatibility bridge. The count remains report-only because wildcard imports and owner-local ORM use are not cross-domain contracts.
- Reconfirmed the earlier ownership plan against the current call surface: GMS product/inventory and price types remain GMS-local; UMS member/benefit types remain UMS-local; order/payment/refund types remain TRADE-local; `OmsDailySummary` remains HOME-local; and SYS configuration/reference types retain their narrow-contract follow-ups. No Entity, Mapper, DTO, route, database object, Flyway migration, or transaction behavior changed in this inventory task.
- Selected `OmsRefundIdempotent` as the only AD-2.2 slice. Its complete production surface is TRADE's `RefundStateGuard` and already-relocated TRADE Mapper; no Controller, API DTO, JSON/Excel serialization, Mapper XML, other Maven module, or cross-Feature caller references it. The existing checkout/refund regression protects its transaction path. `GmsMemberTransaction` and `GmsTurnoverWarningSnapshot` were rejected because their smaller source surfaces lack equivalent behavior coverage or carry snapshot timing risk.
- The next smallest task is AD-2.2: relocate only `OmsRefundIdempotent` to TRADE persistence, add duplicate-refund regression coverage, and run the isolated full verification before considering a cross-domain Entity gate.

### Completed: AD-2.2 TRADE Refund-Idempotency Entity Physical Ownership Migration

- Moved `OmsRefundIdempotent` from the shared API entity package to `feature.trade.infrastructure.persistence.entity`. Only the already-TRADE `OmsRefundIdempotentMapper` and `RefundStateGuard` imports changed; the MyBatis table mapping, fields, existing `(req_id, biz_type)` primary-key semantics, mapper scan root, refund routes, API DTOs, and transaction annotations remain unchanged.
- Added a `CheckoutIntegrationTest` regression for a repeated full-refund request ID. The first refund completes normally and the second request is rejected with `POS_REFUND_REPEAT`, proving the relocated TRADE persistence record retains the existing duplicate-key boundary. The test fixture order number was kept below the existing sale-out document-number limit; no production length behavior changed.
- Source/resource verification found no old `com.money.entity.OmsRefundIdempotent` reference, no API-module copy, and no Mapper XML dependency. The isolated checkout/refund suite and isolated full Maven test suite passed; package build, additions-only architecture gate, and whitespace check passed. The shared-Entity scanner fell from 75 to 74 Feature files; the remaining imports stay report-only.
- The next smallest task is AD-2.3: design a trustworthy, additions-only cross-Feature Entity gate. It must distinguish owner-local ORM imports and documented compatibility bridges from new non-owner Entity contracts before changing the scan script.

### Completed: AD-2.3 Shared-Entity Cross-Domain Additions-Only Gate Design

- Published `MoneyPOS-AD-2.3-Shared-Entity-Additions-Only-Gate-Design.md`. It defines a data-driven ownership registry for the 28 remaining shared Entities, with HOME ownership of `OmsDailySummary` explicitly overriding its historical `Oms` name prefix.
- The proposed gate evaluates source Feature, Entity owner, relative source path, Entity simple name and import form. Owner-local explicit imports remain allowed; only an exact, documented non-owner bridge baseline is tolerated. Unregistered Entities and newly introduced wildcard imports fail in `--check-new`.
- Audited the three existing wildcard imports by actual type use. GMS Excel has one existing GMS → SYS `SysBrandConfig` bridge; the two TRADE wildcard users currently use only TRADE order Entities. The design therefore prevents a wildcard from silently adding a new cross-owner Entity type.
- No scanner, production source, route, DTO, table, Flyway, Mapper or transaction behavior changed in this design-only task. The existing report and additions-only scan gates, `mvn -q package -DskipTests`, and whitespace verification passed.
- The next smallest task is AD-2.3.1: implement the data files and script behavior exactly as designed, with script-level positive and negative fixtures.

### Completed: AD-2.3.1 Shared-Entity Cross-Domain Additions-Only Gate Implementation

- Added versioned shared-Entity ownership, exact cross-owner bridge and wildcard-path baselines under `scripts/architecture-baseline/`. The registry covers the 28 remaining API shared Entities; it records HOME as the owner of `OmsDailySummary` rather than inferring ownership from the `Oms` prefix.
- Extended `architecture-scan.sh --check-new` to allow owner-local explicit imports and only exact bridge tuples, while rejecting newly introduced non-owner imports, unregistered Entities, new wildcard imports, and a new cross-owner Entity type used through an existing wildcard. Default scan mode reports the classification without failing on the 74-file total.
- Added `scripts/test-architecture-scan.sh` temporary-source fixtures. They verify owner-local and exact-bridge acceptance plus rejection of a new cross-owner import, a new wildcard, a newly used cross-owner type in the GMS Excel wildcard, and an unregistered Entity.
- `bash -n scripts/architecture-scan.sh scripts/test-architecture-scan.sh`, the fixture suite, report scan, additions-only scan, `mvn -q package -DskipTests`, and `git diff --check` passed. No production source, route, DTO, table, Flyway, Mapper or transaction behavior changed.
- The next smallest task is AD-2.4: re-inventory candidates and select exactly one second shared-Entity physical-ownership migration slice before moving it.

### Completed: AD-2.4 Second Shared-Entity Physical-Ownership Slice Selection

- Published `MoneyPOS-AD-2.4-Second-Entity-Slice-Selection.md`. It re-audits the lowest-surface remaining candidates and selects GMS `GmsTurnoverWarningSnapshot` as the sole AD-2.5 migration target.
- The selected Entity has only two production consumers: `GmsTurnoverServiceImpl` and the already-GMS `GmsTurnoverWarningSnapshotMapper`. The controller exposes only DTO/map responses, MyBatis already scans the mapper package, and no Java test, resource, XML or public contract references the Entity directly.
- The selection preserves the `gms_turnover_warning_snapshot` Flyway table and daily unique key, the Jackson JSON mapping, tenant-interceptor exemption, query-triggered upsert timing, swallowed persistence failure, 30-day ordering and Top20 semantics. AD-2.5 must add the currently missing snapshot/trend regression before moving the class.
- `GmsMemberTransaction` and `PosMemberLevel` remain deferred because their sole legacy Mapper surfaces lack application behavior and regression evidence; `OmsDailySummary` remains deferred because its HOME read/write/compensation surface is wider despite strong tests. No production source, gate baseline, route, DTO, table, Flyway, Mapper or transaction changed in this selection task.
- The next smallest task is AD-2.5: move only `GmsTurnoverWarningSnapshot` into GMS persistence and add the required behavior regression.

### Completed: AD-2.5 GMS Turnover-Warning Snapshot Entity Physical-Ownership Migration

- Moved `GmsTurnoverWarningSnapshot` from the shared API entity package to `feature.gms.infrastructure.persistence.entity`. Only `GmsTurnoverServiceImpl` and the already-GMS `GmsTurnoverWarningSnapshotMapper` imports changed; `@TableName`, JSON handler, fields, `@InterceptorIgnore`, mapper scan root and the daily unique-key semantics remain unchanged.
- Added `GmsTurnoverSnapshotIntegrationTest` against `money_pos_test`. It proves a warning query creates and then updates one same-day snapshot, persists refreshed count/JSON values, returns date-ordered trend data and reads JSON frequency data. `GmsTurnoverServiceImplTest` proves a snapshot persistence exception is still swallowed without blocking the warning response.
- Source/resource verification found no old FQCN, API-module copy or Mapper XML dependency. The shared-Entity scanner fell from 74 to 73 Feature files and from 28 to 27 registered shared Entities; no cross-owner bridge baseline changed.
- The isolated full Maven suite passed with 24 current test classes and 67 tests; package build, script fixtures, report/additions-only architecture scans and whitespace check passed. No route, DTO, table, Flyway, Mapper package, transaction boundary, SYS strategy behavior, turnover algorithm, sorting or Top20 behavior changed.
- The next smallest task is AD-2.6: re-inventory candidates and select exactly one third shared-Entity physical-ownership migration slice before moving it.

### Completed: AD-2.6 Third Shared-Entity Physical-Ownership Slice Selection

- Published `MoneyPOS-AD-2.6-Third-Entity-Slice-Selection.md`. It selects UMS `PosMemberLevel` as the sole AD-2.7 migration target after re-auditing the smallest remaining persistence surfaces.
- `PosMemberLevel` has an explicit `pos_member_level` table mapping and auto-increment key, with only the scanned legacy `PosMemberLevelMapper` as production consumer. No Feature application service, controller, DTO, JSON/Excel boundary, XML FQCN, cross-Feature contract or other Maven module consumes it.
- AD-2.7 will preserve the legacy Mapper package and `com.money.mapper` scan root, changing only its generic import. Its purpose is Entity physical ownership; the Feature shared-Entity file count therefore may remain 73 while the ownership registry falls from 27 to 26. A direct Mapper CRUD integration regression will provide the currently missing behavior evidence.
- `GmsMemberTransaction` remains deferred because its Entity lacks `@TableName` and its legacy Mapper lacks `@Mapper`; `OmsDailySummary` remains deferred because its HOME read/write/compensation surface is wider. No production source, gate baseline, route, DTO, table, Flyway, Mapper or transaction changed in this selection task.
- The next smallest task is AD-2.7: move only `PosMemberLevel` into UMS persistence and add the required Mapper CRUD regression.

### Completed: AD-2.7 UMS Member-Level Entity Physical-Ownership Migration

- Moved `PosMemberLevel` from the shared API entity package to `feature.ums.infrastructure.persistence.entity`. Only the legacy `PosMemberLevelMapper` generic import changed; its `com.money.mapper` package and scan root remain intact. `@TableName("pos_member_level")`, `IdType.AUTO`, the string tenant id, table/Flyway and external API contracts are unchanged.
- Added `PosMemberLevelMapperIntegrationTest` against `money_pos_test`, proving generated-key insert, readback of level and tenant fields, update, and deletion through the existing Mapper.
- Source/resource verification found no API-module copy, old FQCN or resource dependency. The ownership registry fell from 27 to 26 while the Feature shared-Entity importer count correctly remains 73 because the retained mapper is outside `feature/**`.
- The targeted CRUD test passed; the isolated full Maven suite passed with 25 current test classes and 68 tests (0 failures / 0 errors). Package build, script fixtures, report/additions-only architecture scans and whitespace check passed.
- The next smallest task is AD-2.8: re-inventory candidates and select exactly one fourth shared-Entity physical-ownership migration slice before moving it.

### Completed: AD-2.8 Fourth Shared-Entity Physical-Ownership Slice Selection

- Published `MoneyPOS-AD-2.8-Fourth-Entity-Slice-Selection.md`. It selects GMS `GmsInventoryOrderDetail` as the sole AD-2.9 target after re-auditing the remaining lowest-surface persistence records.
- The selected Entity has exactly two consumers: the GMS inventory-order service creates detail rows in its existing inbound/check/outbound transactions, and the already-GMS `GmsInventoryOrderDetailMapper` persists them. No Controller, DTO, JSON/Excel boundary, XML FQCN, other Maven module or cross-Feature contract directly consumes the Entity.
- AD-2.9 will preserve the implicit MyBatis-Plus `gms_inventory_order_detail` table-name derivation, `ASSIGN_ID`, field types, GMS mapper scan root and all existing inventory business semantics. A direct Mapper CRUD integration test will establish the missing persistence evidence. Feature importer count is expected to fall from 73 to 72 and the ownership registry from 26 to 25.
- `GmsInventoryOrder` remains deferred because its `IService<Entity>` generic is a wider compatibility surface; `GmsMemberTransaction` remains deferred because its only legacy Mapper lacks an application consumer and requires separate scan/derivation characterization. No production source, gate baseline, route, DTO, table, Flyway, Mapper or transaction changed in this selection task.
- The next smallest task is AD-2.9: move only `GmsInventoryOrderDetail` into GMS persistence and add the required Mapper CRUD regression.

### Completed: AD-2.9 GMS Inventory-Order-Detail Entity Physical-Ownership Migration

- Moved `GmsInventoryOrderDetail` from the shared API entity package to `feature.gms.infrastructure.persistence.entity`. Only `GmsInventoryOrderServiceImpl` and the already-GMS `GmsInventoryOrderDetailMapper` imports changed; the implicit `gms_inventory_order_detail` mapping, `IdType.ASSIGN_ID`, field types and GMS mapper scan root remain intact.
- Added `GmsInventoryOrderDetailMapperIntegrationTest` against `money_pos_test`, proving generated-id insert, readback of order, goods, quantity, price, create-time and tenant fields, update, and deletion through the existing Mapper.
- Source/resource verification found no API-module copy, old FQCN or resource dependency. The ownership registry fell from 26 to 25 and the Feature shared-Entity importer count from 73 to 72; the six cross-owner compatibility bridges and three wildcard paths did not change.
- The targeted CRUD test passed; the isolated full Maven suite passed with 26 current test classes and 69 tests (0 failures / 0 errors). Package build, script fixtures, report/additions-only architecture scans and whitespace check passed.
- The next smallest task is AD-2.10: re-inventory candidates and select exactly one fifth shared-Entity physical-ownership migration slice before moving it.

### Completed: AD-2.10 Fifth Shared-Entity Physical-Ownership Slice Selection

- Published `MoneyPOS-AD-2.10-Fifth-Entity-Slice-Selection.md`. It selects GMS `GmsInventoryDocItem` as the sole AD-2.11 target after re-auditing the remaining lowest-surface persistence records.
- The selected Entity is created only by GMS's inventory-document and stock-command services, then persisted through the scanned legacy `GmsInventoryDocItemMapper`. No Controller, DTO, JSON/Excel boundary, XML FQCN, other Maven module or cross-Feature contract directly consumes it.
- AD-2.11 will preserve the explicit `gms_inventory_doc_item` table mapping, BaseEntity `ASSIGN_ID`/audit fields, snapshot fields, legacy mapper scan root and existing inventory semantics. It will extend the existing inbound inventory regression to observe the persisted item snapshot and add direct Mapper CRUD coverage. Feature importer count is expected to fall from 72 to 70 and the ownership registry from 25 to 24.
- `GmsInventoryDoc` remains deferred because FIN's query implementations depend on it; `GmsInventoryOrder` retains its `IService<Entity>` compatibility surface; `GmsMemberTransaction` still needs isolated scan/derivation characterization. No production source, gate baseline, route, DTO, table, Flyway, Mapper or transaction changed in this selection task.
- The next smallest task is AD-2.11: move only `GmsInventoryDocItem` into GMS persistence and add the required snapshot and Mapper CRUD regressions.
### Completed: AD-2.11 GMS Inventory-Document-Item Entity Physical-Ownership Migration

- Moved `GmsInventoryDocItem` from the shared API entity package to `feature.gms.infrastructure.persistence.entity`. Only the two GMS inventory services and legacy `GmsInventoryDocItemMapper` imports changed; explicit table mapping, BaseEntity `ASSIGN_ID`/audit behavior, fields and Mapper scan root remain intact.
- Added inbound document-item snapshot assertions and `GmsInventoryDocItemMapperIntegrationTest` CRUD coverage for the GMS-local Entity.
- No old FQCN or API copy remains. Registry fell 25→24 and owner-local Entity uses 119→117; shared-import file count remains 72 because both services import other shared Entities.
- Targeted and full isolated `money_pos_test` suites, package, scan fixtures, additions-only gate and whitespace check passed.
- Next: AD-2.12 sixth shared Entity slice selection.

### Completed: AD-2.12 Sixth Shared-Entity Physical-Ownership Slice Selection

- Published `MoneyPOS-AD-2.12-Sixth-Entity-Slice-Selection.md`. It re-audits the remaining smallest persistence surfaces and selects UMS `GmsMemberTransaction` as the sole AD-2.13 migration target.
- The legacy-named member-fund transaction Entity has only its legacy Mapper as a production consumer; no service, Controller, DTO, serialization boundary, XML FQCN, other Maven module or cross-Feature contract directly consumes it. The `gms_member_transaction` Flyway table and member-fund fields confirm UMS ownership despite the historical GMS prefix.
- AD-2.13 will retain implicit MyBatis-Plus table derivation, direct `ASSIGN_ID`, all fields, legacy Mapper scan root and all database/API behavior. It must add direct Mapper CRUD coverage because the type currently has no behavior regression. The ownership registry is expected to fall from 24 to 23 while the Feature shared-import file count remains 72.
- `GmsInventoryOrder` remains deferred because of its `IService<Entity>` surface; `GmsInventoryDoc` retains FIN query implementations; `GmsGoodsCombo` and `GmsStockLog` have wider product/inventory/transaction consumers; `Provinces` retains an `IService<Entity>` and Controller surface. No production source, gate baseline, route, DTO, table, Flyway, Mapper or transaction changed in this selection task.
- Next: AD-2.13 UMS member-transaction Entity physical-ownership migration.

### Completed: AD-2.13 UMS Member-Transaction Entity Physical-Ownership Migration

- Moved `GmsMemberTransaction` from the shared API entity package to `feature.ums.infrastructure.persistence.entity`. Only the legacy `GmsMemberTransactionMapper` generic import changed; its `com.money.mapper` package and scan root remain intact. The historical class and `gms_member_transaction` table names were deliberately retained.
- Added `GmsMemberTransactionMapperIntegrationTest`, covering insert with assigned ID, readback of the implicit table mapping and member-fund snapshot fields, update and delete under the existing tenant/authentication test context.
- No old FQCN or API copy remains. Registry fell 24→23; shared-import file count remains 72 because no Feature application class consumed this Entity.
- Targeted and full isolated `money_pos_test` suites, package, scan fixtures, additions-only gate and whitespace check passed.
- Next: AD-2.14 seventh shared-Entity slice selection.

### Completed: AD-2.14 Seventh Shared-Entity Physical-Ownership Slice Selection

- Published `MoneyPOS-AD-2.14-Seventh-Entity-Slice-Selection.md`. It re-audits the remaining smallest persistence surfaces and selects TRADE `OmsOrderPay` as the sole AD-2.15 migration target.
- The Entity is consumed only by TRADE checkout, order-detail and refund application code plus its legacy Mapper. FIN accesses payment aggregates through TRADE-owned Entity-free query contracts; no Controller, DTO, serialization boundary, XML FQCN or other Maven module directly exposes it.
- AD-2.15 will retain implicit `oms_order_pay` derivation, `IdType.AUTO`, every payment snapshot field, existing legacy Mapper scan root and both annotated aggregate SQL statements. It will add direct Mapper CRUD and payment-snapshot assertions while retaining checkout/refund and finance aggregate regressions. The ownership registry is expected to fall from 23 to 22 and shared-import files from 72 to 71.
- `UmsRechargeOrder` remains deferred because its Controller returns the Entity; `GmsInventoryOrder`, `Provinces`, `SysPrintConfig` and `OmsOrderLog` retain `IService<Entity>` or wider service/controller surfaces. No production source, gate baseline, route, DTO, table, Flyway, Mapper or transaction changed in this selection task.
- Next: AD-2.15 TRADE order-payment Entity physical-ownership migration.

### Completed: AD-2.15 TRADE Order-Payment Entity Physical-Ownership Migration

- Moved `OmsOrderPay` from the shared API entity package to `feature.trade.infrastructure.persistence.entity`. Updated only the legacy `OmsOrderPayMapper`, TRADE checkout/order-detail/refund consumers and affected test imports; mapper package, scan root and both annotated payment aggregate SQL statements remain unchanged.
- Added `OmsOrderPayMapperIntegrationTest`, covering assigned auto primary key, implicit table mapping, all payment snapshot fields, update and delete. Extended the cash-checkout regression to characterize the persisted order number, method code/name and legacy/net/original/change amount snapshots.
- No old FQCN or API copy remains. Registry fell 23→22; shared-import file count is verified by the architecture scan after the move.
- Targeted Mapper/checkout/finance suites and full isolated `money_pos_test` suite passed, followed by package, scan fixtures, additions-only gate and whitespace check.
- Next: AD-2.16 eighth shared-Entity slice selection.

### Completed: AD-2.16 Eighth Shared-Entity Physical-Ownership Slice Selection

- Published `MoneyPOS-AD-2.16-Eighth-Entity-Slice-Selection.md`. It re-audits the remaining candidates and selects HOME `OmsDailySummary` as the sole AD-2.17 migration target.
- Although it retains the OMS prefix, all production consumers are HOME's daily-summary query, writer and dashboard services. HOME already owns its read-model writes; TRADE/GMS/UMS provide Entity-free snapshot inputs, and no Controller, DTO, XML FQCN, other module or cross-Feature contract exposes this Entity.
- AD-2.17 will retain `BaseEntity`, explicit `@TableName("oms_daily_summary")`, `IdType.AUTO`, all fields, the legacy Mapper scan root, both annotated atomic SQL statements, the unique `uk_record_date`, and the deliberate preservation of `member_recharge`. It must add direct Mapper persistence coverage while retaining the HOME snapshot characterization suite.
- `UmsRechargeOrder` remains deferred for Controller Entity exposure; `GmsInventoryOrder`, `Provinces`, `SysPrintConfig` and `OmsOrderLog` retain `IService<Entity>` or wider service/controller surfaces; the remaining GMS/SYS candidates have wider algorithmic or compatibility surfaces. No production source, gate baseline, route, DTO, table, Flyway, Mapper or transaction changed in this selection task.
- Next: AD-2.17 HOME daily-summary Entity physical-ownership migration.

### Completed: AD-2.17 HOME Daily-Summary Entity Physical-Ownership Migration

- Moved `OmsDailySummary` from the shared API entity package to `feature.home.infrastructure.persistence.entity`. Updated only the legacy `OmsDailySummaryMapper`, HOME daily-summary query/writer/dashboard consumers and HOME snapshot test imports; the Mapper package, scan root and both annotated atomic SQL statements remain unchanged.
- Added `OmsDailySummaryMapperIntegrationTest`, covering the HOME-local Entity's explicit table mapping, `AUTO` primary key, all snapshot-field CRUD, duplicate-date `insertIfAbsent`, same-record `upsertSnapshot`, and preservation of `member_recharge`. `HomeCountSnapshotCharacterizationTest` continues to cover refresh, read-only GET, seven-day compensation and dashboard snapshot behavior.
- No old FQCN or API copy remains. Registry fell 22→21; the architecture scan reports shared-import files 71→68 and owner-local uses 114→111, with cross-owner bridges and wildcard baseline unchanged.
- Targeted Mapper/HOME snapshot suites and the full isolated `money_pos_test` suite passed, followed by package, scan fixtures, additions-only gate and whitespace check.
- Next: AD-2.18 ninth shared-Entity slice selection.

### Completed: AD-2.18 Ninth Shared-Entity Physical-Ownership Slice Selection

- Published `MoneyPOS-AD-2.18-Ninth-Entity-Slice-Selection.md`. It re-audits the remaining candidates and selects GMS `GmsGoodsCombo` as the sole AD-2.19 migration target.
- The Entity is consumed only by its legacy Mapper and four GMS product/inventory services. TRADE reaches GMS through stock command contracts rather than importing the Entity; no Controller, DTO, XML FQCN, public `IService<Entity>`, other Maven module or cross-Feature production contract exposes it.
- AD-2.19 will retain implicit MyBatis-Plus table derivation, `IdType.ASSIGN_ID`, every field, the legacy Mapper scan root and the historical coexistence of that Java key strategy with the table's auto-increment definition. It will add direct Mapper CRUD while retaining GMS combo/stock and TRADE combo checkout/refund regressions. The ownership registry is expected to fall from 21 to 20 and shared-import files from 68 to 64.
- `GmsInventoryOrder`, `Provinces`, `SysPrintConfig`, `UmsRechargeOrder`, `UmsMemberLog` and `OmsOrderLog` remain deferred for public generic or HTTP Entity surfaces; the remaining price, stock, document and strategy candidates have wider algorithmic, compatibility or cross-Feature query surfaces. No production source, gate baseline, route, DTO, table, Flyway, Mapper or transaction changed in this selection task.
- Next: AD-2.19 GMS goods-combo Entity physical-ownership migration.

### Completed: AD-2.19 GMS Goods-Combo Entity Physical-Ownership Migration

- Moved `GmsGoodsCombo` from the shared API entity package to `feature.gms.infrastructure.persistence.entity`. Updated only the legacy `GmsGoodsComboMapper`, GMS combo/product/stock-command consumers and affected GMS/TRADE test imports; Mapper package and scan root remain unchanged.
- Added `GmsGoodsComboMapperIntegrationTest`, covering the GMS-local Entity's assigned ID, implicit table mapping, BOM fields, explicit creation timestamp, update and delete. Existing GMS combo/stock and TRADE combo checkout/full-refund integration regressions remain intact.
- No old FQCN or API copy remains. Registry fell 21→20; owner-local uses fell 111→107. Shared-import **files** fell 68→67 rather than the four-import forecast because three changed GMS files still import other shared Entities; cross-owner bridges and wildcard baseline are unchanged.
- Targeted Mapper/GMS combo/TRADE checkout suites and the full isolated `money_pos_test` suite passed, followed by package, scan fixtures, additions-only gate and whitespace check.
- Next: AD-2.20 tenth shared-Entity slice selection.

### Completed: AD-2.20 Tenth Shared-Entity Physical-Ownership Slice Selection

- Published `MoneyPOS-AD-2.20-Tenth-Entity-Slice-Selection.md`. It re-audits all 20 remaining registered shared Entities and selects TRADE `OmsOrderLog` as the sole AD-2.21 migration target.
- `OmsOrderLog` has one TRADE-local Mapper and five TRADE application/domain consumers. Its `IService<OmsOrderLog>` generic remains inside TRADE, while the order-details route converts it to `OrderLogVO`; no other Feature, HTTP contract, resource FQCN or Maven module consumes the Entity.
- AD-2.21 will retain `@TableName("oms_order_log")`, `BaseEntity` / `ASSIGN_ID`, audit fills, the Mapper scan root, the `(order_id, create_time DESC)` index dependence and the service-level ban on audit-log updates/deletes. It will add Mapper insert/read-back coverage and extend existing checkout/refund/order-detail integration checks. Registry is expected to fall 20→19; shared-import files may fall only 67→64 because three relevant files retain other shared Entity imports.
- `GmsInventoryOrder`, `Provinces`, `SysPrintConfig`, `UmsRechargeOrder`, `UmsMemberLog` and the remaining price, stock, document and strategy candidates remain deferred for wider workflow, compatibility, HTTP or cross-Feature query surfaces. No production source, gate baseline, route, DTO, table, Flyway, Mapper or transaction changed in this selection task.
- Next: AD-2.21 TRADE order-audit-log Entity physical-ownership migration.

### Completed: AD-2.21 TRADE Order-Audit-Log Entity Physical-Ownership Migration

- Moved `OmsOrderLog` from the shared API entity package to `feature.trade.infrastructure.persistence.entity`. Updated only its TRADE Mapper, local service interface/implementation, checkout, refund and order-detail consumers; Mapper package and scan root remain unchanged.
- Added `OmsOrderLogMapperIntegrationTest`, covering the TRADE-local Entity's explicit `oms_order_log` mapping, inherited assigned ID, order ID, description, tenant ID and audit-field insert/read-back. It intentionally does not perform direct Mapper update/delete operations, preserving the existing service-level audit-log immutability rule.
- Extended `CheckoutIntegrationTest`: settlement creates its JSON audit record; full and partial refunds append their respective descriptions; order details return the same ascending timestamp order as the Mapper result through `OrderLogVO`.
- No old FQCN or API copy remains. Registry fell 20→19; owner-local uses fell 107→101 and shared-import files fell 67→64. Cross-owner bridges (6) and wildcard baseline (3) are unchanged.
- Targeted Mapper/TRADE checkout suites and the full isolated `money_pos_test` suite passed, followed by package, scan fixtures, additions-only gate and whitespace check.
- Next: AD-2.22 eleventh shared-Entity slice selection.

### Completed: AD-2.22 Eleventh Shared-Entity Physical-Ownership Slice Selection

- Published `MoneyPOS-AD-2.22-Eleventh-Entity-Slice-Selection.md`. It re-audits all 19 remaining registered shared Entities and selects GMS `GmsInventoryOrder` as the sole AD-2.23 migration target.
- The Entity has one GMS-local Mapper and two GMS-local service declarations. Its `IService<GmsInventoryOrder>` generic remains entirely within GMS; production search found no Controller, other Feature, DTO/VO, serialization, XML/resource FQCN or Maven-module consumer.
- AD-2.23 will retain implicit MyBatis-Plus `gms_inventory_order` derivation, `IdType.ASSIGN_ID`, all fields, the GMS Mapper scan root and inbound/check/outbound transactions. It will add Mapper CRUD plus three inventory-order command regressions covering the completed main record, details, stock logs and each path's inventory/cost results. Registry is expected to fall 19→18 and shared-import files 64→62.
- SYS/UMS low-use candidates remain deferred for Controller, compatibility or runtime surfaces; GMS document/log, goods/price and remaining TRADE/UMS/SYS candidates retain wider algorithms, API compatibility or cross-Feature query surfaces. No production source, gate baseline, route, DTO, table, Flyway, Mapper or transaction changed in this selection task.
- Next: AD-2.23 GMS inventory-order Entity physical-ownership migration.

### Completed: AD-2.23 GMS Inventory-Order Entity Physical-Ownership Migration

- Moved `GmsInventoryOrder` from the shared API entity package to `feature.gms.infrastructure.persistence.entity`. Updated only its GMS Mapper, local service interface and implementation; Mapper package and scan root remain unchanged.
- Added `GmsInventoryOrderMapperIntegrationTest`, covering the GMS-local Entity's assigned ID, implicit table mapping, all main-record fields, update and deletion. Added `GmsInventoryOrderServiceIntegrationTest`, covering inbound weighted-cost update, check difference and outbound scrap deduction together with their completed main record, detail and stock-log writes.
- No old FQCN or API copy remains. Registry fell 19→18; owner-local uses fell 101→98 and shared-import files fell 64→62. Cross-owner bridges (6) and wildcard baseline (3) are unchanged.
- Targeted Mapper/GMS inventory-order suites and the full isolated `money_pos_test` suite passed, followed by package, scan fixtures, additions-only gate and whitespace check.
- Next: AD-2.24 twelfth shared-Entity slice selection.

### Completed: AD-2.24 Twelfth Shared-Entity Physical-Ownership Slice Selection

- Published `MoneyPOS-AD-2.24-Twelfth-Entity-Slice-Selection.md`. It re-audits all 18 remaining registered shared Entities and selects SYS `Provinces` as the sole AD-2.25 migration target.
- Direct production Entity use is limited to the legacy `ProvincesMapper`, `ProvincesService` and `ProvincesServiceImpl`; the public Controller exposes only `SelectVO` through the province, city and district routes. The retained `IService<Provinces>` generic has no other Feature, Maven module, DTO/VO, resource or HTTP Entity consumer.
- AD-2.25 will move only the Entity to `feature.sys.infrastructure.persistence.entity`, retaining its implicit `provinces` mapping, `BaseEntity`, legacy Mapper scan root, tenant-ignore configuration, Flyway seed data and one-time JVM cache semantics. The historical mismatch between inherited ID/audit fields and the eight-column read-only seed table is explicitly not a migration repair: first characterize its current read path, then do not add write CRUD or alter schema/Flyway.
- `SysBrandConfig`, `SysPrintConfig`, `SysStrategy`, UMS asset/log candidates and remaining GMS/TRADE candidates remain deferred for broader configuration, device, HTTP, algorithmic or transaction surfaces. No production source, gate baseline, route, DTO, table, Flyway, Mapper or transaction changed in this selection task.
- Next: AD-2.25 SYS Provinces Entity physical-ownership migration with read-only Mapper/cache/HTTP regression.

### Completed: AD-2.24.1 Provinces Read-Only Mapping Repair Design

- The first AD-2.25 isolated-database characterization found that the inherited `BaseEntity` made MyBatis-Plus select `id` and audit columns from the eight-column Flyway `provinces` seed table. `money_pos_test` correctly failed with `Unknown column 'id' in 'SELECT'`; the cache and Controller therefore had no usable migration baseline.
- Published `MoneyPOS-AD-2.24.1-Provinces-Read-Only-Mapping-Repair-Design.md`. The authorized remedy is a separate AD-2.24.2: keep the Entity in the API package temporarily, model only its eight real geographic fields with explicit `provinces` mapping, and replace generic Mapper/service write inheritance with a narrow explicit read query and service.
- No source, table, Flyway, tenant-ignore, route, DTO, scanner baseline or registry changed in this design task. The repair must not add synthetic ID/audit columns, write SQL or dictionary data mutations. After its validation and push, AD-2.25 resumes solely as the SYS physical-ownership move.
- Next: AD-2.24.2 Provinces read-only mapping repair implementation with Mapper/cache/HTTP regression.

### Completed: AD-2.24.2 Provinces Read-Only Mapping Repair Implementation

- Replaced the false `BaseEntity` / `BaseMapper` model with a real static-dictionary model while leaving `Provinces` temporarily in `money-app-api`: explicit `@TableName("provinces")`, exactly eight geographic fields, and no inherited ID/audit fields.
- `ProvincesMapper` now exposes only an explicit eight-column `selectAll()` query. `ProvincesService` and its implementation no longer inherit `IService` / `ServiceImpl`; the existing first-read double-checked JVM cache uses that read query and retains all three `SelectVO` methods.
- Added read-only integration regressions: the Mapper asserts the Flyway Beijing seed record's eight fields without mutations; the Controller/service test verifies province/city/district `SelectVO` values and repeated reads of the initialized cache. It binds the existing web-log request context, without modifying production Controller behavior.
- Targeted Mapper/controller, full isolated `money_pos_test`, package, scan fixtures, additions-only gate and whitespace validation passed. Table/Flyway, tenant-ignore, seed data, routes, scanner baseline and ownership registry did not change.
- Next: AD-2.25 SYS Provinces Entity physical-ownership migration.

### Completed: AD-2.25 SYS Provinces Entity Physical-Ownership Migration

- Moved the already-validated read-only `Provinces` Entity from `money-app-api: com.money.entity` to `feature.sys.infrastructure.persistence.entity`. Updated only the legacy Mapper, the SYS service implementation and the Mapper regression import; the Controller, service interface, Mapper package and scan root remain unchanged.
- Retained the explicit `provinces` mapping, exactly eight-column read query, no `BaseEntity` / generic write inheritance, Flyway seed data, tenant-ignore configuration, first-read JVM cache and all three `SelectVO` routes. No schema, Flyway, SQL, route, DTO or cache behavior changed.
- No API copy or old FQCN remains. The ownership registry fell 18→17; shared Feature import files and owner-local-use report remain 62 and 98 because the affected legacy consumers were outside the scan's Feature-file metric. Cross-owner bridges (6) and wildcard baseline (3) are unchanged.
- Targeted Mapper/controller, full isolated `money_pos_test`, package, scan fixtures, additions-only gate and whitespace validation passed.
- Next: AD-2.26 thirteenth shared-Entity slice selection.

### Completed: AD-2.26 Thirteenth Shared-Entity Physical-Ownership Slice Selection

- Published `MoneyPOS-AD-2.26-Thirteenth-Entity-Slice-Selection.md`. It re-audits all 17 remaining registered shared Entities and selects UMS `UmsRechargeOrder` as the sole AD-2.27 migration target.
- Direct production use is restricted to the legacy Mapper, `UmsMemberRechargeService`, `UmsMemberAssetService` and `UmsMemberAssetController`, all within UMS. Its single direct REST return is UMS-local; no other Feature, module, DTO/VO, XML/resource FQCN or compatibility bridge consumes the type.
- AD-2.27 will retain explicit `ums_recharge_order`, `Serializable`, AUTO ID, fields, the legacy Mapper scan root, `uk_order_no`, `idx_member_id`, recharge/red-void asset/log transactions and the existing query route/JSON/authorization behavior. It must add Mapper insert/readback/VOID-update, balance recharge/red-void, and query-route regression; it must not introduce a direct deletion flow.
- `SysPrintConfig` remains deferred for printer/cash-drawer runtime behavior; the remaining UMS member/coupon, GMS inventory/product, TRADE order and SYS configuration/strategy candidates have wider workflow, compatibility, algorithmic or cross-Feature surfaces. No production source, scanner baseline, route, DTO, table, Flyway, Mapper or transaction changed in this selection task.
- Next: AD-2.27 UMS recharge-certificate Entity physical-ownership migration.

### Completed: AD-2.27 UMS Recharge-Certificate Entity Physical-Ownership Migration

- Moved `UmsRechargeOrder` from `money-app-api: com.money.entity` to `feature.ums.infrastructure.persistence.entity`. Updated only the legacy Mapper, `UmsMemberRechargeService`, `UmsMemberAssetService`, `UmsMemberAssetController` and the new UMS-local test imports; Mapper package and scan root remain unchanged.
- Added `UmsRechargeOrderMapperIntegrationTest`, covering explicit table mapping, generated AUTO ID, complete certificate snapshot insert/readback and the existing `VOID` update without adding a direct deletion flow. Added balance-recharge/red-void coverage for the persisted certificate, member balance/coupon restoration and recharge/reversal logs; the query-handler regression covers returned fields plus blank/missing-order failures.
- Retained `@TableName("ums_recharge_order")`, `Serializable`, every field, `uk_order_no`, `idx_member_id`, transaction annotations, random order-number generation, asset/log/red-void behavior, route, JSON fields, authorization, Flyway and schema. No API copy or old FQCN remains.
- Registry fell 17→16. The architecture scan reports shared Feature import files 62→62 and owner-local uses 98→95 because the legacy Mapper lies outside `feature/**` and all three changed Feature consumers still use other shared Entity types; bridges (6) and wildcard baseline (3) remain unchanged.
- Targeted Mapper/service/controller, full isolated `money_pos_test`, package, scan fixtures, additions-only gate and whitespace validation passed. The first sandbox test attempt was blocked before DB connection; its identical approved isolated-db rerun passed.
- Next: AD-2.28 fourteenth shared-Entity slice selection.

### Completed: AD-2.28 Fourteenth Shared-Entity Physical-Ownership Slice Selection

- Published `MoneyPOS-AD-2.28-Fourteenth-Entity-Slice-Selection.md`. It re-audits all 16 remaining registered shared Entities and selects SYS `SysPrintConfig` as the sole AD-2.29 migration target.
- Direct production use is limited to the legacy Mapper, service interface/implementation, configuration Controller and `PosPrinterService`, all within SYS's legacy application boundary. Controller JSON exposure and the printer's fixed-ID read are local runtime behavior; no other Feature, module, DTO/VO, XML/resource FQCN or compatibility bridge consumes the type.
- AD-2.29 will retain explicit `sys_print_config`, `BaseEntity`, INPUT ID, fixed ID=1 semantics, Flyway seed/configuration row, audit fields, Mapper scan root, controller route/JSON/failure behavior and the printer's ESC/POS/hardware handling. It must add ID=1 Mapper read/update and configuration service/controller regression, while retaining real printer/cash-drawer verification as a manual environment check.
- `SysStrategy` and `SysBrandConfig` remain deferred for strategy/brand compatibility surfaces; UMS member/coupon, GMS inventory/product and TRADE order candidates retain wider workflow, cross-owner, algorithmic or public-contract surfaces. No production source, scanner baseline, route, DTO, table, Flyway, Mapper or transaction changed in this selection task.
- Next: AD-2.29 SYS print-configuration Entity physical-ownership migration.

### Completed: AD-2.29 SYS Print-Configuration Entity Physical-Ownership Migration

- Moved `SysPrintConfig` from `money-app-api: com.money.entity` to `feature.sys.infrastructure.persistence.entity`. Updated only the legacy Mapper, service interface/implementation, configuration Controller, `PosPrinterService` and new SYS-local test imports; Mapper package and scan root remain unchanged.
- Added `SysPrintConfigMapperIntegrationTest`, which reads the Flyway ID=1 seed through the SYS-local Entity, asserts every configuration and inherited audit field, then updates and reads back that same fixed record. Added Controller regression proving a non-1 request ID is forced to 1 and that the route returns all printer configuration fields; neither test invokes hardware.
- Retained explicit `sys_print_config`, `BaseEntity`, INPUT ID, fixed ID=1 semantics, seed row, audit fills, route/JSON/failure behavior, `PosPrinterService`'s `selectById(1L)` timing, ESC/POS generation and error handling. No API copy or old FQCN remains; actual printer/cash-drawer verification remains a manual environment acceptance item.
- Registry fell 16→15. Shared Feature import files and owner-local-use report remain 62 and 95 because all affected consumers are outside `feature/**`; bridges (6) and wildcard baseline (3) are unchanged.
- Targeted Mapper/controller, full isolated `money_pos_test`, package, scan fixtures, additions-only gate, static fixed-ID read check and whitespace validation passed.
- Next: AD-2.30 fifteenth shared-Entity slice selection.

### Completed: AD-2.30 Fifteenth Shared-Entity Physical-Ownership Slice Selection

- Published `MoneyPOS-AD-2.30-Fifteenth-Entity-Slice-Selection.md`. It re-audits all 15 remaining registered shared Entities and selects GMS `GmsInventoryDoc` as the sole AD-2.31 migration target.
- Production use is one legacy Mapper plus four GMS inventory/financial-snapshot consumers. The two services carrying `Finance` in their names are GMS-owned adapters that return existing Entity-free finance snapshots; FIN neither imports the Entity nor the Mapper. The inventory Controller only accepts a DTO, and TRADE invokes inventory commands rather than the Entity.
- AD-2.31 will retain explicit `gms_inventory_doc`, `BaseEntity`, the historical assigned-ID/auto-increment coexistence, all fields, `uk_doc_no`, Mapper scan root, inventory document/cost/detail/log transactions, TRADE stock-command behavior and FIN aggregation semantics. It will add direct Mapper persistence coverage and retain GMS inventory, checkout/refund and finance snapshot regressions.
- `SysBrandConfig` and `SysStrategy` remain deferred because GMS still directly consumes their SYS configuration/strategy Entities; remaining GMS, UMS and TRADE candidates retain wider algorithms, asset workflows, public compatibility or transaction surfaces. No production source, scanner baseline, route, DTO, table, Flyway, Mapper or transaction changed in this selection task.
- Next: AD-2.31 GMS inventory-document Entity physical-ownership migration.

### Completed: AD-2.31 GMS Inventory-Document Entity Physical-Ownership Migration

- Moved `GmsInventoryDoc` from `money-app-api: com.money.entity` to `feature.gms.infrastructure.persistence.entity`. Updated only the legacy `GmsInventoryDocMapper`, the four GMS-owned inventory/financial-snapshot consumers and affected test imports; Mapper package and scan root remain unchanged.
- Added `GmsInventoryDocMapperIntegrationTest`, covering the GMS-local Entity's assigned ID, explicit `gms_inventory_doc` mapping, all persisted document fields, inherited audit fields and summary-field update. Existing GMS inbound-document, TRADE checkout/refund and FIN daily-document/waterfall-snapshot integration regressions now use the local type.
- Retained `BaseEntity`, the historical assigned-ID/AUTO_INCREMENT coexistence, `uk_doc_no`, every field, inventory/cost/detail/log transaction behavior, TRADE stock-command behavior, FIN Entity-free snapshot contracts, routes, DTOs, table and Flyway. No API copy or old FQCN remains.
- Registry fell 15→14; shared Feature import files fell 62→60 and owner-local uses fell 95→91. Cross-owner bridges (6) and wildcard baseline (3) are unchanged. Targeted and full isolated `money_pos_test` suites, package, scan fixtures, additions-only gate and whitespace validation passed.
- Next: AD-2.32 sixteenth shared-Entity slice selection.

### Completed: AD-2.32 Sixteenth Shared-Entity Physical-Ownership Slice Selection

- Published `MoneyPOS-AD-2.32-Sixteenth-Entity-Slice-Selection.md`. It re-audits all 14 remaining registered shared Entities and selects GMS `PosSkuLevelPrice` as the sole AD-2.33 migration target.
- Production use is the legacy Mapper plus seven GMS-local price-matrix, product, Excel and Entity-free goods-snapshot consumers. The retained `GmsGoodsExcelManager` wildcard is a pre-existing GMS/SYS baseline; it will receive a GMS-local explicit price Entity import without changing that wildcard or its SYS bridge. No Controller, API DTO, resource FQCN, other Maven module or cross-Feature production code consumes the Entity.
- AD-2.33 will retain explicit `pos_sku_level_price`, AUTO ID, all fields, null/default behavior, the legacy Mapper scan root, price-matrix merge/delete semantics, Excel output, checkout and POS snapshot contracts. It will add Mapper CRUD coverage and retain existing product, Excel and legacy POS regressions. Registry is expected to fall 14→13, shared-import files 60→59 and owner-local uses 91→84; bridges (6) and wildcard baseline (3) must not expand.
- GMS stock/log/catalog candidates remain deferred for HTTP or algorithmic surfaces; SYS candidates retain GMS bridges; UMS candidates retain member-asset workflows; TRADE orders retain core transaction and compatibility surfaces. No production source, scanner baseline, route, DTO, table, Flyway, Mapper or transaction changed in this selection task.
- Next: AD-2.33 GMS SKU-level-price Entity physical-ownership migration.

### Completed: AD-2.33 GMS SKU-Level-Price Entity Physical-Ownership Migration

- Moved `PosSkuLevelPrice` from `money-app-api: com.money.entity` to `feature.gms.infrastructure.persistence.entity`. Updated only the legacy Mapper, six explicit GMS product consumers, the existing GMS Excel-manager wildcard consumer and affected test imports; Mapper package and scan root remain unchanged.
- Added `PosSkuLevelPriceMapperIntegrationTest`, covering the GMS-local Entity's AUTO ID, explicit `pos_sku_level_price` mapping, every persisted price-matrix field, update and deletion. Existing GMS product, Excel, checkout and legacy POS snapshot regressions now use the local type.
- Retained all fields and null/default behavior, price-matrix merge/delete behavior, Excel import/export, checkout/POS Entity-free snapshots, the Excel manager's wildcard import and existing SYS bridge, routes, DTOs, table and Flyway. No API copy or old FQCN remains.
- Registry fell 14→13; shared Feature import files fell 60→59 and owner-local uses fell 91→84. Cross-owner bridges (6) and wildcard baseline (3) are unchanged. Targeted and full isolated `money_pos_test` suites, package, scan fixtures, additions-only gate and whitespace validation passed.
- Next: AD-2.34 seventeenth shared-Entity slice selection.

### Completed: AD-2.34 Seventeenth Shared-Entity Physical-Ownership Slice Selection

- Published `MoneyPOS-AD-2.34-Seventeenth-Entity-Slice-Selection.md`. It re-audits all 13 remaining registered shared Entities and selects UMS `UmsMemberBrandLevel` as the sole AD-2.35 migration target.
- Production Entity use is one legacy Mapper plus five UMS-local member-matrix, import, Excel and Entity-free profile/checkout-snapshot consumers. The Mapper's HOME distribution query returns aggregation Maps and TRADE has no production Entity/Mapper import; its test fixture is not a production contract.
- AD-2.35 will retain explicit `ums_member_brand_level`, AUTO ID, every field, `uk_member_brand`, audit/tenant columns, the Mapper's orphan/deleted-member filtering SQL, matrix delete-and-save/import-merge behavior, Excel and HOME/checkout/POS snapshot contracts. It will add Mapper CRUD coverage and retain existing UMS/TRADE integration regressions. Registry is expected to fall 13→12, shared-import files 59→55 and owner-local uses 84→79; bridges (6) and wildcard baseline (3) must not expand.
- GMS stock/catalog, SYS bridge, UMS asset/coupon/member and TRADE order candidates remain deferred for wider HTTP, transaction, compatibility or cross-owner surfaces. No production source, scanner baseline, route, DTO, table, Flyway, Mapper or transaction changed in this selection task.
- Next: AD-2.35 UMS member-brand-level Entity physical-ownership migration.

### Completed: AD-2.35 UMS Member-Brand-Level Entity Physical-Ownership Migration

- Moved `UmsMemberBrandLevel` from `money-app-api: com.money.entity` to `feature.ums.infrastructure.persistence.entity`. Updated only the legacy Mapper, five UMS-owned member-matrix/import/Excel/Entity-free snapshot consumers and affected test imports; Mapper package and scan root remain unchanged.
- Added `UmsMemberBrandLevelMapperIntegrationTest`, covering the UMS-local Entity's explicit `ums_member_brand_level` mapping, AUTO ID, complete member-brand-level/tenant matrix, database audit timestamps, update and deletion. Existing UMS member import, HOME aggregation, Excel export, checkout-benefit and TRADE POS-profile regressions now use the local type.
- Retained every Entity annotation and field, `uk_member_brand`, audit/tenant columns, the Mapper's orphan/deleted-member filtering aggregation SQL, delete-and-save/import-merge semantics, Excel output, HOME/checkout/POS Entity-free snapshots, routes, DTOs, table and Flyway. No API copy or old FQCN remains.
- Registry fell 13→12 and owner-local uses fell 84→79. Shared Feature import files remain 59 because every migrated consumer still imports at least one other shared Entity; this report metric therefore did not fall. Cross-owner bridges (6) and wildcard baseline (3) are unchanged. Targeted and full isolated `money_pos_test` suites, package, scan fixtures, additions-only gate and whitespace validation passed.
- Next: AD-2.36 eighteenth shared-Entity slice selection.

### Completed: AD-2.36 Eighteenth Shared-Entity Physical-Ownership Slice Selection

- Published `MoneyPOS-AD-2.36-Eighteenth-Entity-Slice-Selection.md`. It re-audits all 12 remaining registered shared Entities and selects GMS `GmsGoodsCategory` as the sole AD-2.37 migration target.
- Direct production use is the GMS-local Mapper, category service/interface, category-name query and GMS checkout/Excel consumers. The category Controller uses DTO/`SelectVO`/`TreeNodeVO`; FIN receives names through `GoodsCategoryNameQuery` and TRADE receives names through `CheckoutGoodsSnapshot`, so neither has an Entity/Mapper dependency.
- AD-2.37 will retain explicit `gms_goods_category`, `BaseEntity` and its existing ID behavior, every field/audit/tenant rule, icon upload transaction hooks, duplicate/child/goods delete guards, tree/recursive traversal, atomic goods count, Excel behavior and Entity-free checkout/FIN contracts. It will add Mapper CRUD coverage and retain category-tree/guard, Excel, checkout and FIN name regressions.
- Brand/goods/stock candidates remain deferred for wider controller, catalog, inventory-command or legacy surfaces; SYS candidates retain GMS bridges; UMS member/coupon candidates retain assets, transactions or TRADE bridges; TRADE orders retain core transaction/print/report surfaces. No production source, scanner baseline, route, DTO, table, Flyway, Mapper or transaction changed in this selection task.
- Next: AD-2.37 GMS goods-category Entity physical-ownership migration.

### Completed: AD-2.37 GMS Goods-Category Entity Physical-Ownership Migration

- Moved `GmsGoodsCategory` from `money-app-api: com.money.entity` to `feature.gms.infrastructure.persistence.entity`; updated the existing GMS Mapper, category services/name query, checkout/Excel consumers and test fixtures.
- Added `GmsGoodsCategoryMapperIntegrationTest`, covering the GMS-local Entity's inherited assigned ID/audit fields, explicit table mapping, category fields, update and deletion. Category, Excel, checkout and FIN regressions retain their existing Entity-free contracts.
- Retained `BaseEntity`, table/Flyway, icon transaction hooks, tree/delete guards, goods count, routes, DTOs and snapshots. No API copy or old FQCN remains; registry fell 12→11.
- Next: AD-2.38 nineteenth shared-Entity slice selection.

### Completed: AD-2.38 Nineteenth Shared-Entity Physical-Ownership Slice Selection

- Selected GMS `GmsBrand` as the sole AD-2.39 migration target. Its Mapper, brand service and name/selection queries are GMS-local; external consumers use DTO/VO or existing Entity-free snapshots.
- AD-2.39 retains `BaseEntity`, `gms_brand`, assigned ID, Flyway seeds, logo transaction hooks, pagination/selectors, goods count, Excel and external snapshots; it adds Mapper CRUD coverage.
- Next: AD-2.39 GMS brand Entity physical-ownership migration.

### Completed: AD-2.39 GMS Brand Entity Physical-Ownership Migration

- Moved `GmsBrand` from `money-app-api: com.money.entity` to `feature.gms.infrastructure.persistence.entity`; updated the legacy Mapper, GMS brand/name/selection/Excel consumers and affected local test fixtures.
- Added `GmsBrandMapperIntegrationTest` for the local `BaseEntity` mapping, generated assigned ID, all brand fields, audit readback, update and deletion. Isolated full `money_pos_test` regression, package, scan fixtures, additions-only gate and whitespace check passed.
- Registry fell 11→10; scan reports shared Feature imports 55→52 and owner-local uses 72→67. Bridges (6) and wildcard baseline (3) remain unchanged. Next: AD-2.40 twentieth shared-Entity slice selection.

### Completed: AD-2.40 Twentieth Shared-Entity Physical-Ownership Slice Selection

- Selected GMS `GmsStockLog` as the sole AD-2.41 target; its inventory write/query/analysis, legacy Mapper/service and Controller are GMS-local.
- AD-2.41 retains AUTO ID, stock/cost snapshots, transaction and pagination/JSON behavior, table/Flyway and existing regressions; it adds Mapper CRUD coverage.
- Next: AD-2.41 GMS stock-log Entity physical-ownership migration.

### Completed: AD-2.43 UMS Member-Log Entity Physical-Ownership Migration

- Added `UmsMemberLogMapperIntegrationTest`, covering the UMS-local asset ledger's AUTO ID, balance snapshots, order/real-amount/member-history fields, tenant, update and deletion.
- Isolated full `money_pos_test` regression, package, scan fixtures, additions-only gate and whitespace validation passed. Registry is 8; shared imports 48; owner-local uses 53; bridges 6 and wildcard baseline 3 remain unchanged.

### Completed: AD-2.41 GMS Stock-Log Entity Physical-Ownership Migration

- Added `GmsStockLogMapperIntegrationTest` for the GMS-local ledger Entity's AUTO ID, quantity/cost/asset snapshots, order/tenant fields, update and deletion.
- Isolated full `money_pos_test` regression, package, scan fixtures, additions-only gate and whitespace validation passed. Registry is 9; shared imports 50; owner-local uses 61; bridges 6 and wildcard baseline 3.

### Completed: AD-2.47 SYS Strategy Entity Physical-Ownership Migration

- Moved `SysStrategy` from `money-app-api: com.money.entity` to `feature.sys.infrastructure.persistence.entity`. The SYS Mapper, service, controller and FIN strategy query use the SYS-local Entity.
- Replaced the former GMS-to-SYS persistence-Entity/Mapper dependency with the API-neutral `TurnoverStrategyQuery` and immutable `TurnoverStrategySnapshot`; GMS turnover now consumes only this read contract.
- Added `SysStrategyMapperIntegrationTest`, covering generated ID, every persisted strategy threshold/day/tenant field, readback, update and deletion. The GMS turnover unit test now mocks the query contract rather than a SYS Mapper.
- Removed the retired GMS-to-SYS `SysStrategy` bridge from the architecture baseline and updated the scan fixtures to a remaining registered SYS Entity. Full isolated `money_pos_test` regression, package, scan fixtures, additions-only gate and whitespace validation passed. Registry is 6; shared imports 36; owner-local uses 41; bridges 5 and wildcard baseline 3.
- Next: AD-2.48 twenty-fourth shared-Entity slice selection.

### Completed: AD-2.48 Twenty-Fourth Shared-Entity Physical-Ownership Slice Selection

- Published `MoneyPOS-AD-2.48-Twenty-Fourth-Entity-Slice-Selection.md` after re-auditing all six remaining shared Entity types. It selects SYS `SysBrandConfig` as the sole AD-2.49 migration target.
- The selection requires a narrow Entity-free brand coupon-policy query for GMS Excel import and the legacy POS facade before moving the SYS Entity; this removes the existing GMS-to-SYS Entity bridge without broadening routes, tables, DTOs or transactions.
- Next: AD-2.49 SYS brand-config Entity physical-ownership migration.

### Completed: AD-2.49 SYS Brand-Config Entity Physical-Ownership Migration

- Moved `SysBrandConfig` from `money-app-api: com.money.entity` to `feature.sys.infrastructure.persistence.entity`; updated the legacy Mapper, SYS configuration service and affected tests.
- Added the API-neutral `BrandCouponPolicyQuery`. Its SYS implementation returns immutable brand-to-coupon-enabled snapshots, so the legacy POS facade and GMS Excel import no longer import the SYS Entity or Mapper.
- Added SYS Mapper CRUD, POS enabled/disabled/missing-policy, and GMS Excel enabled/disabled-brand import regressions. Retained `sys_brand_config`, AUTO ID, tenant/audit fields, brand update-or-insert, POS coupon sanitization, Excel price/coupon semantics, routes, DTOs, Flyway and transaction boundaries.
- Removed the retired GMS-to-SYS bridge and wildcard baseline. Registry is 5; shared Feature imports are 35, owner-local uses 41, documented bridges 4 and wildcard paths 2. Isolated full regression, package, scan fixtures, additions-only gate and whitespace validation passed.
- Next: AD-2.50 twenty-fifth shared-Entity slice selection.

### Completed: AD-2.50 Twenty-Fifth Shared-Entity Physical-Ownership Slice Selection

- Published `MoneyPOS-AD-2.50-Twenty-Fifth-Entity-Slice-Selection.md` after re-auditing the five remaining registered shared Entity types. It selects UMS `PosMemberCoupon` as the sole AD-2.51 migration target.
- The member-coupon asset is UMS-owned. TRADE uses it only for unused-coupon counts by member/rule; the legacy printer only needs the unused count. AD-2.51 will replace those reads with an API-neutral wallet/count query before moving the Entity.
- Orders, order details, coupon rules and members remain deferred for their core transaction, public JSON, compatibility or broad workflow surfaces. No production source, scanner baseline, route, DTO, table, Flyway, Mapper or transaction changed in this selection task.
- Next: AD-2.51 UMS member-coupon Entity physical-ownership migration.

### Completed: AD-2.51 UMS Member-Coupon Entity Physical-Ownership Migration

- Moved `PosMemberCoupon` from `money-app-api: com.money.entity` to `feature.ums.infrastructure.persistence.entity`; updated its Mapper, UMS asset/import/profile/recharge consumers and affected tests.
- Moved the existing batch unused-count contract implementation into UMS and added `MemberCouponWalletQuery` for immutable rule-to-unused-count snapshots. TRADE coupon management and the legacy printer now consume contracts rather than the UMS Entity or Mapper.
- Added UMS Mapper CRUD and wallet/count query regressions; retained checkout/refund, coupon-rule card-pack and member-asset export coverage. AUTO ID, table/indexes, FIFO and concurrent consumption guard, refund restoration, recharge behavior, routes, DTOs, Flyway and transaction boundaries remain unchanged.
- Removed the two retired TRADE-to-UMS Entity bridges. Registry is 4; shared Feature imports are 33, owner-local uses 35, documented bridges 2 and wildcard paths 2. Isolated full regression, package, scan fixtures, additions-only gate and whitespace validation passed.
- Next: AD-2.52 twenty-sixth shared-Entity slice selection.

### Completed: AD-2.52 Twenty-Sixth Shared-Entity Physical-Ownership Slice Selection

- Published `MoneyPOS-AD-2.52-Twenty-Sixth-Entity-Slice-Selection.md` after re-auditing the four remaining registered shared Entity types. It selects UMS `PosCouponRule` as the sole AD-2.53 migration target.
- The UMS Mapper and member/card-pack/checkout benefit queries own the rule persistence. The only non-owner surface is the legacy TRADE rule-management service, whose Entity signatures are also exposed by `/pos/couponRule`; AD-2.53 must first replace that exposure with an API-neutral UMS command/query contract.
- Orders and order details remain deferred as the TRADE transaction aggregate, and `UmsMember` remains deferred for its broad profile, asset, import, checkout, FIN/HOME and public-management surface. No production source, scanner baseline, route, DTO, table, Flyway, Mapper or transaction changed in this selection task.
- Next: AD-2.53 UMS coupon-rule Entity physical-ownership migration.

### Completed: AD-2.53 UMS Coupon-Rule Entity Physical-Ownership Migration

- Moved `PosCouponRule` from `money-app-api: com.money.entity` to `feature.ums.infrastructure.persistence.entity`; updated its legacy Mapper, UMS member benefit and checkout pricing consumers, and affected test fixtures.
- Added API-neutral coupon-rule management query and command contracts with immutable management/card-pack snapshots. UMS now owns pagination, card-pack assembly and transactional writes; the legacy TRADE service delegates only to those contracts, and `/pos/couponRule` retains its routes and JSON fields without exposing the persistence Entity.
- Added UMS Mapper CRUD plus management pagination/JSON/card-pack coverage, and retained checkout pricing, member POS and checkout regressions. Table, seed, AUTO ID, filters/order, Flyway and transaction semantics remain unchanged.
- Removed the retired TRADE-to-UMS bridges. Registry is 3; shared Feature imports are 29, owner-local uses 33, documented bridges 0 and wildcard paths 2. Isolated full `money_pos_test` regression, package, scan fixtures, additions-only gate and whitespace validation passed.
- Next: AD-2.54 twenty-seventh shared-Entity slice selection.

### Completed: AD-2.54 Twenty-Seventh Shared-Entity Physical-Ownership Slice Selection

- Published `MoneyPOS-AD-2.54-Twenty-Seventh-Entity-Slice-Selection.md` after re-auditing the three remaining registered shared Entity types. It selects TRADE `OmsOrderDetail` as the sole AD-2.55 migration target.
- Its Mapper, domain service, checkout, refund, order-query and legacy printing consumers are all TRADE-owned. Controllers and external reports use DTOs or existing snapshots; no production cross-Feature Entity/Mapper or resource FQCN dependency exists, so the physical move requires no new compatibility contract.
- `OmsOrder` remains deferred as the wider transaction aggregate/report source, and `UmsMember` remains deferred for its profile, asset, import, checkout, FIN/HOME and public-management surface. AD-2.55 must retain detail cost/category/brand snapshots, atomic partial-refund guards, inventory timing, table/Flyway and all established compatibility behavior.
- Next: AD-2.55 TRADE order-detail Entity physical-ownership migration.

### Completed: AD-2.55 TRADE Order-Detail Entity Physical-Ownership Migration

- Moved `OmsOrderDetail` from `money-app-api: com.money.entity` to `feature.trade.infrastructure.persistence.entity`; updated its legacy Mapper, TRADE detail service, checkout, refund and order-query consumers, plus affected FIN/HOME/member-rank/checkout fixtures.
- Added `OmsOrderDetailMapperIntegrationTest`, covering the TRADE-local `BaseEntity` mapping and ID, every persisted order/item/price/cost/coupon/refund/tenant/brand/category snapshot field, update and deletion.
- Retained duplicate-checkout recovery, stock deduction/restoration timing, `refundGoodsAtomically` quantity guard and `REFUNDED` transition, partial/full refund behavior, order DTO, printing/report query semantics, table/Flyway and transaction boundaries.
- Registry is 2; shared Feature imports are 25, owner-local uses 25, documented bridges 0 and wildcard paths 2. Isolated full `money_pos_test` regression, package, scan fixtures, additions-only gate and whitespace validation passed.
- Next: AD-2.56 twenty-eighth shared-Entity slice selection.

### Completed: AD-2.56 Twenty-Eighth Shared-Entity Physical-Ownership Slice Selection

- Published `MoneyPOS-AD-2.56-Twenty-Eighth-Entity-Slice-Selection.md` after re-auditing the two remaining registered shared Entity types. It selects UMS `UmsMember` as the sole AD-2.57 migration target.
- UMS owns every production Entity/Mapper consumer: profile, import, asset, log, recharge, checkout/POS and FIN/HOME query implementations. Public member routes already use DTO/VO, and TRADE/FIN/HOME use existing Entity-free contracts, so no new compatibility bridge is needed.
- `OmsOrder` remains deferred as the wider TRADE transaction aggregate and report source. AD-2.57 must retain member archive, asset, recharge/reversal, checkout/refund, coupon/log, POS/rank and FIN/HOME behavior, including existing table/Flyway and transaction semantics.
- Next: AD-2.57 UMS member Entity physical-ownership migration.

### Completed: AD-2.57 UMS Member Entity Physical-Ownership Migration

- Moved `UmsMember` from `money-app-api: com.money.entity` to `feature.ums.infrastructure.persistence.entity`; updated its legacy Mapper, UMS profile/import/asset/log/recharge/query consumers, and affected test fixtures.
- Added `UmsMemberMapperIntegrationTest`, covering the UMS-local `BaseEntity` mapping and ID, every persisted archive/contact/asset/visit/delete/tenant/level field, update and deletion.
- Retained member routes/DTO/VO, logical-delete scope, import, asset export, recharge/reversal, atomic balance operations, checkout/refund, coupon/log, POS/rank and FIN/HOME snapshot behavior, table/Flyway and transaction boundaries.
- Registry is 1; shared Feature imports are 12, owner-local uses 12, documented bridges 0 and wildcard paths 2. Isolated full `money_pos_test` regression, package, scan fixtures, additions-only gate and whitespace validation passed.
- Next: AD-2.58 twenty-ninth shared-Entity slice selection.

### Completed: AD-2.58 Twenty-Ninth Shared-Entity Physical-Ownership Slice Selection

- Published `MoneyPOS-AD-2.58-Twenty-Ninth-Entity-Slice-Selection.md` after re-auditing the final registered shared Entity. It selects TRADE `OmsOrder` as the sole AD-2.59 migration target.
- Every production Entity/Mapper consumer is TRADE-owned: checkout, refund, order query and TRADE-owned FIN/HOME projections. `/oms-order` already exposes DTO/VO, while FIN/HOME and UMS consume existing snapshots/contracts rather than the Entity or Mapper; no new compatibility bridge is required.
- AD-2.59 must preserve order snapshots, payment validation/idempotency, partial/full refund state and amount transitions, public routes/DTOs, reporting formulas, table/Flyway and transaction boundaries. The two existing TRADE wildcard imports are to be narrowed during the move, not expanded.
- No production source, scanner baseline, route, DTO, table, Flyway, Mapper or transaction changed in this selection task. Baseline remains registry 1; shared imports 12; owner-local uses 12; bridges 0 and wildcard paths 2.
- Next: AD-2.59 TRADE main-order Entity physical-ownership migration.

### Completed: AD-2.59 TRADE Main-Order Entity Physical-Ownership Migration

- Moved `OmsOrder` from `money-app-api: com.money.entity` to `feature.trade.infrastructure.persistence.entity`; updated the legacy Mapper, TRADE checkout/refund/order-query/report consumers and affected FIN/HOME/UMS test fixtures.
- Added `OmsOrderMapperIntegrationTest`, covering the TRADE-local `BaseEntity` mapping and ID, every persisted member/contact/pricing/coupon/payment/refund/tenant snapshot field, update and deletion.
- Retained payment amount validation, duplicate-checkout recovery, inventory/member-asset/payment ordering, partial/full refund amount and status transitions, `/oms-order` DTO/VO and printing, FIN/HOME/member-rank projections, table/Flyway and transaction boundaries. The two retired TRADE wildcard imports now use explicit local imports.
- Removed the final ownership and wildcard baseline entries; scan fixtures now prove a retired shared Entity is unregistered. Registry, shared Feature imports, owner-local uses, documented bridges and wildcard paths are all 0. Isolated full `money_pos_test` regression, package, scan fixtures, additions-only gate and whitespace validation passed.
- AD-2 shared Entity physical ownership is closed. Next: AD-4 Maven physical modularization reassessment/design.
