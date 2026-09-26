# Portfolio Manager: Coding Standards (v0.1)

Standards for the stack chosen in [`spec.md`](./spec.md): Java 21 + Spring Boot (Gradle Kotlin DSL) + Spring Data JPA + Flyway + Postgres on the API side; Next.js App Router + TypeScript + Tailwind + Recharts on the web side. Work order is in [`plan.md`](./plan.md).

**Rules of thumb:** correctness of money math beats cleverness; the domain core stays pure; every rule below has a reason, and a deviation needs a note in the PR/commit message or in `spec.md`.

Keywords: **MUST** and **MUST NOT** are enforced in review; **SHOULD** is the default unless there is a stated reason.

## 1. Universal rules

1. **Test first.** Write the failing test, watch it fail for the right reason, implement, watch it pass (TDD, as in `plan.md` §1). Bug fixes start with a test that reproduces the bug.
2. **One task, one commit**, on `main`, message prefixed with the task ID: `M2-T5: transactions API with replay validation`. Imperative mood, subject ≤ 72 chars, body explains *why* when not obvious.
3. **No dead code, no commented-out code, no `TODO` without a task ID** (`TODO(M4-T1): ...`).
4. **Names say what things are.** No abbreviations except well-known ones (`id`, `url`); write `quantity`, not `qty`. Domain terms follow `intent.md`/`spec.md`: *position*, *lot*, *snapshot*, *instrument*, *refresh run*.
5. **Small units.** A function does one thing and fits on a screen; a file has one responsibility. If a file passes ~300 lines, split it.
6. **Scope guard.** Do not implement anything listed as a non-goal (`plan.md` §3, `NG-*`).
7. **Comments explain why, not what.** Public domain types and non-obvious algorithms get a short doc comment with a worked example when helpful (see `FifoEngine`).
8. **No secrets in the repo.** Env vars only; `.env*` is git-ignored (only `.env.example` is committed).
9. **No third-party analytics, trackers, or CDN assets** (`intent.md` NFR-PRIV-1). Fonts are self-hosted through `next/font`.

## 2. Money, numbers, dates (both stacks)

These rules exist because a rounding or precision mistake silently corrupts financial figures (`intent.md` NFR-NUM-1).

| Rule | Detail |
|---|---|
| **No floating point for money or quantities** | Java: `BigDecimal` only. Postgres: `NUMERIC`. TypeScript: money/quantity values stay **strings**; never `parseFloat`, `Number(...)`, or `+value` on them. |
| **Column precision** | Quantities and unit prices `NUMERIC(24,8)`; monetary totals `NUMERIC(20,4)`; target percentages `NUMERIC(5,2)` (spec §4); computed percentages are scale 4 in code and are not stored. |
| **Rounding** | `RoundingMode.HALF_UP`. Intermediate arithmetic at scale 8 (or `MathContext(28)` for divisions), round **once** at the end to the output scale. Never round per-lot then sum. |
| **Comparison** | `compareTo`, never `equals` (scale-sensitive). Tests use `isEqualByComparingTo`. |
| **Construction** | `new BigDecimal("12.50")` or `BigDecimal.valueOf(long)`; **never** `new BigDecimal(double)`. |
| **JSON** | Decimals are serialised as strings (`"12.50000000"`) and parsed from strings. Dates are ISO-8601 (`2026-09-26`); timestamps are UTC ISO-8601 with `Z`. |
| **Division** | Always pass a scale and rounding mode; guard against zero divisors (empty result, not an exception, when the domain says "not defined"). |
| **Dates and time** | Java: `java.time` only (`LocalDate`, `Instant`); inject a `Clock` instead of calling `now()`. Snapshot dates use `app.timezone` (default UTC). No `java.util.Date`/`Calendar`. |
| **Currency** | USD implied. No currency columns, no FX code paths (`F-CUR-1`). |

## 3. Java and Spring Boot (`/api`)

