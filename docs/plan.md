# Portfolio Manager: Implementation Plan (v0.1)

Sequenced work plan for building the product defined in [`intent.md`](./intent.md) using the architecture in [`spec.md`](./spec.md).

- **intent.md** says *what* and *why*. **spec.md** says *how*. **plan.md** says *in what order*.
- If the documents disagree, `intent.md` wins on scope and `spec.md` wins on mechanism. Fix the disagreement in the docs rather than silently in code.

## 1. How to use this plan

**Conventions**
- Milestones are `M0`–`M8`. Tasks are `M<milestone>-T<n>` (for example `M1-T2`).
- Requirement IDs (§3) trace every task back to `intent.md`. A task without a requirement ID is either scaffolding (marked `infra`) or a mistake.
- Spec references (`spec §5.1`) point to the design that governs the task.

**Working agreement**
1. **Test first.** Write the failing test, run it, watch it fail for the right reason, then implement, then run it green.
2. **One commit per task**, on `main`, with a message like `M1-T1: FIFO lot engine`. Do not batch tasks into one commit.
3. **Run the milestone check** (`scripts/check.sh`, created in M0-T6) before starting the next milestone.
4. **App runs at the end of every milestone.** No milestone leaves the tree broken.
5. **Scope guard.** Anything listed under Non-goals (§3, `NG-*`) is not built, even if it looks cheap.

**Definition of done (task)**
- All tests named in the task exist, ran, and pass.
- The task's verification command passes and its output was read.
- The full suite for the touched subproject (`api` or `web`) still passes.
- No new warnings in build or test output.
- Docs updated if behavior differs from `spec.md` (and the difference is recorded in the spec).

**Definition of done (milestone):** every task done, the milestone check passes, the exit criteria demo works by hand, and the traceability matrix (§6) rows for that milestone are satisfied.

## 2. Prerequisites and environment

| Item | Requirement | Notes |
|---|---|---|
| JDK | Java 21 at `/opt/homebrew/opt/openjdk@21` | Export `JAVA_HOME=/opt/homebrew/opt/openjdk@21` and put its `bin` on `PATH`. `/usr/bin/java` on this Mac is only a stub. `scripts/env.sh` (M0-T1) does this. |
| Node | Node 22 LTS via nvm | `.nvmrc` pins 22. The machine's default is Node 25 (non-LTS), so run `nvm use` first. |
| Docker | Docker Desktop running | Postgres 16 container for local development and for Testcontainers. |
| Build | Gradle wrapper (Kotlin DSL) | Use the generated `./gradlew`, not the Homebrew Gradle. |
| Spring Boot | Pin **4.1.1** explicitly | Spring Initializr currently offers only Boot 4.x. Boot 4 differs from 3.x: split starters (for example `spring-boot-starter-webmvc`, `spring-boot-starter-flyway`), separate test starters, and **Jackson 3** (`tools.jackson.*` packages). Verify each dependency's coordinates when adding it and record the working set in `api/build.gradle.kts`. Note this in the README. |
| Git | Commit per task directly on `main` | The repo starts with one docs commit. |
| Network | Only needed for real price refresh | All tests use stubs/fixtures. No test may call a live price API. |

Suggested dependency set (confirm coordinates against Boot 4.1.1 as you add them): web MVC, data JPA, validation, actuator, Flyway (+ `flyway-database-postgresql`), Postgres driver, Apache Commons CSV (added in M4-T1), Testcontainers (Postgres + JUnit), WireMock, AssertJ. Frontend: Next.js App Router, TypeScript, Tailwind, Recharts, SWR, TanStack Table, react-hook-form + zod, Vitest + Testing Library, Playwright.

## 3. Requirements index

IDs are assigned here for traceability; the text is condensed from `intent.md` (section in brackets).

**Goals [§3]**

| ID | Requirement |
|---|---|
| G-1 | Record holdings across stocks/ETFs, mutual funds/retirement accounts, crypto. |
| G-2 | Show market value, cost basis, unrealized gain/loss ($ and %) per holding and in total. |
| G-3 | Keep daily portfolio-value snapshots and show a value-over-time chart. |
| G-4 | Refresh prices on demand and record today's snapshot when it runs. |
| G-5 | Show allocation by asset type as a chart and compare with targets I set. |

**Functional [§4]**

| ID | Requirement |
|---|---|
| F-DATA-1 | Add/edit/delete accounts, holdings, and transactions; types buy, sell, split (ratio), dividend reinvestment. |
| F-DATA-2 | Splits and reinvestments are manual entries; cash dividends are not tracked. |
| F-DATA-3 | CSV import in one generic documented schema with preview and validation before commit (no mapping UI). |
| F-DATA-4 | Manual price override for instruments with no feed. |
| F-DATA-5 | Data export: transactions CSV (import schema), snapshots CSV, full JSON. |
| F-COST-1 | Sells use FIFO over internally tracked lots (not tax-lot reporting). |
| F-COST-2 | Holdings table "avg cost" is derived from remaining lots. |
| F-PRICE-1 | On-demand refresh (button) fetches prices, then upserts today's snapshot (one per date). |
| F-PRICE-2 | Yahoo Finance for stocks/ETFs/funds, CoinGecko for crypto; no automatic fallback feed. |
| F-PRICE-3 | Store last-known price with timestamp; show stale indicators when a refresh fails; manual override is the fallback. |
| F-VIEW-1 | Dashboard: total value, cost basis, unrealized $/%, day change if available. |
| F-VIEW-2 | Holdings table: symbol, quantity, avg cost, price, value, gain/loss; sortable, filterable by account and asset type. |
| F-VIEW-3 | History: line chart from stored snapshots; gaps are real; no backfill. |
| F-VIEW-4 | Allocation: chart by asset type plus target-vs-actual; targets set in settings. |
| F-CUR-1 | Single base currency (USD), no FX. |

**Platform [§2]**

| ID | Requirement |
|---|---|
| P-1 | Responsive web only. |
| P-2 | Single user; no sharing or billing. |
| P-3 | Phased delivery: Phase 1 local, Phase 2 cloud. |
| P-4 | Stack: Spring Boot (Java 21, Gradle) API + Next.js + Postgres; monorepo `/api`, `/web`, `/docs`. |
| P-5 | Cloud: Vercel frontend proxies `/api/*` to the Render API (same-origin, no CORS); API accepts only proxied traffic. |

**Non-functional [§6]**

| ID | Requirement |
|---|---|
| NFR-SEC-1 | Cloud: login required (single user, passkey), recovery via bootstrap secret, encrypted in transit and at rest, no broker credentials stored. |
| NFR-SEC-2 | Local: no login; API and UI bound to localhost only, never exposed. |
| NFR-SEC-3 | Passkey RP ID is the browser-visible domain; registration and login go through the proxy. |
| NFR-REL-1 | Refresh is idempotent, reports per-instrument failures without failing the run, is re-runnable, and concurrent refreshes are guarded. |
| NFR-NUM-1 | Money and quantities use exact decimals. |
| NFR-COST-1 | Free or low-cost hosting and free data APIs. |
| NFR-PRIV-1 | No third-party analytics. |

**Verification [§9]**

| ID | Check |
|---|---|
| V-1 | Import a real broker CSV; totals match the broker statement within rounding. |
| V-2 | A crypto, an ETF, and a manual-priced fund all appear on the dashboard with correct gain/loss. |
| V-3 | Several buys and a partial sell match a FIFO calculation; a split adjusts quantity and per-share cost. |
| V-4 | Target allocations set; allocation chart and target-vs-actual render correctly. |
| V-5 | Refresh updates prices and creates/updates today's snapshot (twice = one row); simulated API failure shows stale indicators without breaking the dashboard. |
| V-6 | The value-over-time chart reflects the snapshots. |
| V-7 | All of the above work in the local deployment. |
| V-8 | Cloud: unreachable without login; API rejects requests not via the Next.js proxy. |
| V-9 | Passkey registration and login work end to end through the proxy. |

**Non-goals [§5], used as scope guards (never implemented in v1)**

| ID | Excluded |
|---|---|
| NG-1 | Trading/order execution. |
| NG-2 | Rebalancing recommendations or trade suggestions. |
| NG-3 | Tax reporting, tax lots, wash sales. |
| NG-4 | TWR/MWR, dividends, realized gains. |
| NG-5 | Native mobile app, push notifications. |
| NG-6 | Aggregator sync and direct exchange APIs. |
| NG-7 | Multi-currency. |
| NG-8 | Scheduled/automated refresh. |
| NG-9 | Snapshot backfill from historical prices. |
| NG-10 | Column-mapping UI, broker-specific CSV parsers. |
| NG-11 | Transaction fees/commissions (decision, intent §7). |
| NG-12 | Automatic fallback price feed (decision, intent §7). |

## 4. Milestone overview

