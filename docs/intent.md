# Portfolio Manager: Product Requirements Document (draft v0.2)

## 1. Context
A personal, single-user web app for tracking what I own across stocks/ETFs, mutual funds/retirement accounts, and crypto, and seeing how it is performing. The repo is currently empty (no commits), so this is a greenfield build.

**Success looks like:** I open one page and immediately see my total portfolio value, gain/loss per holding and overall, allocation against my targets, and a value-over-time chart, without logging into several brokers.

## 2. Users & platform
- Single user (me). No multi-user, sharing, or billing.
- Responsive web only.
- **Delivery is phased:**
  1. **Phase 1, local:** a complete offline local deployment (app + Postgres on my machine). All v1 features work here.
  2. **Phase 2, cloud:** hosted deployment, reachable from any device, with authentication and secure storage of financial data. Postgres in the cloud, passkey (WebAuthn) login.
- **Stack:** Spring Boot (Java 21, Gradle) API + Next.js frontend + Postgres. Price refresh is triggered on demand by the user; there is no scheduler.
- **Cloud hosting:** the Next.js frontend goes on Vercel. Vercel has no supported Java runtime, and Spring Boot is a long-running process (connection pool), so the API runs as a container on Render, with Postgres alongside it. Render's free tier spins services down when idle and limits free Postgres lifetime; with on-demand refresh this only costs a cold start on first use.
- **Frontend-to-API:** the browser only talks to Vercel. Next.js proxies `/api/*` to the Render API, so requests are same-origin (no CORS, plain session cookies). The API accepts traffic only from the Next.js proxy (shared secret header), not directly from the internet.

## 3. Goals (v1)
1. Record holdings across asset types: stocks & ETFs, mutual funds / retirement accounts, crypto.
2. Show current market value, cost basis, and unrealized gain/loss (absolute and %) per holding and in total.
3. Keep daily portfolio-value snapshots and show a value-over-time chart.
4. Refresh prices on demand (a manual Refresh action) and record today's snapshot when it runs.
5. Show allocation by asset type as a chart, and compare it with target allocations that I set.

## 4. Functional requirements
**Data entry**
- Add/edit/delete accounts, holdings, and transactions manually. Transaction types: buy, sell, split (ratio), and dividend reinvestment.
- Splits and dividend reinvestments are entered by hand; they are not detected from the price feed. Cash dividends are not tracked in v1.
- CSV import in one documented generic schema (no column-mapping UI; I reshape broker exports to fit), with a preview and validation step before commit.
- Manual price override for instruments with no feed (e.g. 401(k) fund NAVs).
- Data export (transactions CSV in the import schema, snapshots CSV, full JSON). This is the only backup on the free cloud tier.

**Cost basis**
- Sells use FIFO: they consume the oldest purchase lots first. Lots are tracked internally to compute the remaining basis. This is not tax-lot reporting.
- The holdings table's "avg cost" is derived from the remaining lots.

**Pricing**
- On-demand price refresh (Refresh button) from free APIs. Refresh fetches latest prices, then upserts today's snapshot (one per date; repeated refreshes the same day update it):
  - Stocks/ETFs/funds: Yahoo Finance. No API key, but the endpoint is unofficial and can break; there is no automatic fallback feed. There is no yfinance in Java, so the HTTP endpoints are called directly.
  - Crypto: CoinGecko free tier (about 30 calls/min, demo key). A batch of a few dozen symbols fits within these limits.
- Store the last-known price and its timestamp. Show stale-price indicators when a refresh fails. The manual price override is the fallback when a feed breaks.

**Views**
- Dashboard: total value, total cost basis, total unrealized gain/loss ($ and %), day change if available.
- Holdings table: symbol, quantity, avg cost, price, value, gain/loss; sortable and filterable by account and asset type.
- History: line chart of total portfolio value from stored snapshots. Days with no refresh have no point. There is no backfill from historical prices in v1.
- Allocation: chart of current allocation by asset type, plus target-vs-actual % per asset type. Targets are set per asset type in a settings screen.

**Currency**
- Single base currency (assumed USD). No FX conversion.