### 3.1 Language and style
- **Java 21.** Use records for immutable data (DTOs, value types like `Lot`, `Txn`), `sealed`/pattern matching where it removes casts, and `Optional` for "maybe absent" **return values** only (never fields, parameters, or collections).
- **Formatting is automated** (Spotless with google-java-format, AOSP style: 4-space indent). `./gradlew spotlessApply` before committing; `spotlessCheck` runs in `scripts/check.sh`. No manual formatting debates.
- **Imports:** no wildcards, no unused imports. Static imports allowed only for test assertions and constants.
- **Nullability:** prefer non-null everywhere; validate at boundaries (`Objects.requireNonNull` in constructors of domain types). Return empty collections, never `null`.
- **Immutability:** fields `final` by default; domain types are immutable; collections returned from domain code are unmodifiable copies.
- **Exceptions:** use unchecked exceptions with domain meaning (`NotFoundException`, `ConflictException`, `OversellException`). No catching `Exception`/`Throwable` except at a documented boundary (the refresh worker, the exception handler). Never swallow: log with context or rethrow.
- **Logging:** SLF4J. Parameterised messages (`log.info("refresh {} finished: {}", runId, status)`), no string concatenation. No request bodies, no secrets, no full financial figures in logs at INFO. Levels: `ERROR` = needs attention, `WARN` = degraded but handled (a feed failure), `INFO` = lifecycle events, `DEBUG` = diagnostics.
- **Concurrency:** virtual threads for I/O fan-out (price fetching) with an explicit concurrency cap. No shared mutable state in singleton beans; the database (unique index) is the single-flight guard for refresh.
- **Boot 4 / Jackson 3:** use `tools.jackson.*` packages and the Boot 4 starter names. When a Boot 3 sample or answer differs, follow the Boot 4 documentation.

### 3.2 Package structure and dependency rule
```
com.portfoliomanager
├── domain       pure logic and value types, imports NOTHING from Spring/JPA/Jackson
├── persistence  JPA entities + Spring Data repositories
├── application  use-case services (transactions, orchestration)
├── pricing      PriceProvider + adapters, StalenessPolicy
├── importing    CSV parsing, validation, staging
├── auth         WebAuthn, session, proxy-secret filter (Phase 2)
└── web          controllers, request/response DTOs, exception handling
```
- Allowed direction: `web → application → domain`; `application → persistence | pricing | importing`. `domain` MUST NOT depend on any other package; `persistence` MUST NOT depend on `web`.
- Enforce with an ArchUnit test in `api/src/test` (added in M2) so the rule cannot drift.
- Business rules live in `domain` or `application`, never in controllers or entities.

### 3.3 Spring conventions
- **Constructor injection only** (single constructor, no `@Autowired` on fields). Beans are stateless.
- **Controllers** are thin: parse/validate the request, call one service method, map to a DTO. No business logic, no repository access.
- **DTOs are separate from entities.** Never return a JPA entity from a controller; never accept one.
- **Validation:** Bean Validation annotations on request DTOs plus explicit domain validation in services. Cross-field rules (a SPLIT forbids quantity) live in the service/domain, and are also enforced by database check constraints.
- **Configuration:** typed `@ConfigurationProperties` records for app settings (`app.timezone`, `app.pricing.*`), not scattered `@Value`. Profiles: `local` (open, loopback only), `cloud` (secured), `e2e` (stub providers). Profile-specific behaviour is isolated in config classes, never `if (profile)` in business code.
- **Transactions:** `@Transactional` on service methods (not controllers/repositories). Read-only queries use `readOnly = true`. Anything that mutates a position replays it through `FifoEngine` **inside the same transaction** and rolls back on violation.
- **Time:** inject `Clock`; tests use a fixed clock.