| # | Milestone | Phase | Delivers | Key requirements |
|---|---|---|---|---|
| M0 | Scaffold | 1 | Monorepo, DB, API and web skeletons, proxy, scripts | P-4, NFR-SEC-2, infra |
| M1 | Domain core | 1 | FIFO engine, valuation, allocation math, staleness policy | F-COST-1/2, NFR-NUM-1, V-3 |
| M2 | Data entry and holdings | 1 | CRUD, holdings, dashboard, design system and core screens | G-1, G-2, F-DATA-1/2/4, F-VIEW-1/2, V-2 |
| M3 | Pricing, refresh, snapshots | 1 | Providers, async refresh, snapshots, history chart | G-3, G-4, F-PRICE-1/2/3, F-VIEW-3, NFR-REL-1, V-5, V-6 |
| M4 | CSV import and export | 1 | Import with preview, exports | F-DATA-3/5, V-1 |
| M5 | Allocation | 1 | Allocation view and target editor | G-5, F-VIEW-4, V-4 |
| M6 | Local hardening and acceptance | 1 | E2E, demo data, offline check, a11y/responsive, acceptance record | V-1..V-7, P-1, NFR-PRIV-1 |
| M7 | Cloud auth | 2 | Proxy-only access, passkeys, sessions | NFR-SEC-1/3, P-5, V-8, V-9 |
| M8 | Cloud deployment | 2 | Render + Vercel, smoke tests, runbook | P-3, P-5, NFR-COST-1, V-8, V-9 |

**Dependencies:** M0 → M1 → M2 → M3 → M4 → M5 → M6 (**Phase 1 gate**) → M7 → M8. M1 has no dependency on Spring or the DB and can be built and tested first. M4 needs M2 (transactions) and M3 (snapshots for export). M5 needs M2 (targets) and M3 (prices). **M7 does not start until M6's acceptance is signed off by you**, matching the "ensure local works before cloud" decision.

Package layout in `api` (spec §3): `com.portfoliomanager.{domain, persistence, application, pricing, importing, auth, web}`. Dependency rule: `web → application → domain`; `domain` imports nothing.

---

## M0. Scaffold

**Goal:** an empty but runnable monorepo with tests wired, ready for features.
**Requirements:** P-4, NFR-SEC-2 (bind guard), infra. **Spec:** §2, §3, §4, §11 (local), §12.
**Exit criteria:** `scripts/dev.sh` starts Postgres, API, and web; `http://127.0.0.1:3000` loads; a request to `/api/actuator/health/liveness` through the Next proxy returns `UP`; `scripts/check.sh` is green.

### M0-T1: Repo skeleton and toolchain (infra)
- **Files:** `.nvmrc` (`22`), `scripts/env.sh` (exports `JAVA_HOME`, `PATH`, sources nvm and runs `nvm use`), `docker-compose.yml`, `.gitignore` (extend: `api/build`, `api/.gradle`, `web/node_modules`, `web/.next`, `.env*`), `README.md` (a short stub here: prerequisites, commands, Boot 4 note; it is expanded into the full README in M6-T6).
- **Steps:** create files. `docker-compose.yml` runs `postgres:16` with db/user/password `portfolio`, published on `127.0.0.1:5432`, named volume, `pg_isready` healthcheck.
- **Verify:** `source scripts/env.sh && java -version` shows 21; `node -v` shows 22; `docker compose up -d db` then `docker compose ps` shows healthy.

### M0-T2: API project, profiles, loopback guard (NFR-SEC-2, P-4)
- **Files:** `api/` (Gradle Kotlin DSL, Boot 4.1.1, Java 21 toolchain), `application.yml`, `application-local.yml`, `com/portfoliomanager/config/LoopbackGuard.java`, test `LoopbackGuardTest.java`.
- **Steps:** (1) Write `LoopbackGuardTest`: `"0.0.0.0"` throws `IllegalStateException`; `127.0.0.1`, `::1`, `localhost` pass. Run, see it fail. (2) Implement `LoopbackGuard` as a `@Profile("local")` startup check that reads `server.address`; absent address counts as non-loopback. (3) Config: `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false`, `app.timezone=${APP_TIMEZONE:UTC}`; `local` profile: datasource `jdbc:postgresql://localhost:5432/portfolio`, `server.address=127.0.0.1`, port 8080, expose only `health` on actuator. (4) Add the `/api` context: controllers map under `/api/...` (set via a shared `@RequestMapping("/api")` convention or `server.servlet.context-path` is **not** used, so the health path is `/actuator/health/liveness` and the proxy maps `/api/actuator/...` only if you choose; decide and record).
- **Verify:** `./gradlew test` green; `./gradlew bootRun --args=--spring.profiles.active=local` then `curl 127.0.0.1:8080/actuator/health/liveness`.

### M0-T3: Flyway baseline schema (spec §4, NFR-NUM-1)
- **Files:** `db/migration/V1__baseline.sql`, `MigrationIT.java`, `AbstractIntegrationTest.java` (singleton Testcontainers Postgres 16).
- **Steps:** (1) Write `MigrationIT`: context boots and Flyway history has V1; inserting a `SELL` transaction with `quantity = 0` violates a check; inserting a `SPLIT` with a quantity violates a check; inserting a second `refresh_run` with status `RUNNING` violates the partial unique index; `snapshot_holding` FK to `snapshot` cascades on delete. (2) Run: fails. (3) Write V1 for `account`, `instrument`, `transaction` (with `seq bigserial`), `price`, `snapshot`, `snapshot_holding`, `target_allocation`, `refresh_run`, `import_batch`, `import_row`, exactly as spec §4 (types `NUMERIC(24,8)` for quantity/price, `NUMERIC(20,4)` for totals, enums as `text` with check constraints, unique `(symbol, asset_type)` on instrument, index `(account_id, instrument_id, trade_date, seq)`). Auth/session tables are added in M7. (4) Green.
- **Verify:** `./gradlew test --tests '*MigrationIT'`.

### M0-T4: Web project (P-4, P-1, NFR-PRIV-1)
- **Files:** `web/` from `create-next-app` (TypeScript, Tailwind, App Router, ESLint, no `src/`), `vitest.config.ts`, `playwright.config.ts`, `web/.nvmrc`.
- **Steps:** scaffold; add dependencies from §2; configure Vitest (jsdom, Testing Library setup); add a trivial passing test; ensure no external font or script URLs (`next/font` self-host only).
- **Verify:** `npm run test`, `npm run build`, `npm run lint` all pass on Node 22.

### M0-T5: `/api` proxy route handler (P-5 local half, spec §2)
- **Files:** `web/app/api/[...path]/route.ts`, `web/lib/proxy.ts`, `web/lib/proxy.test.ts`.
- **Steps:** (1) Test with a stubbed `fetch`: path and query forwarded; method and JSON body forwarded; upload bodies stream (`duplex: "half"`); upstream status codes (`202`, `409`, `422`) and `content-type` pass through; cookies pass through both ways; hop-by-hop headers dropped; when `PROXY_SECRET` is set, `X-Proxy-Secret` is attached; upstream unreachable → `502` problem+json. (2) Implement handlers for GET/POST/PUT/DELETE using `API_BASE_URL` (default `http://127.0.0.1:8080`). The handler forwards `/api/*` to the same path on the API.
- **Verify:** `npm run test`; with the API up, `curl localhost:3000/api/actuator/health/liveness` → `UP` (or whichever health path was chosen in M0-T2, applied consistently).

### M0-T6: Dev scripts (infra)
- **Files:** `scripts/dev.sh`, `scripts/reset-db.sh`, `scripts/check.sh`.
- **Steps:** `dev.sh` sources `env.sh`, starts the DB and waits for health, starts the API (`local` profile) and `npm run dev` bound to `127.0.0.1`, and stops both on Ctrl-C. `reset-db.sh` recreates the volume after an explicit `y` prompt. `check.sh` runs `./gradlew test` then `npm run test && npm run lint && npm run build`, and fails fast.
- **Verify:** run `scripts/dev.sh`, open the page, stop with Ctrl-C (no orphan processes); `scripts/check.sh` exits 0.

---

## M1. Domain core (pure Java)

**Goal:** all money math implemented and proven, with no Spring or DB involved.
**Requirements:** F-COST-1, F-COST-2, G-2, NFR-NUM-1, V-3. **Spec:** §5, §6 (staleness).
**Exit criteria:** `./gradlew test --tests 'com.portfoliomanager.domain.*'` green; the spec §5.1 worked example passes as a test; `domain` package has no imports from Spring, JPA, or Jackson.

### M1-T1: Transactions, lots, FIFO engine (F-COST-1, NFR-NUM-1, V-3)
- **Files:** `domain/{TxnType,Txn,Lot,Position,Violation,ReplayResult,FifoEngine}.java`; `FifoEngineTest.java`.
- **Interfaces:** `Txn(UUID id, long seq, TxnType type, LocalDate date, BigDecimal quantity, BigDecimal unitPrice, int splitNum, int splitDen)`; `Position(List<Lot> lots)` with `quantity()`, `costBasis()`, `avgCost()` (empty when quantity is 0); `FifoEngine.replay(List<Txn>) → ReplayResult(position, Optional<Violation>)` (sorts by `(date, seq)`).
- **Steps (tests first, each watched failing):** (1) spec §5.1 example (buy 10@100, buy 10@120, sell 15, split 2:1 → 10 @ 60, basis 600); (2) partial lot consumption keeps original unit cost; (3) oversell yields a `Violation` naming the sell's id; (4) reverse split 1:10; (5) fractional crypto `0.00000001` keeps exact basis; (6) REINVEST behaves as a buy; (7) same-date ordering by `seq` (a sell with lower `seq` than its buy is a violation); (8) input list is not mutated; (9) empty history → empty position. Then implement: `ArrayDeque` of lots; SELL consumes from the head; SPLIT scales quantity by `n/m` and unit cost by `m/n`; intermediates at scale 8 `HALF_UP`; total basis summed unrounded and rounded once to scale 4.
- **Verify:** `./gradlew test --tests '*FifoEngineTest'`.

