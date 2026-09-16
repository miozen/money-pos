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