### 3.4 REST API conventions
- Base path `/api`, plural nouns, lowercase kebab-case paths (`/api/transactions`, `/api/refresh/latest`).
- Verbs: `GET` read, `POST` create/action, `PUT` replace, `DELETE` remove. Status codes: `200`, `201` (created), `202` (accepted, async refresh), `204`, `400` (validation), `401`/`403` (auth), `404`, `409` (conflict/single-flight), `413`, `422` (well-formed but violates a domain rule, e.g. oversell), `429`.
- **Errors** are RFC 7807 `application/problem+json` with `errors[{field, message}]` for validation. Messages are human-readable and say what to change. One `@RestControllerAdvice` maps exceptions; controllers never build error bodies by hand.
- JSON property names are `camelCase`. Enums are `UPPER_SNAKE_CASE` strings. Paging uses `page` and `size` query parameters.
- Every endpoint documented in `spec.md` §9 keeps that exact path and shape; changing one means updating the spec first.

### 3.5 Persistence and migrations
- **Flyway owns the schema.** `spring.jpa.hibernate.ddl-auto=validate`; Hibernate never creates or alters tables.
- Migrations are `V<n>__<snake_case_description>.sql`, **append-only**: never edit a migration that has been committed; add a new one.
- Every table has a primary key; every foreign key and every column used in a filter/sort has an index (or a documented reason not to). Constraints (checks, unique, FK) are declared in SQL, not only in Java.
- `TIMESTAMPTZ` for instants, `DATE` for calendar dates, `UUID` primary keys (`gen_random_uuid()`), `bigserial` only where ordering matters (`transaction.seq`).
- Table and column names are `snake_case`; quote reserved words (`"transaction"`) in SQL.
- JPA: entities are plain and minimal; `spring.jpa.open-in-view=false`; associations are `LAZY`; avoid bidirectional relationships unless needed; no business logic in entities; no `CascadeType.ALL` shortcuts. Prefer explicit repository queries (JPQL or derived) over loading graphs; watch for N+1 in holdings/snapshot code.
- Bulk work (snapshot holdings, imports) uses batch writes inside one transaction.

### 3.6 Domain code rules
- `domain` classes are **pure functions over immutable inputs**; same input → same output; no I/O, no `Clock.now()`, no randomness.
- Every rule with a worked example in `spec.md` (§5.1 FIFO example) exists as a test with the same numbers.
- Domain results carry violations as data (`ReplayResult`), not by throwing; the service decides to raise `OversellException`.

## 4. Testing standards

| Layer | Tooling | Standard |
|---|---|---|
| Domain unit | JUnit 5 + AssertJ | Fast (< 1 s total), no Spring context, no mocks. Table-style parameterised tests for numeric edge cases. |
| Persistence / API integration | Spring Boot test + MockMvc + Testcontainers (Postgres 16) | Real Postgres, real Flyway migrations; one shared container per JVM (`AbstractIntegrationTest`). No H2. Tests are independent: each cleans up or uses unique data. |
| Provider adapters | WireMock + recorded fixtures | No live network in any test. Cover success, error payload, timeout, retryable 5xx, 4xx. |
| Frontend unit/component | Vitest + Testing Library | Assert what users see (roles, text), not implementation details. |
| End-to-end | Playwright | Golden path, failure path, import, offline, accessibility (axe), network audit. Stub providers via the `e2e` profile. |

Rules:
- **Test names describe behaviour:** `sellBeyondHoldingsIsAViolation`, `refreshTwiceSameDayLeavesOneSnapshot`. Not `test1`.
- **Arrange–Act–Assert**, one behaviour per test, minimal fixtures via small builders (`buy(seq, date, qty, price)`).
- **Prefer real objects over mocks.** Mock only true external boundaries (HTTP feeds, the `Clock`). Never assert on mock interactions when you can assert on results.
- Assertions on decimals use `isEqualByComparingTo`; assert JSON decimals as strings.
- **Deterministic:** fixed `Clock`, no sleeps (await conditions), no ordering dependence, no dependence on the current date or the network.
- A test that passes before the implementation exists is wrong; fix the test.
- `scripts/check.sh` (API tests, web tests, lint, build) MUST be green before each milestone closes; the touched subproject's full suite MUST be green before every commit.
- Coverage is a signal, not a target: every requirement in the traceability matrix (`plan.md` §6) has at least one test; domain packages should be effectively fully covered.