### M1-T2: Valuation (G-2, F-COST-2, F-PRICE-3)
- **Files:** `domain/{AssetType,PriceState,PriceInput,PositionValue,PortfolioTotals,Valuation}.java`; `ValuationTest.java`.
- **Steps:** tests: value = qty × price; unrealized and % (empty when basis is 0); unpriced position excluded from totals and counted; stale position included and counted; day change only when `prevPrice` exists and totals sum only positions that have it; totals unrealized = value − basis over priced positions only. Implement per spec §5.3 with scale-4 money and percentages.
- **Verify:** `./gradlew test --tests '*ValuationTest'`.

### M1-T3: Allocation (G-5, F-VIEW-4)
- **Files:** `domain/{AllocationRow,Allocation}.java`; `AllocationTest.java`.
- **Steps:** tests: actual % per asset type sums to exactly 100.00 (rounding remainder goes to the largest bucket); drift = actual − target; targets not summing to 100.00 throw `IllegalArgumentException`; no targets → actuals only; zero total value → all zero without division errors. Implement per spec §5.4.
- **Verify:** `./gradlew test --tests '*AllocationTest'`.

### M1-T4: Staleness policy (F-PRICE-3)
- **Files:** `pricing/StalenessPolicy.java`; `StalenessPolicyTest.java`.
- **Steps:** tests: feed error → `STALE`; price date 4 days old → `OK`, 5 days → `STALE`; manual price 45 days → `MANUAL`, 46 → `MANUAL_STALE`; no price and no manual → `UNPRICED`. Implement with thresholds as named constants (spec §6). Keep this class free of Spring imports so it stays unit-testable.
- **Verify:** `./gradlew test --tests '*StalenessPolicyTest'`, then the whole `domain` and `pricing` test packages.

---

## M2. Data entry and holdings

**Goal:** enter accounts, instruments, and transactions and see correct holdings and dashboard, in a designed UI. Prices are entered manually in this milestone (feeds arrive in M3).
**Requirements:** G-1, G-2, F-DATA-1, F-DATA-2, F-DATA-4, F-COST-1/2, F-VIEW-1, F-VIEW-2, F-CUR-1, P-1, V-2 (manual-priced part), V-3. **Spec:** §4, §5, §9, §10.
**Exit criteria:** by hand, create an account, an ETF and a manually priced fund, enter buys/sell/split, and see the correct dashboard and holdings; `scripts/check.sh` green.

### Backend

### M2-T1: Entities and repositories (infra)
- **Files:** `persistence/{AccountEntity,InstrumentEntity,TransactionEntity,PriceEntity,TargetAllocationEntity}.java` and Spring Data repositories; `PersistenceIT.java`.
- **Steps:** tests first: round-trip each entity with `BigDecimal` scale preserved (8 dp quantity, 4 dp totals); unique `(symbol, asset_type)`; `TransactionEntity.seq` is DB-generated and monotonic. Implement entities with `NUMERIC` column mappings and `validate` DDL mode.
- **Verify:** `./gradlew test --tests '*PersistenceIT'`.

### M2-T2: Error handling and JSON conventions (infra, NFR-NUM-1)
- **Files:** `web/{ApiExceptionHandler,ProblemFactory}.java`, `web/dto/*`, `ApiErrorIT.java`.
- **Steps:** tests: validation failures → `400` `application/problem+json` with `errors[{field,message}]`; not found → `404`; conflict → `409`; oversell → `422`. Decimals serialize as strings and deserialize from strings (configure Jackson 3 accordingly); dates ISO-8601. Implement exception types `NotFound`, `Conflict`, `OversellException`.
- **Verify:** `./gradlew test --tests '*ApiErrorIT'`.

### M2-T3: Accounts API (G-1, F-DATA-1)
- **Files:** `application/AccountService.java`, `web/AccountController.java`, `AccountApiIT.java`.
- **Steps:** tests: create → `201`; duplicate name → `409`; list; update; delete an empty account → `204`; delete an account that has transactions → `409`. Implement `GET/POST /api/accounts`, `PUT/DELETE /api/accounts/{id}`. Account types `BROKERAGE|RETIREMENT|CRYPTO|OTHER`.
- **Verify:** `./gradlew test --tests '*AccountApiIT'`.

### M2-T4: Instruments and manual price (G-1, F-DATA-1, F-DATA-4, F-PRICE-3)
- **Files:** `application/InstrumentService.java`, `web/InstrumentController.java`, `InstrumentApiIT.java`.
- **Steps:** tests: defaults by asset type (stock/ETF/fund → `YAHOO`, crypto → `COINGECKO`); crypto without `sourceId` → `400`; unique `(symbol, asset_type)` → `409`; `PUT /api/instruments/{id}/manual-price {price, asOf}` sets manual price; `DELETE` on the same path clears it; switching `priceSource` to `MANUAL` is allowed (fallback path); deleting an instrument referenced by transactions → `409`. Implement `GET/POST/PUT/DELETE /api/instruments` plus the manual-price endpoints.
- **Verify:** `./gradlew test --tests '*InstrumentApiIT'`.

### M2-T5: Transactions API with replay validation (F-DATA-1, F-DATA-2, F-COST-1, V-3)
- **Files:** `application/TransactionService.java`, `application/PositionLoader.java`, `web/TransactionController.java`, `TransactionApiIT.java`.
- **Interfaces:** `TransactionService.replayFor(UUID accountId, UUID instrumentId): ReplayResult`.
- **Steps:** tests: buy then sell 15 of 10 → `422` naming the offending transaction; buy, sell, then **delete the buy** → `422` and DB unchanged; editing a sell above holdings → `422`; SPLIT requires both ratio fields and forbids quantity/price; BUY/SELL/REINVEST require quantity > 0 and price ≥ 0 (`400` otherwise); cash dividends have no transaction type (attempting one → `400`); list filters `accountId`, `instrumentId`, `type`, `from`, `to`, paging. Implement `GET/POST /api/transactions`, `PUT/DELETE /api/transactions/{id}`: mutate, replay that position in the same DB transaction, roll back on violation.
- **Verify:** `./gradlew test --tests '*TransactionApiIT'`.

### M2-T6: Holdings service and endpoint (G-2, F-COST-2, F-VIEW-2, V-2)
- **Files:** `application/PortfolioService.java`, `web/{HoldingsController,dto/HoldingRow}.java`, `HoldingsApiIT.java`.
- **Steps:** tests (seed via repositories): crypto + ETF + manual-priced fund each show correct value and gain/loss; avg cost derives from remaining lots after a partial sell; zero-quantity positions omitted; filters by `accountId` and `assetType`; sorting by any column; unpriced instrument → `priceStatus: UNPRICED` with null value; response shape matches spec §9 example (decimal strings). Implement by loading all transactions once, grouping by `(account, instrument)`, replaying, valuing with the effective price (spec §5.3: manual for `MANUAL` sources, else stored feed price).
- **Verify:** `./gradlew test --tests '*HoldingsApiIT'`.

### M2-T7: Dashboard endpoint (G-2, F-VIEW-1)
- **Files:** `web/DashboardController.java`, `DashboardApiIT.java`.
- **Steps:** tests: totals (value, cost basis, unrealized, %) over priced positions only; `unpricedPositions` and `stalePositions` counts; `dayChange` absent unless at least one position has a previous price; empty database → zeros, no exception; `lastRefresh` null until M3. Implement `GET /api/dashboard`.
- **Verify:** `./gradlew test --tests '*DashboardApiIT'`.

### M2-T8: Target allocation storage (G-5, F-VIEW-4)
- **Files:** `application/TargetAllocationService.java`, `web/TargetAllocationController.java`, `TargetAllocationApiIT.java`.
- **Steps:** tests: `PUT /api/allocation/targets` with values summing to 100.00 → `200`; sum ≠ 100.00 → `400`; negative or > 100 → `400`; `GET` returns stored targets; empty set allowed (clears targets). Implement storage and validation using `Allocation.validateTargets`. (The computed `GET /api/allocation` view lands in M5.)
- **Verify:** `./gradlew test --tests '*TargetAllocationApiIT'`.

### Frontend

### M2-T9: Design system and app shell (P-1, NFR-PRIV-1, infra)
- **Files:** `web/app/{layout.tsx,globals.css}`, `web/components/ui/*` (Button, Card, Badge, Input, Select, Dialog, Tabs, Toast, Skeleton, EmptyState, DataTable), `web/components/{AppShell,Sidebar,TopBar,ThemeToggle}.tsx`.
- **Steps:** invoke the `frontend-design` skill first to settle direction. Define tokens as CSS variables (color, spacing, radius, type scale) with light and dark themes; follow the system theme with a persisted toggle (`localStorage` inside try/catch, page correct without it). Fonts via `next/font` only. Sidebar (Dashboard, Holdings, Allocation, History, Transactions, Import, Settings) collapses to a bottom tab bar under 768 px. Focus rings, reduced-motion support, keyboard-operable dialogs. Add a Vitest test for the theme toggle and for the DataTable's empty state.
- **Verify:** `npm run test`; `npm run build`; manual check at 1280 px and 400 px in both themes.