## 5. Non-goals (v1)
- Trading or order execution (read-only tracking).
- Rebalancing recommendations or trade suggestions. Only the target-vs-actual view is in scope.
- Tax reporting, tax lots, wash-sale handling.
- Performance metrics beyond total value and unrealized gain/loss: time-weighted / money-weighted returns, dividends, and realized gains (candidates for v2).
- Native mobile app or push notifications.
- Broker/bank aggregator sync (Plaid-style) and direct exchange APIs.
- Multi-currency.
- Automated/scheduled refresh (v2 candidate).
- Backfilling snapshot history from historical prices.
- Column-mapping UI or broker-specific CSV parsers.

## 6. Non-functional requirements
- Security: in the cloud phase, login required (single user, passkey; recovery via a bootstrap secret in an environment variable that re-opens enrollment); data encrypted in transit and at rest; no broker credentials stored.
- Local phase auth: no login. The API and frontend bind to localhost only and must never be exposed beyond it. Passkey auth is built as part of phase 2, before any cloud exposure.
- Passkeys (WebAuthn): the relying-party ID is the Vercel (or custom) domain the browser sees. Registration and login go through the Next.js proxy to the API.
- Reliability: Refresh is idempotent, reports per-instrument failures without failing the whole run, and can be re-run at any time. Concurrent refreshes are guarded.
- Numeric precision: money and quantities use exact decimals (no floating point).
- Cost: run on free or low-cost hosting and free data APIs.
- Privacy: no third-party analytics.

## 7. Decisions
| Topic | Decision |
|---|---|
| Performance metrics | v1 shows total value and unrealized gain/loss only. TWR/MWR and dividends/realized gains are deferred. |
| Allocation and rebalancing | Allocation chart and target-vs-actual view are in v1. Rebalancing recommendations are out. |
| Cost-basis method | FIFO, with lots tracked internally. |
| Hosting and auth | Local first (no login, localhost-only), then cloud: Vercel (Next.js frontend), Render (Spring Boot API + Postgres), passkey auth. |
| Frontend-to-API | Next.js proxies `/api/*` to the Render API (same-origin, no CORS). The API accepts only proxied traffic. |
| Price APIs | Yahoo for stocks/ETFs/funds; CoinGecko for crypto. Manual override is the fallback (no second feed). |
| Fees | Not modeled in v1; basis = quantity × price. |
| Snapshot timezone | UTC. |
| Cloud tier and backup | Render free tier; manual data export (re-importable transactions CSV + JSON) is the only backup. |
| Domain | `*.vercel.app` for now; passkeys re-enrolled via bootstrap secret if it changes. |
| Splits, dividends, reinvestment | Manual transactions (split, dividend reinvestment). Cash dividends ignored. |
| Tech stack | Java 21 + Gradle Spring Boot API (Spring Data JPA, Flyway), Next.js App Router + TypeScript + Tailwind + Recharts, Postgres. Monorepo: `/api`, `/web`, `/docs`. |
| Price refresh | On-demand only (no scheduler). Refresh fetches prices and upserts today's snapshot. |
| Snapshots | Daily total plus per-holding rows. No historical backfill in v1. |
| CSV import | One generic documented schema, no mapping UI. |
| Passkey recovery | Bootstrap secret in an environment variable re-opens enrollment. |

## 8. Open questions
- **Snapshot restore:** should the JSON export be importable for a full restore, given the free-tier database can expire?
- **Free Postgres expiry:** confirm Render's current free-database lifetime before storing real data.

## 9. Verification (how we'll know v1 works)
- Import a real broker CSV, and totals match the broker's statement within rounding.
- Add a crypto, an ETF, and a manual-priced fund, and all appear in the dashboard with correct gain/loss.
- Enter several buys and a partial sell: the remaining basis matches a FIFO calculation. A split entry adjusts quantity and per-share cost correctly.
- Set target allocations: the allocation chart and target-vs-actual view render correctly.
- Clicking Refresh updates prices and creates or updates today's snapshot row (clicking twice leaves one row). A simulated API failure shows stale-price indicators without breaking the dashboard.
- The value-over-time chart reflects the snapshots.
- All of the above work in the local deployment (phase 1).
- In the cloud deployment (phase 2), the app is unreachable without logging in, and the Render API rejects requests that do not come through the Next.js proxy.
- Passkey registration and login work end-to-end through the proxy.