## 5. TypeScript, Next.js, Tailwind (`/web`)

### 5.1 TypeScript
- `strict: true`, `noUncheckedIndexedAccess: true`. **No `any`** (use `unknown` and narrow); no non-null assertions (`!`) without a comment; no `// @ts-ignore` (use `@ts-expect-error` with a reason).
- Types for API payloads live in `web/lib/types.ts` and mirror `spec.md` §9. Decimal fields are typed as `DecimalString` (a branded `string`), so passing a `number` where money is expected is a compile error.
- Validate untrusted data at boundaries (forms, API responses if shape is uncertain) with **zod**; derive types from schemas (`z.infer`).
- Prefer `type` for unions and object shapes, `interface` only when declaration merging is needed. Named exports; default exports only where Next.js requires them (`page.tsx`, `layout.tsx`, `route.ts` handlers).
- **Formatting and linting:** Prettier (defaults plus `singleQuote: false`, `printWidth: 100`) and ESLint with `next/core-web-vitals` and `@typescript-eslint` strict rules; `npm run lint` and `prettier --check` run in `scripts/check.sh`. Imports use the `@/` alias, ordered: external, then `@/`, then relative.
- Files: `PascalCase.tsx` for components, `camelCase.ts` for modules/hooks (`useRefresh.ts`), `kebab-case` only for route segment folders, tests next to the code as `*.test.ts(x)`.

### 5.2 Next.js App Router
- **Server components by default;** add `"use client"` only for interactivity (forms, charts, tables with state). Keep client components small and push data fetching to hooks.
- **Data fetching:** client components use the SWR wrapper `useApi` against `/api/*` so local and cloud behave identically. Mutations call the API client, then `mutate()` the affected keys. No data fetching inside `useEffect` by hand.
- **All API traffic goes through the proxy route handler** (`app/api/[...path]/route.ts`). Never call the Spring API's address directly from browser code; never expose `API_BASE_URL`, `PROXY_SECRET`, or any secret to the client (no `NEXT_PUBLIC_` for secrets).
- **Loading, empty, and error states are required** for every data view (skeleton, empty state with a next action, readable error). A failed price feed must degrade to a stale badge, never a broken page.
- **Auth guard (Phase 2):** middleware only redirects on cookie presence; the API is the authority.
- No `window.alert/confirm/prompt`: confirmations are in-page dialogs.
- Persisted UI preferences (`localStorage`) are always wrapped in `try/catch` and the page must render correctly without them.

### 5.3 Components, forms, tables
- Components are small, typed, and accessible: real `<button>`/`<a>`/`<label>` elements, associated labels and error text (`aria-describedby`), visible focus, keyboard-operable dialogs (focus trap, `Esc` closes), `prefers-reduced-motion` respected.
- Forms: react-hook-form + zod resolver; validation messages are specific; server `4xx/422` problem details are shown next to the relevant field or form.
- Tables: TanStack Table; sticky header; numeric columns right-aligned with tabular numerals; long tables scroll inside their own container.
- **Money display:** only through the shared formatters (`formatMoney`, `formatPct`, `formatQty`) that operate on decimal strings. No arithmetic on money in the browser. Gains/losses show sign **and** arrow/text, never colour alone.
- Charts (Recharts): accessible labels or a data-table alternative; no interpolation across missing snapshot days; tooltips show exact values from the API.

### 5.4 Styling (Tailwind)
- Design tokens (colours, spacing, radius, type scale) are CSS variables defined once in `globals.css` with light and dark themes; components use semantic utilities (`bg-surface`, `text-muted`) mapped to those tokens rather than hard-coded hex values.
- Mobile-first responsive classes; layouts verified at 400 px, 768 px, and 1280 px. No page-level horizontal scroll.
- Prefer composing utility classes in a component over one-off CSS; extract a component (not an `@apply` blob) when a pattern repeats three times.
- Contrast: text and gain/loss colours meet WCAG AA in both themes.