### M2-T10: API client and formatters (NFR-NUM-1, F-CUR-1)
- **Files:** `web/lib/{api.ts,useApi.ts,format.ts,decimal.ts,types.ts}`, `format.test.ts`, `api.test.ts`.
- **Steps:** tests: `formatMoney("1234.5") → "$1,234.50"`; negative and null (`—`) handling; 8-dp crypto quantity keeps every digit; values above `2^53` never pass through `Number` (string-based formatting or `BigInt`); `ApiError` carries status and problem body; `422` messages surface unchanged. Implement `api.get/post/put/del`, an SWR wrapper, and formatters (display only, no arithmetic). `Money` and `Delta` components show a sign and arrow, never colour alone.
- **Verify:** `npm run test`.

### M2-T11: Settings screen: accounts and instruments (G-1, F-DATA-1, F-DATA-4)
- **Files:** `web/app/settings/page.tsx`, `web/components/{AccountForm,InstrumentForm,ManualPriceForm}.tsx`, tests.
- **Steps:** tests: zod validation (crypto requires `sourceId`); manual price form requires price and as-of date; server `409` messages shown inline. Build account list/create/edit/delete; instrument list/create/edit with price source, source id, and manual price plus "switch to manual". Deletions use an in-page confirmation (no `window.confirm`).
- **Verify:** `npm run test`; create an account and instruments by hand against the running API.

### M2-T12: Transactions screen (F-DATA-1, F-DATA-2, V-3)
- **Files:** `web/app/transactions/page.tsx`, `web/components/{TransactionForm,TransactionsTable}.tsx`, tests.
- **Steps:** tests: form fields switch by type (SPLIT shows ratio only; BUY/SELL/REINVEST show quantity and price); rejects non-positive quantity; a server `422` oversell message renders beside the form; filters (account, instrument, type, dates). Build list plus add/edit/delete dialog with SWR revalidation.
- **Verify:** `npm run test`; enter buys, a partial sell, and a split by hand; then try an oversell and confirm the error.

### M2-T13: Holdings and Dashboard screens (G-2, F-VIEW-1, F-VIEW-2, V-2)
- **Files:** `web/app/page.tsx`, `web/app/holdings/page.tsx`, `web/components/{KpiCard,HoldingsTable,StaleBadge,PricingBanner}.tsx`, tests.
- **Steps:** tests: HoldingsTable sorts (value asc/desc, gain %), filters by account and asset type, shows a badge for `STALE`/`MANUAL_STALE`, and "No price" for `UNPRICED`. Dashboard: KPI cards (total value, cost basis, unrealized $ and %, day change only when present); banner when `stalePositions` or `unpricedPositions` > 0; real empty state with a "Add your first transaction" call to action. Tables scroll horizontally inside their own container at 400 px.
- **Verify:** `npm run test`; by hand confirm the crypto/ETF/manual-fund scenario shows correct gain/loss.

**M2 milestone check:** `scripts/check.sh`; manual walkthrough of the exit criteria.

---

## M3. Pricing, refresh, snapshots, history

**Goal:** the Refresh button fetches prices, tolerates failures, and records daily snapshots that feed the history chart.
**Requirements:** G-3, G-4, F-PRICE-1, F-PRICE-2, F-PRICE-3, F-VIEW-3, NFR-REL-1, V-5, V-6, NG-8/9/12 (scope guards). **Spec:** §6, §7.
**Exit criteria:** with the stub provider profile, Refresh twice on the same day leaves one snapshot row; a failing provider shows stale badges and a warning banner while the dashboard still renders; the history chart shows the snapshot points. `scripts/check.sh` green.

### M3-T1: Provider contract and Yahoo adapter (F-PRICE-2)
- **Files:** `pricing/{PriceSource,PriceProvider,PriceResult,YahooProvider,PricingProperties}.java`, `YahooProviderTest.java`, `src/test/resources/fixtures/yahoo-*.json`.
- **Steps:** WireMock tests first: parses last close, prior close, and date from a recorded `v8/finance/chart?range=5d&interval=1d` fixture; unknown ticker (`chart.error`) → `PriceResult` with an error message; `500` retried once, then reported as an error; `4xx` not retried; read timeout → error result, not an exception; a browser-like `User-Agent` is sent; at most 5 requests in flight. Implement with `java.net.http.HttpClient` (5 s connect, 10 s request), virtual-thread executor, base URL from `app.pricing.yahoo-base-url`. No fallback provider (NG-12).
- **Verify:** `./gradlew test --tests '*YahooProviderTest'`.

### M3-T2: CoinGecko adapter (F-PRICE-2)
- **Files:** `pricing/CoinGeckoProvider.java`, `CoinGeckoProviderTest.java`, fixtures.
- **Steps:** tests: one batched call for many ids; `prevPrice = price / (1 + change24h/100)`; an id missing from the response gets a per-id error; `x-cg-demo-api-key` header only when a key is configured; `429` → error for every id; no exception escapes. Implement.
- **Verify:** `./gradlew test --tests '*CoinGeckoProviderTest'`.

### M3-T3: Price persistence and effective price (F-PRICE-3, F-DATA-4)
- **Files:** `application/PriceService.java`, `PriceServiceIT.java`; update `PortfolioService`.
- **Steps:** tests: a failed fetch sets `status=ERROR` and `last_error` while `price`, `prev_price`, and `fetched_at` keep the last good values; a successful fetch overwrites and clears the error; `MANUAL` instruments are never sent to providers; effective price selection (manual vs feed) and `priceState` from `StalenessPolicy` flow into holdings and dashboard (`priceStatus`, counts). Implement per spec §5.3 and §6.
- **Verify:** `./gradlew test --tests '*PriceServiceIT' --tests '*HoldingsApiIT' --tests '*DashboardApiIT'`.

### M3-T4: Single-flight refresh runs (NFR-REL-1, F-PRICE-1)
- **Files:** `application/{RefreshService,RefreshWorker}.java`, `persistence/RefreshRunEntity.java`+repo, `RefreshServiceIT.java`.
- **Steps:** tests with fake providers and a fixed `Clock`: starting a run inserts `RUNNING`; a second start while `RUNNING` returns a conflict carrying the existing run id; two concurrent starts → exactly one wins; a `RUNNING` row older than 5 minutes is marked `FAILED` and a new run may start; only instruments with an open position are fetched; results list each instrument with `ok`/`error` and message; final status `SUCCEEDED`, `PARTIAL`, or `FAILED` per spec §7. Implement with the partial unique index as the guard and a virtual-thread worker.
- **Verify:** `./gradlew test --tests '*RefreshServiceIT'`.

### M3-T5: Snapshot service (G-3, G-4, F-PRICE-1, V-5)
- **Files:** `application/SnapshotService.java`, `persistence/{SnapshotEntity,SnapshotHoldingEntity}.java`+repos, `SnapshotServiceIT.java`, `Clock` bean.
- **Steps:** tests with a fixed clock: a first run writes one `snapshot` row and per-holding rows with `is_stale` flags; a second run the same day **still one `snapshot` row** and holdings replaced, not duplicated; the date is computed in `app.timezone` (default UTC) — test a late-evening US timestamp that lands on the next UTC date; a `FAILED` run leaves the existing snapshot untouched; zero open positions → snapshot with zero totals. Implement with `INSERT ... ON CONFLICT (snap_date) DO UPDATE` and a single transaction.
- **Verify:** `./gradlew test --tests '*SnapshotServiceIT'`.

### M3-T6: Refresh and history endpoints (G-3, G-4, V-5, V-6)
- **Files:** `web/{RefreshController,SnapshotController}.java`, `RefreshApiIT.java`, `SnapshotApiIT.java`.
- **Steps:** tests: `POST /api/refresh` → `202 {runId}`; second call while running → `409 {runId}`; `GET /api/refresh/{id}` and `/latest` return status and per-instrument results; `GET /api/snapshots?from&to` returns `[{date,totalValue,totalCostBasis}]` ordered ascending and filtered by range; dashboard `lastRefresh` populated. Implement. A `e2e` Spring profile swaps real providers for a configurable stub bean (used by M6).
- **Verify:** `./gradlew test --tests '*RefreshApiIT' --tests '*SnapshotApiIT'`.

### M3-T7: Refresh UX (G-4, F-PRICE-3, NFR-REL-1, V-5)
- **Files:** `web/components/{RefreshButton,RefreshProgress,LastRefreshed}.tsx`, `web/lib/useRefresh.ts`, tests.
- **Steps:** tests: clicking Refresh posts once and disables the button while running; polls `/api/refresh/{id}` every 1 s until finished; `409` attaches to the existing run instead of erroring; `PARTIAL` shows a warning banner listing failed symbols; `FAILED` shows an error toast but leaves all pages usable; after completion the dashboard, holdings, and history data are revalidated; after 3 s pending, show a "waking up the server…" hint. Wire the button into the top bar and the dashboard.
- **Verify:** `npm run test`; by hand with the stub profile, including a forced failure.

