# Phase 1 acceptance record (local)

Run on 2026-09-26 against `main`. This is the gate before Phase 2 (`plan.md` M6-T7): **Phase 2 does not start until the owner signs off below.**

## Automated results

| Check | Result |
|---|---|
| `scripts/check.sh` (API format and tests, web format, tests, lint, build, privacy scan, README links) | passed |
| API tests (`./gradlew test`, Testcontainers Postgres 16) | 304 tests, 0 failures |
| Web tests (`npm run test`) | 174 tests, 0 failures |
| End to end (`npx playwright test`) | 34 tests, 0 failures |
| README followed from a clean clone in a bare shell (2026-09-26) | dashboard reached; `scripts/check.sh` passed |

## Verification items (intent.md section 9)

| ID | Item | Method | Result |
|---|---|---|---|
| V-1 | A real broker CSV imports and totals match the broker statement within rounding | Automated: sample CSV with hand-calculated totals (`ImportApiIT`, `e2e/import.spec.ts`). **By hand with a real broker CSV: not done.** | **Pending: needs your broker export** |
| V-2 | A crypto, an ETF and a manual-priced fund appear with correct gain or loss | `e2e/golden-path.spec.ts` (dashboard, holdings) and `HoldingsApiIT` | Pass |
| V-3 | Buys and a partial sell match FIFO; a split adjusts quantity and per-share cost | `FifoEngineTest` (includes the spec section 5.1 example); golden path (VTI: 32 shares at $56.25, basis $1,800) | Pass |
| V-4 | Target allocations set; chart and target-vs-actual render correctly | `AllocationApiIT`, component tests, seeded a11y run; figures checked by hand (30,000 / 33,568 = 89.37%) | Pass |
| V-5 | Refresh updates prices and one snapshot per day; a feed failure shows stale indicators without breaking the dashboard | `SnapshotServiceIT`, `RefreshServiceIT`, golden path (two refreshes, one point), `e2e/refresh-failure.spec.ts` | Pass |
| V-6 | The value-over-time chart reflects the snapshots | `ValueChart` tests, golden path, and a look at a chart with five snapshots | Pass |
| V-7 | All of the above work in the local deployment | The whole suite runs against the `local` profile (plus `e2e` stubs for feeds) | Pass, apart from the real-network items below |

## Other checks

| Item | Method | Result |
|---|---|---|
| No Wi-Fi: refresh reports FAILED or PARTIAL, no error page, other screens usable | Simulated: real adapters pointed at a closed port returned `FAILED` with "Yahoo request failed: ConnectException" and kept prior state; stub-based offline spec passes. **Real Wi-Fi-off run by hand: not done.** | **Pending: needs you** |
| Responsive, 400 px, no page-level horizontal scroll | `e2e/a11y.spec.ts` on all seven pages | Pass |
| Accessibility, both themes | axe: no serious or critical violations on all seven pages and the transaction dialog; keyboard-only entry works | Pass |
| Privacy, only own-origin requests; no analytics or CDN in the build | `e2e/network-audit.spec.ts`, `scripts/check-privacy.sh` | Pass |
| Local API cannot be exposed | `LoopbackGuardTest`, `LocalProfileBindingIT` (real application refuses to start on `0.0.0.0`) | Pass |

## Known gaps and notes

- V-1 with a real broker CSV, and the real Wi-Fi-off refresh, need you. Both are quick: reshape one broker export to the schema in the README and import it; then switch Wi-Fi off, click Refresh, and browse.
- The dashboard has no mini value chart (spec section 10); History has the chart.
- Refresh needs the internet and the unofficial Yahoo API, which can change without notice; the manual price is the fallback.
- Decisions made where the spec was silent are listed in `spec.md` section 17.
- `intent.md` sections 8 and 9 needed no change: no user-visible behaviour differs from them.

## Sign-off

- [ ] V-1 real broker CSV checked
- [ ] Wi-Fi-off refresh checked
- [ ] Owner approves starting Phase 2 (M7)

Signed off by: ______________  Date: ______________