## 6. Security standards
- **Local profile:** bind to `127.0.0.1` only; startup fails otherwise (`LoopbackGuard`).
- **Cloud profile:** every request needs `X-Proxy-Secret` (constant-time compare) except the liveness probe; every non-auth endpoint needs a session; CSRF token on mutations; cookies `HttpOnly; Secure; SameSite=Lax`.
- **Never trust the client:** validate every input server-side; parameterised queries only (JPA/JPQL parameters, never string-built SQL).
- **Secrets** come from environment variables, are never logged, never returned in responses, and are compared in constant time. Bootstrap-secret attempts are rate limited.
- **CSV import** is untrusted input: enforce the 2 MB / 5,000-row limits, parse defensively, escape output, and never evaluate content. Guard against CSV formula injection in the **export** by prefixing cells that start with `=`, `+`, `-`, or `@` in user-provided text fields (note, names).
- **Dependencies:** add only what is needed; pin versions (Gradle version catalog / `package-lock.json` committed); review new dependencies for maintenance and licence; run `npm audit` and review outdated/vulnerable Gradle dependencies before each milestone closes, and note anything deferred.
- No broker credentials are ever stored or requested (`intent.md` NFR-SEC-1).

## 7. Error handling and resilience
- External feeds (Yahoo, CoinGecko) are unreliable by nature: every call has explicit timeouts (5 s connect, 10 s read), a bounded retry policy (one retry on network error/5xx, none on 4xx), and returns a result object with an error message rather than throwing across the boundary.
- A failure for one instrument MUST NOT fail the whole refresh; the last good price is preserved and flagged stale.
- Refresh and snapshot writes are **idempotent**; repeated calls the same day converge on the same rows.
- User-facing errors are actionable ("Sell of 15 exceeds the 10 held on 2026-03-05"), never stack traces or internal ids.
- Unexpected exceptions return a generic `500` problem body and are logged once with a correlation id (no double logging up the stack).

## 8. Documentation and change process
- `intent.md` (what) and `spec.md` (how) are the sources of truth. If code needs to differ, **update the doc in the same commit** and say why.
- `README.md` stays accurate for setup: prerequisites, commands, environment variables, the Boot 4 note.
- Public endpoints, environment variables, and CSV schema are documented where the spec says (`spec.md` §8, §9, §12).
- Record non-obvious decisions as one line in `spec.md` (decision + reason), not in chat.
- **Review checklist** (self-review before each commit; used by a reviewer at milestone end):
  1. Does a test exist that failed first and now passes?
  2. Any floating-point money, `Number()` on a decimal, or `equals` on `BigDecimal`?
  3. Any business logic in a controller, entity, or component?
  4. Does the layering rule hold (`domain` imports nothing framework-y)?
  5. Are error, empty, loading, and stale states handled?
  6. Any secret, PII-like data, or third-party host introduced?
  7. Does it match the spec's endpoint/table names, and is any deviation documented?
  8. Is anything built that a non-goal (`NG-*`) forbids?

## 9. Tooling summary

| Concern | Tool | Command (from repo root) |
|---|---|---|
| Java format | Spotless (google-java-format, AOSP) | `cd api && ./gradlew spotlessApply` / `spotlessCheck` |
| Java tests | JUnit 5, AssertJ, Testcontainers, WireMock | `cd api && ./gradlew test` |
| Architecture rules | ArchUnit | runs in `./gradlew test` |
| TS format/lint | Prettier, ESLint | `cd web && npm run lint && npx prettier --check .` |
| TS tests | Vitest + Testing Library | `cd web && npm run test` |
| E2E | Playwright (+ axe) | `cd web && npx playwright test` |
| Everything | `scripts/check.sh` | `scripts/check.sh` |
| Toolchain | JDK 21 (`JAVA_HOME=/opt/homebrew/opt/openjdk@21`), Node 22 (`nvm use`) | `source scripts/env.sh` |