### M3-T8: History screen (G-3, F-VIEW-3, V-6)
- **Files:** `web/app/history/page.tsx`, `web/components/ValueChart.tsx`, `ValueChart.test.tsx`.
- **Steps:** tests: 3 snapshots render 3 points; 0 snapshots render the empty state explaining that history starts at the first Refresh; range selector (1M/6M/1Y/All) filters points; gaps are not interpolated (a missing day has no point); tooltip shows exact decimal strings. Build Recharts line/area chart of total value with cost basis as a second series.
- **Verify:** `npm run test`; by hand: refresh on two different (stubbed) dates and confirm two points.

**M3 milestone check:** `scripts/check.sh`; manual run of the exit criteria.

---

## M4. CSV import and export

**Goal:** bring in transaction history from a CSV and take data back out.
**Requirements:** F-DATA-3, F-DATA-5, NG-10 (scope guard), V-1. **Spec:** §8, §9 (Import, Export).
**Exit criteria:** import a sample CSV through the UI with preview → commit; holdings match hand calculation; export the transactions CSV, wipe the database, re-import, and get identical holdings.

### M4-T1: CSV parser (F-DATA-3)
- **Library (decision):** Apache Commons CSV (`org.apache.commons:commons-csv`), not a hand-written parser. Pin the version in `api/build.gradle.kts` and confirm it resolves under Boot 4.1.1's dependency management. `CsvParser` is a thin wrapper so the rest of `importing` never touches the library directly.
- **Files:** `importing/{CsvParser,CsvRow,CsvLimits}.java`, `CsvParserTest.java`, `src/test/resources/csv/*.csv`.
- **Steps:** tests first: UTF-8 BOM stripped; CRLF and LF; quoted commas and quoted newlines; header names case-insensitive and trimmed; unknown columns ignored with a warning; > 2 MB → rejected; > 5,000 rows → rejected; empty file and header-only file give clear errors. Implement `CsvParser` on top of Commons CSV: strip a leading BOM before parsing (check the byte order mark on the input stream, since Commons CSV does not do it for you), configure `CSVFormat` with a header row, `ignoreSurroundingSpaces`, `ignoreEmptyLines`, and case-insensitive header lookup, enforce the 2 MB cap on the byte stream before parsing, and stop reading once the 5,000-row cap is exceeded rather than loading the whole file. Wrap the library's `IOException`/`IllegalArgumentException` in a domain `CsvFormatException` with the line number.
- **Verify:** `./gradlew test --tests '*CsvParserTest'`.

### M4-T2: Row and position validation (F-DATA-3, F-COST-1, V-1)
- **Files:** `importing/{ImportValidator,ImportIssue}.java`, `ImportValidatorTest.java`.
- **Steps:** tests per the spec §8 schema: required columns by type; enum/date/decimal parsing; `2:1` ratio parsing (`0:1`, `2:0`, `a:b` rejected); crypto row for a new instrument without `source_id` → error; new account/instrument → `WILL_CREATE`; duplicates within the file and against existing transactions → `WARNING`; **an oversell across file rows, or against existing history, is reported as an error on the offending line**. Implement by virtually merging staged rows with existing transactions and replaying each affected position through `FifoEngine`.
- **Verify:** `./gradlew test --tests '*ImportValidatorTest'`.

### M4-T3: Staging and commit endpoints (F-DATA-3)
- **Files:** `importing/ImportService.java`, `persistence/{ImportBatchEntity,ImportRowEntity}.java`+repos, `web/ImportController.java`, `ImportApiIT.java`.
- **Steps:** tests: `POST /api/imports` (multipart) returns a preview with per-row status and a summary, and writes nothing to real tables; `GET /api/imports/{id}` returns it again; commit blocked (`422`) when any row has an error; commit is all-or-nothing (force a failure on the last row and assert zero transactions inserted); commit creates missing accounts and instruments; flagged duplicates are skipped unless `includeDuplicates=true`; committing twice → `409`; file order preserved in `seq`; `DELETE` discards a staged batch; batches older than 24 h are purged when a new import starts. Implement.
- **Verify:** `./gradlew test --tests '*ImportApiIT'`.

### M4-T4: Export endpoints (F-DATA-5)
- **Files:** `web/ExportController.java`, `application/ExportService.java`, `ExportApiIT.java`.
- **Steps:** tests: `GET /api/export/transactions.csv` uses exactly the import schema; **round trip** (export, clear tables, import, holdings identical); `snapshots.csv` columns `date,total_value,total_cost_basis`; `all.json` contains accounts, instruments, transactions, manual prices, targets, and snapshots with decimals as strings; correct `Content-Type` and `Content-Disposition`; empty database exports headers only. Implement.
- **Verify:** `./gradlew test --tests '*ExportApiIT'`.

### M4-T5: Import UI (F-DATA-3, V-1)
- **Files:** `web/app/import/page.tsx`, `web/components/{ImportDropzone,ImportPreview}.tsx`, `web/public/sample-transactions.csv`, tests.
- **Steps:** tests: preview renders row statuses (ok, will-create, warning, error) with per-row messages; Commit disabled while any error exists; "include duplicates" toggle sets the query param; result summary after commit; client-side size check (2 MB) before upload. Build a drag-and-drop or file picker, an inline schema reference table, and a downloadable sample CSV.
- **Verify:** `npm run test`; by hand import the sample and a deliberately broken file.

### M4-T6: Export UI (F-DATA-5)
- **Files:** `web/components/ExportPanel.tsx` (placed in Settings), test.
- **Steps:** tests: three download actions (transactions CSV, snapshots CSV, full JSON); "last exported" date stored in `localStorage` (try/catch; page correct without it); a nudge after 30 days without an export. Downloads use ordinary links to `/api/export/*` served with `Content-Disposition`.
- **Verify:** `npm run test`; by hand download all three and open them.

**M4 milestone check:** `scripts/check.sh`; the round-trip exit criterion by hand.

---

## M5. Allocation

**Goal:** see how the portfolio is split by asset type and how far it is from my targets.
**Requirements:** G-5, F-VIEW-4, NG-2 (no rebalancing suggestions), V-4. **Spec:** §5.4, §9, §10.
**Exit criteria:** with targets set to sum to 100, the chart and the target-vs-actual table show correct percentages and drift; `scripts/check.sh` green.

### M5-T1: Allocation endpoint (G-5, F-VIEW-4)
- **Files:** `application/AllocationService.java`, `web/AllocationController.java`, `AllocationApiIT.java`.
- **Steps:** tests: `GET /api/allocation` returns rows per asset type with `value`, `actualPct`, `targetPct`, `driftPct`; unpriced positions excluded from values; no targets → target and drift null; zero portfolio value → zero percentages. Implement using `Allocation.compute` and holdings values. Return no buy/sell suggestions (NG-2).
- **Verify:** `./gradlew test --tests '*AllocationApiIT'`.

### M5-T2: Allocation screen and target editor (G-5, F-VIEW-4, V-4)
- **Files:** `web/app/allocation/page.tsx`, `web/components/{AllocationDonut,DriftTable,TargetEditor}.tsx`, tests.
- **Steps:** tests: TargetEditor shows a running total and the remaining amount, disables Save unless the total is exactly 100.00, and shows the server's `400` message; DriftTable shows actual, target, and drift with sign and bar; donut uses accessible labels (pattern or text, not colour alone); empty state when there are no holdings; view shows actuals only when no targets are set. Link the target editor from Settings.
- **Verify:** `npm run test`; by hand set targets and confirm figures against a manual calculation.

**M5 milestone check:** `scripts/check.sh`.

---

## M6. Local hardening and acceptance (Phase 1 gate)

**Goal:** prove the whole local app works together, including when the network is down, and record the evidence.
**Requirements:** V-1 … V-7, P-1, NFR-SEC-2, NFR-PRIV-1, NFR-REL-1. **Spec:** §10, §12, §13.
**Exit criteria:** Playwright suite green; `docs/acceptance-local.md` records pass/fail for V-1..V-7 with dates; you sign off before M7 starts.

### M6-T1: E2E golden path (V-2, V-3, V-4, V-5, V-6, V-7)
- **Files:** `web/e2e/golden-path.spec.ts`, `web/e2e/fixtures.ts`, API `e2e` profile stub provider (from M3-T6) with a control endpoint enabled only under that profile.
- **Steps:** write the spec first and watch it fail: create accounts, an ETF, a crypto (with `sourceId`), and a manual fund; add buys, a partial sell, and a split; set the manual price; Refresh with stubbed prices; assert dashboard totals, per-holding gain/loss, allocation, and exactly one history point; Refresh again → still one point. Playwright starts DB, API (`local,e2e`), and web through a `webServer` config.
- **Verify:** `npx playwright test golden-path`.

### M6-T2: E2E failure, import, and offline paths (V-1, V-5, NFR-REL-1)
- **Files:** `web/e2e/{refresh-failure,import,offline}.spec.ts`.
- **Steps:** tests: stubbed provider failure → warning banner, stale badges, dashboard intact, last prices retained; import sample CSV → preview → commit → holdings match; with all providers unreachable (simulated by the stub) everything except price fetch still works and manual-priced instruments still value. Then do a **real offline check by hand** (Wi-Fi off, real providers): Refresh reports `FAILED`/`PARTIAL`, no error page, other screens usable. Record the result in the acceptance doc.
- **Verify:** `npx playwright test`.

### M6-T3: Demo data script (infra)
- **Files:** `scripts/seed-demo.sh`, `scripts/demo/{transactions.csv,instruments.json}`.
- **Steps:** off by default; the script calls the API (creating instruments and accounts, importing the demo CSV, setting manual prices and targets) so the UI can be judged with realistic data. It refuses to run if the database already has real transactions unless `--force` is passed. `reset-db.sh` clears it.
- **Verify:** run on an empty database and inspect the UI; run again and confirm the refusal.

### M6-T4: Responsive and accessibility pass (P-1)
- **Files:** touched components as needed; `web/e2e/a11y.spec.ts` (axe via `@axe-core/playwright`, dev dependency).
- **Steps:** tests: axe finds no serious/critical violations on Dashboard, Holdings, History, Allocation, Transactions, Import, Settings in both themes; keyboard-only flow can add a transaction; layouts at 400 px have no page-level horizontal scroll (tables scroll inside their containers). Fix what fails.
- **Verify:** `npx playwright test a11y`; manual look at 400 px, 768 px, and 1280 px.

### M6-T5: Privacy and network audit (NFR-PRIV-1, NFR-SEC-2)
- **Files:** `web/e2e/network-audit.spec.ts`, test in `api` for the loopback guard.
- **Steps:** tests: while browsing every page, the browser makes requests only to its own origin (fail on any third-party host); the API refuses to start under the `local` profile bound to `0.0.0.0` (already unit-tested in M0-T2, now checked end to end); no analytics or CDN references in the built output (grep of `.next`).
- **Verify:** `npx playwright test network-audit`; `grep -r "googletagmanager\|analytics" web/.next` finds nothing.

### M6-T6: README with local run and test instructions (infra; supports V-7, P-4)
- **Files:** `README.md` (replaces the M0-T1 stub); `scripts/check-readme-links.sh`.
- **Content (all sections required):**
  1. **Title and one-paragraph summary:** what the app is (personal, single-user portfolio tracker for stocks/ETFs, funds, crypto), its status (Phase 1 local; cloud not yet deployed), and a screenshot of the dashboard and holdings (captured during M6-T4; stored in `docs/images/`, with alt text).
  2. **Features (v1)** in one short list, taken from `intent.md` §3–§4, plus a **Not in v1** list (`intent.md` §5: no trading, no scheduled refresh, no fees, no snapshot backfill, no FX).
  3. **Tech stack and architecture:** the stack line from `spec.md` §2, the request path (browser → Next.js `/api/*` proxy → Spring Boot API → Postgres), and a link to `docs/spec.md` for detail.
  4. **Prerequisites** with versions and how to check each: JDK 21 (`java -version`; on this Mac `JAVA_HOME=/opt/homebrew/opt/openjdk@21`, `/usr/bin/java` is only a stub), Node 22 LTS via nvm (`nvm use` reads `.nvmrc`), Docker Desktop running (`docker info`), Git. State that Gradle is provided by the wrapper (`./gradlew`) and that the internet is needed only for real price refresh.
  5. **Quick start (local):** `git clone`, `source scripts/env.sh`, `scripts/dev.sh`, open `http://127.0.0.1:3000`; expected output at each step (containers healthy, API "Started ApiApplication", web ready); how to stop (Ctrl-C) and what it leaves running (the Postgres container: `docker compose down` to stop it).
  6. **Running the pieces manually:** the same thing step by step (`docker compose up -d db`; `cd api && ./gradlew bootRun --args='--spring.profiles.active=local'`; `cd web && npm ci && npm run dev`), with ports (Postgres 5432, API 8080, web 3000), all bound to `127.0.0.1`.
  7. **Configuration:** table of environment variables for local (`API_BASE_URL`, `APP_TIMEZONE`, `COINGECKO_API_KEY` optional, DB settings), their defaults, and which are cloud-only (`PROXY_SECRET`, `BOOTSTRAP_SECRET`, `WEBAUTHN_*`, `DATABASE_URL`, marked "Phase 2"). Never commit real values; `.env.example` lists names only.
  8. **Using the app (first 10 minutes):** create an account; add an instrument (ticker, crypto with CoinGecko id, manual-priced fund); add transactions (buy, sell, split, dividend reinvestment); set a manual price; click **Refresh**; read the dashboard, holdings, history, and allocation; set target allocations. Explain that history starts at the first Refresh and days without a Refresh have no point.
  9. **CSV import and export:** the generic schema table from `spec.md` §8 with a 3-row example, a pointer to `web/public/sample-transactions.csv`, the preview → commit flow, and the three exports plus the backup advice (export regularly; restore = re-import the transactions CSV, then re-enter manual prices and targets).
  10. **Demo data (optional):** `scripts/seed-demo.sh` (off by default; refuses on a database with real data unless `--force`) and `scripts/reset-db.sh`.
  11. **Testing:** exact commands and what each covers: `scripts/check.sh` (everything); `cd api && ./gradlew test` (domain unit + Testcontainers integration; needs Docker running); a single class (`./gradlew test --tests '*FifoEngineTest'`); `cd web && npm run test` (Vitest), `npm run lint`, `npm run build`; end-to-end `cd web && npx playwright test` (first run `npx playwright install`); note that no test calls a live price API.
  12. **Project structure:** the tree from `spec.md` §3 with one line per folder, and the `docs/` index (`intent.md` requirements, `spec.md` design, `plan.md` milestones, `standards.md` coding standards). List only files that exist at this point; M6-T7 adds `acceptance-local.md` and M8 adds `deploy-cloud.md` and `acceptance-cloud.md` to this index as they are created.
  13. **Troubleshooting** (each with symptom → fix): "Unable to locate a Java Runtime" (stub; set `JAVA_HOME`); wrong Node version (`nvm use`); port 5432/8080/3000 already in use; Docker not running / DB unhealthy; API refuses to start with a non-loopback address (by design under `local`); Refresh shows stale badges or `FAILED` offline or when Yahoo/CoinGecko is down (set a manual price); Flyway checksum mismatch (never edit an applied migration; `scripts/reset-db.sh` for a dev database); Spring Boot 4 note (Jackson 3 `tools.jackson`, starter names differ from Boot 3 examples).
  14. **Cloud deployment:** one paragraph saying cloud deployment (Vercel + Render, passkey login) is planned as Phase 2 and the runbook will live in `docs/deploy-cloud.md`. **Do not link that file yet** (it does not exist until M8-T2, and the link checker would fail); M8-T2 adds the link. State that auth is not enabled locally and the local profile must never be exposed beyond localhost.
  15. **Contributing / standards:** link to `docs/standards.md`, the one-commit-per-task convention, and the review checklist.
  16. **License / ownership:** a single line stating this is a private personal project with no license granted (decide before publishing).
- **Steps:** (1) Write `scripts/check-readme-links.sh` first: it extracts relative links and referenced paths (scripts, docs, sample CSV, images) from `README.md` and fails if any target is missing; run it and watch it fail on the old stub. (2) Write the README to the outline above, copying commands from the scripts themselves rather than retyping them from memory. (3) Add `.env.example` (variable names only) if it does not exist. (4) **Dry run from a clean clone:** `git clone . /tmp/pm-readme-test` (scratch location), follow the README literally on a shell with no leftover environment (new terminal), and fix every step that fails or is ambiguous; note the date in the commit message.
- **Verify:** `scripts/check-readme-links.sh` exits 0; the clean-clone dry run reaches a working dashboard using only README instructions, and `scripts/check.sh` passes when run as the README says.

### M6-T7: Acceptance run and Phase 1 gate (V-1 … V-7)
- **Files:** `docs/acceptance-local.md`.
- **Steps:** run `scripts/check.sh` and the full Playwright suite. Then, by hand, walk every item V-1..V-7, including **V-1 with a real broker CSV reshaped to the schema** (compare totals to the broker statement). Write date, method, and pass/fail per item, plus known gaps. Update `intent.md` §8/§9 if any behavior changed. Ask you to sign off.
- **README:** add `docs/acceptance-local.md` to the README's docs index and re-run `scripts/check-readme-links.sh`.
- **Verify:** the document exists with all seven items resolved. **Do not start M7 until you approve.**

---

## M7. Cloud auth (Phase 2)

**Goal:** the API can be exposed safely: only proxied traffic, only an authenticated single user.
**Requirements:** NFR-SEC-1, NFR-SEC-3, P-5, V-8, V-9. **Spec:** §11.
**Exit criteria:** under the `cloud` profile locally (with a local HTTPS-capable hostname or `localhost`, which browsers treat as a secure context for WebAuthn): requests without `X-Proxy-Secret` get `403`; unauthenticated calls get `401`; passkey enrollment (with bootstrap secret), logout, and login all work through the Next.js proxy.

### M7-T1: Cloud profile and proxy-secret filter (NFR-SEC-1, P-5, V-8)
- **Files:** `application-cloud.yml`, `auth/ProxySecretFilter.java`, `ProxySecretFilterTest.java`, `CloudProfileIT.java`; update `web/lib/proxy.ts` (already attaches the header when `PROXY_SECRET` is set).
- **Steps:** tests: request without header → `403`; wrong value → `403`; correct value passes; comparison is constant-time (`MessageDigest.isEqual`); `/actuator/health/liveness` is exempt and leaks nothing; CORS is disabled; the filter is first in the chain; startup fails under `cloud` if `PROXY_SECRET` is empty. Implement.
- **Verify:** `./gradlew test --tests '*ProxySecretFilterTest' --tests '*CloudProfileIT'`.

### M7-T2: Sessions, security chain, CSRF (NFR-SEC-1)
- **Files:** `db/migration/V2__auth.sql` (`app_user`, `passkey_credential`, Spring Session JDBC tables), `auth/SecurityConfig.java`, `SecurityIT.java`.
- **Steps:** tests: all non-auth endpoints return `401` without a session; auth endpoints reachable pre-login; the session cookie is `HttpOnly; Secure; SameSite=Lax` with a 14-day idle timeout; mutating requests without the CSRF token → `403`, with it pass; under the `local` profile nothing changes (still open). Implement with Spring Security and Spring Session JDBC.
- **Verify:** `./gradlew test --tests '*SecurityIT'`, and the full suite still passes under `local`.

### M7-T3: WebAuthn registration and login (NFR-SEC-1, NFR-SEC-3, V-9)
- **Files:** `auth/{WebAuthnService,CredentialRepositoryImpl,AuthController}.java`, `WebAuthnServiceTest.java`, `AuthApiIT.java`.
- **Steps:** tests using Yubico's test authenticator or recorded ceremonies: registration options and verify persist a credential; login options and verify create a session; sign-count regression is rejected; RP ID and origin come from `WEBAUTHN_RP_ID` and `WEBAUTHN_ORIGIN`, never from request headers; user verification is required; `GET /api/auth/status` reports `setupRequired` and `authenticated`; `POST /api/auth/logout` invalidates the session. Implement using `webauthn-server-core`.
- **Verify:** `./gradlew test --tests '*WebAuthnServiceTest' --tests '*AuthApiIT'`.

### M7-T4: Bootstrap-secret enrollment and recovery (NFR-SEC-1)
- **Files:** `auth/{EnrollmentGuard,AttemptLimiter}.java`, tests.
- **Steps:** tests: with no credentials, registration requires the correct `BOOTSTRAP_SECRET`; with existing credentials, registration requires either an authenticated session **or** the bootstrap secret (recovery); wrong secret is rejected in constant time; more than 5 failed attempts in 15 minutes locks further attempts (`429`) until the window passes; the secret never appears in logs or responses. Implement.
- **Verify:** `./gradlew test --tests '*EnrollmentGuardTest' --tests '*AttemptLimiterTest'`.

### M7-T5: Passkey management endpoints (NFR-SEC-1)
- **Files:** `auth/PasskeyController.java`, `PasskeyApiIT.java`.
- **Steps:** tests: `GET /api/auth/passkeys` lists labels and dates (no key material); `DELETE /api/auth/passkeys/{id}` removes one; deleting the **last** passkey is refused (`409`) so you cannot lock yourself out; adding another passkey while logged in works. Implement.
- **Verify:** `./gradlew test --tests '*PasskeyApiIT'`.

### M7-T6: Login UI and route guard (V-8, V-9)
- **Files:** `web/app/login/page.tsx`, `web/middleware.ts`, `web/components/{PasskeyLogin,PasskeyEnroll,PasskeyList}.tsx`, `web/lib/webauthn.ts`, tests.
- **Steps:** tests: unauthenticated visit redirects to `/login`; `setupRequired` shows the enrollment form asking for the bootstrap secret; the browser-side ceremony helper encodes and decodes buffers correctly (unit-tested with fixtures); an API `401` sends the user to `/login`; Settings shows passkeys with add/remove and the last-passkey guard message. Middleware only checks cookie presence; the API remains the authority. Load the CSRF token and echo it in `X-XSRF-TOKEN`.
- **Verify:** `npm run test`; by hand under the `cloud` profile: enroll, log out, log in, and confirm protected pages redirect when logged out.

### M7-T7: Cloud-profile end-to-end (V-8, V-9)
- **Files:** `web/e2e/auth.spec.ts` (Playwright virtual authenticator via CDP), `api` cloud test suite.
- **Steps:** tests: unauthenticated navigation redirects; enrollment through the proxy with the bootstrap secret succeeds with a virtual authenticator; logout then login succeeds; direct API call without the proxy header → `403`; recovery path (enroll a second passkey with the secret while logged out) works.
- **Verify:** `npx playwright test auth` against a stack started with the `cloud` profile locally.

**M7 milestone check:** `scripts/check.sh` plus the auth e2e; the `local` profile behavior remains unchanged.

---

## M8. Cloud deployment

**Goal:** the same code running on Vercel and Render, verified against V-8 and V-9, with a backup routine.
**Requirements:** P-3, P-5, NFR-COST-1, NFR-SEC-1, V-8, V-9. **Spec:** §11, §12.
**Exit criteria:** the deployed app requires passkey login, the Render URL rejects direct calls, refresh works on the free tier, and a data export has been taken and re-imported successfully into a scratch database.

### M8-T1: API container and JDBC URL shim (P-3, infra)
- **Files:** `api/Dockerfile` (multi-stage: Gradle build → Temurin 21 JRE, layered jar), `api/.dockerignore`, `config/DatabaseUrlConfig.java`, test.
- **Steps:** test first: a `postgres://user:pass@host:5432/db` URL converts to `jdbc:postgresql://host:5432/db` with credentials split out; `DATABASE_URL` absent leaves the local config alone. Set `-XX:MaxRAMPercentage=70` and lazy initialisation to fit small instances; health check on `/actuator/health/liveness`; runs as non-root.
- **Verify:** `docker build -t portfolio-api api` and run it against the local Postgres with `SPRING_PROFILES_ACTIVE=cloud` and dummy secrets; it starts and migrates.

### M8-T2: Render provisioning (P-3, NFR-COST-1)
- **Files:** `docs/deploy-cloud.md` (runbook), optional `render.yaml`.
- **Steps:** create the Render Postgres (same region) and Docker web service; set `SPRING_PROFILES_ACTIVE=cloud`, `DATABASE_URL`, `PROXY_SECRET`, `BOOTSTRAP_SECRET`, `WEBAUTHN_RP_ID`, `WEBAUTHN_ORIGIN`, `COINGECKO_API_KEY`, `APP_TIMEZONE=UTC`; health check path `/actuator/health/liveness`. **Check Render's current free-database lifetime and encryption-at-rest statement before storing real data** and record the findings (intent §8, spec §11). Update the README: link `docs/deploy-cloud.md` from its "Cloud deployment" section and add it to the docs index, then re-run `scripts/check-readme-links.sh`.
- **Verify:** service healthy; `curl` to the public Render URL without the secret returns `403`; the README link check passes.

### M8-T3: Vercel project (P-5)
- **Files:** `docs/deploy-cloud.md` (extend).
- **Steps:** project rooted at `web/`; env `API_BASE_URL` (Render URL) and `PROXY_SECRET`; confirm the route handler proxies with cookies intact; set `WEBAUTHN_ORIGIN`/`RP_ID` on the API to the final `*.vercel.app` hostname (changing the hostname later means re-enrolling via the bootstrap secret; document this).
- **Verify:** the Vercel URL loads and redirects to `/login`.

### M8-T4: Cold-start experience (NFR-REL-1, P-1)
- **Files:** `web/components/WakeUpNotice.tsx`, test.
- **Steps:** tests: after 3 s of pending API requests show "waking up the server…", hide it on success; on failure after 60 s show a retry action. Confirm refresh's async design (M3) avoids Vercel function time limits, and that CSV upload stays under the ~4.5 MB body limit (2 MB cap already enforced; add a clear client message for `413`).
- **Verify:** `npm run test`; on the deployed free tier after idle time, confirm the notice appears and the app recovers.

### M8-T5: Deployed smoke test (V-8, V-9, V-5)
- **Files:** `docs/acceptance-cloud.md`.
- **Steps:** on the deployed pair: enroll a passkey with the bootstrap secret; log out and in; direct call to the Render API → `403`; unauthenticated Vercel page → `/login`; add a transaction, Refresh with real feeds, confirm one snapshot row for today, refresh again and confirm still one; simulate a Yahoo failure by setting a bad ticker and confirm stale indication. Record dated results per item.
- **Verify:** the acceptance document has V-8 and V-9 passing.

### M8-T6: Backup runbook (F-DATA-5, NFR-COST-1)
- **Files:** `docs/deploy-cloud.md` (backup section).
- **Steps:** document the export routine (Settings → Export all three files, store them offline); rehearse a restore: import `transactions.csv` into a fresh local database, then re-enter manual prices and targets, and note that snapshot history is not restorable from CSV in v1; record the outcome and put a reminder date based on the free-database lifetime found in M8-T2.
- **README:** change the README status line from "Phase 1 local" to reflect the deployed state, add `docs/acceptance-cloud.md` to the docs index, and re-run `scripts/check-readme-links.sh`.
- **Verify:** restore rehearsal produces identical holdings to the cloud instance.

---

## 5. Milestone exit checklist (copy for each milestone)

- [ ] All tasks committed, one commit per task, messages prefixed with the task ID.
- [ ] `scripts/check.sh` exits 0 (API and web suites, lint, build).
- [ ] Manual exit-criteria demo performed.
- [ ] Traceability rows for the milestone satisfied (§6).
- [ ] `spec.md` / `intent.md` updated for any deviation.

## 6. Requirements traceability matrix

Every requirement maps to at least one task and at least one test. Tests are listed by class or spec file.

| Requirement | Tasks | Tests |
|---|---|---|
| G-1 | M2-T3, M2-T4, M2-T5, M2-T11, M2-T12 | `AccountApiIT`, `InstrumentApiIT`, `TransactionApiIT`, golden path |
| G-2 | M1-T2, M2-T6, M2-T7, M2-T13 | `ValuationTest`, `HoldingsApiIT`, `DashboardApiIT` |
| G-3 | M3-T5, M3-T6, M3-T8 | `SnapshotServiceIT`, `SnapshotApiIT`, `ValueChart.test.tsx`, golden path |
| G-4 | M3-T4, M3-T5, M3-T6, M3-T7 | `RefreshServiceIT`, `RefreshApiIT`, refresh UI tests |
| G-5 | M1-T3, M2-T8, M5-T1, M5-T2 | `AllocationTest`, `TargetAllocationApiIT`, `AllocationApiIT`, `TargetEditor` test |
| F-DATA-1 | M2-T3, M2-T4, M2-T5, M2-T11, M2-T12 | `AccountApiIT`, `InstrumentApiIT`, `TransactionApiIT`, form tests |
| F-DATA-2 | M2-T5, M2-T12 | `TransactionApiIT` (split fields, no cash-dividend type), form tests |
| F-DATA-3 | M4-T1, M4-T2, M4-T3, M4-T5 | `CsvParserTest`, `ImportValidatorTest`, `ImportApiIT`, `ImportPreview` test, import e2e |
| F-DATA-4 | M2-T4, M2-T11, M3-T3 | `InstrumentApiIT`, `PriceServiceIT`, manual-price form test |
| F-DATA-5 | M4-T4, M4-T6, M8-T6 | `ExportApiIT` (round trip), `ExportPanel` test, restore rehearsal |
| F-COST-1 | M1-T1, M2-T5, M4-T2 | `FifoEngineTest`, `TransactionApiIT`, `ImportValidatorTest` |
| F-COST-2 | M1-T1, M1-T2, M2-T6 | `FifoEngineTest`, `HoldingsApiIT` |
| F-PRICE-1 | M3-T4, M3-T5, M3-T6 | `SnapshotServiceIT` (twice = one row), `RefreshApiIT`, golden path |
| F-PRICE-2 | M3-T1, M3-T2 | `YahooProviderTest`, `CoinGeckoProviderTest` |
| F-PRICE-3 | M1-T4, M3-T3, M3-T7, M2-T13 | `StalenessPolicyTest`, `PriceServiceIT`, `HoldingsTable` test, failure e2e |
| F-VIEW-1 | M2-T7, M2-T13 | `DashboardApiIT`, dashboard tests |
| F-VIEW-2 | M2-T6, M2-T13 | `HoldingsApiIT`, `HoldingsTable` test |
| F-VIEW-3 | M3-T6, M3-T8 | `SnapshotApiIT`, `ValueChart.test.tsx` |
| F-VIEW-4 | M1-T3, M5-T1, M5-T2 | `AllocationTest`, `AllocationApiIT`, `DriftTable` test |
| F-CUR-1 | M2-T10 | `format.test.ts` (USD only, no FX code paths) |
| P-1 | M2-T9, M6-T4 | a11y and responsive e2e |
| P-2 | M7-T2, M7-T5 | `SecurityIT` (single user), `PasskeyApiIT` |
| P-3 | M0 through M8 phasing, M8-T1..T3 | milestone gates; M6-T7 sign-off before M7 |
| P-4 | M0-T2, M0-T4 | build and `scripts/check.sh` |
| P-5 | M0-T5, M7-T1, M8-T3 | proxy tests, `ProxySecretFilterTest`, deployed smoke test |
| NFR-SEC-1 | M7-T1..T5, M8-T2 | `SecurityIT`, `WebAuthnServiceTest`, `EnrollmentGuardTest`, encryption-at-rest check recorded |
| NFR-SEC-2 | M0-T2, M6-T5 | `LoopbackGuardTest`, network audit |
| NFR-SEC-3 | M7-T3, M7-T6, M7-T7 | `AuthApiIT`, auth e2e through the proxy |
| NFR-REL-1 | M3-T4, M3-T5, M3-T7, M6-T2, M8-T4 | `RefreshServiceIT` (single-flight, partial), failure and offline e2e |
| NFR-NUM-1 | M0-T3, M1-T1, M2-T2, M2-T10 | `MigrationIT`, `FifoEngineTest`, decimal-string API tests, `format.test.ts` |
| NFR-COST-1 | M8-T2 | Free-tier configuration recorded in the runbook |
| NFR-PRIV-1 | M0-T4, M2-T9, M6-T5 | network audit, build grep |
| V-1 | M4-T2, M4-T3, M6-T2, M6-T7 | import e2e; manual real-CSV acceptance |
| V-2 | M2-T6, M2-T13, M6-T1 | `HoldingsApiIT`, golden path |
| V-3 | M1-T1, M2-T5, M2-T12 | `FifoEngineTest` (§5.1 example), `TransactionApiIT`, golden path |
| V-4 | M5-T1, M5-T2, M6-T1 | `AllocationApiIT`, golden path |
| V-5 | M3-T3..T7, M6-T2 | `SnapshotServiceIT`, `RefreshServiceIT`, failure e2e |
| V-6 | M3-T8, M6-T1 | `ValueChart.test.tsx`, golden path |
| V-7 | M6-T1, M6-T2, M6-T6, M6-T7 | whole Phase 1 suite; README clean-clone dry run and link check; `docs/acceptance-local.md` |
| V-8 | M7-T1, M7-T7, M8-T5 | `ProxySecretFilterTest`, auth e2e, deployed smoke test |
| V-9 | M7-T3, M7-T7, M8-T5 | `AuthApiIT`, auth e2e, deployed smoke test |
| NG-1 … NG-12 | none by design | Reviewed at each milestone exit: no code for excluded items (no scheduler, no backfill, no fee column, no fallback feed, no cash-dividend type, no rebalancing suggestions) |

## 7. Risks and mitigations

| Risk | Affects | Mitigation |
|---|---|---|
| Spring Boot 4.x differences (Jackson 3, split starters, test starters) | M0 onward | Pin 4.1.1, verify each dependency as it is added, keep a short "Boot 4 notes" section in the README, and prefer plain Spring APIs over niche ones. |
| Node 25 on the machine vs pinned Node 22 | M0, M6 | `scripts/env.sh` runs `nvm use`; CI-less `check.sh` refuses to run on other majors. |
| Yahoo's unofficial API changes or blocks | M3, M6, M8 | Isolated behind `PriceProvider`; fixtures make breakage visible; manual price is the escape hatch (no second feed by decision). |
| Mutual-fund NAVs not on Yahoo | M3 | `MANUAL` price source with a 45-day staleness warning. |
| Money-math mistakes silently corrupt figures | M1, M2, M4 | Domain isolated and unit-tested first; decimals never leave `BigDecimal` or string form; worked examples are test cases. |
| Snapshot gaps because refresh is manual | M3, M6 | Accepted by the on-demand decision; chart shows only real points and explains this in the empty state. |
| Passkeys through a proxy across two platforms | M7, M8 | RP ID/origin pinned by env; test locally in M7-T7 before any deployment; re-enrolment path exists via bootstrap secret. |
| Render free tier (idle spin-down, time-limited free Postgres) | M8 | Cold-start UI state (M8-T4); regular exports; check current limits in M8-T2 and set a reminder. |
| Vercel request-body limit (~4.5 MB) | M4, M8 | CSV capped at 2 MB with a clear client message. |
| Scope creep (fees, fallback feeds, backfill, schedulers) | all | Non-goal IDs `NG-*` reviewed at each milestone exit. |

## 8. Open questions carried over

From `intent.md` §8 and `spec.md` §16:
- **Snapshot restore:** should the JSON export be importable for a full restore including snapshots? Currently not in v1; if yes, add a task to M4 (import of `all.json`) and revisit M8-T6.
- **Render free Postgres lifetime and encryption at rest:** to be confirmed in M8-T2 before real data is stored.

Decisions needed during the build (record answers in `spec.md` when made):
- **M0-T2:** the exact URL path of the health endpoint through the proxy (whether `/api/actuator/...` is exposed, or health is only hit directly on the API).

Resolved: CSV parsing uses Apache Commons CSV (M4-T1; recorded in `spec.md` §15).
