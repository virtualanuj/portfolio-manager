import { Money } from "@/components/Money";
import { formatPct, formatPoints, signOf } from "@/lib/format";
import { cn } from "@/lib/cn";
import type { AllocationRow, AssetType } from "@/lib/types";

const LABELS: Record<AssetType, string> = {
  STOCK: "Stock",
  ETF: "ETF",
  MUTUAL_FUND: "Mutual fund",
  CRYPTO: "Crypto",
};

/** Bar length is presentation only: percentage points capped at 50 so it fits its half. */
function barWidth(driftPct: string): string {
  const points = Math.min(Math.abs(Number(driftPct)), 50);
  return `${points * 2}%`;
}

function DriftCell({ row }: { row: AllocationRow }) {
  if (row.driftPct === null) return <span>—</span>;
  const sign = signOf(row.driftPct);
  const arrow = sign === "positive" ? "▲" : sign === "negative" ? "▼" : "•";
  const text = formatPoints(row.driftPct, { signed: true });
  return (
    <div className="flex items-center gap-3">
      <span
        role="img"
        aria-label={`Drift ${text}`}
        className="relative block h-2 w-28 rounded-full bg-sunken"
      >
        <span className="absolute inset-y-0 left-1/2 w-px bg-line" />
        <span
          className={cn(
            "absolute inset-y-0 rounded-full",
            sign === "positive" ? "left-1/2 bg-gain" : "right-1/2 bg-loss",
          )}
          style={{ width: `calc(${barWidth(row.driftPct)} / 2)` }}
        />
      </span>
      <span className={cn(sign === "positive" && "text-gain", sign === "negative" && "text-loss")}>
        <span aria-hidden="true">{arrow} </span>
        {text}
      </span>
    </div>
  );
}

export function DriftTable({ rows, targetsSet }: { rows: AllocationRow[]; targetsSet: boolean }) {
  const heading = "border-b border-line px-3 py-2 font-medium whitespace-nowrap text-muted";
  return (
    <div className="overflow-x-auto rounded-panel border border-line bg-surface">
      <table className="w-full border-collapse text-sm">
        <thead>
          <tr>
            <th scope="col" className={cn(heading, "text-left")}>
              Asset type
            </th>
            <th scope="col" className={cn(heading, "text-right")}>
              Value
            </th>
            <th scope="col" className={cn(heading, "text-right")}>
              Actual
            </th>
            {targetsSet && (
              <>
                <th scope="col" className={cn(heading, "text-right")}>
                  Target
                </th>
                <th scope="col" className={cn(heading, "text-left")}>
                  Drift
                </th>
              </>
            )}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.assetType} className="border-b border-line last:border-b-0">
              <th scope="row" className="px-3 py-2 text-left font-medium">
                {LABELS[row.assetType]}
              </th>
              <td className="px-3 py-2 text-right">
                <Money value={row.value} />
              </td>
              <td className="px-3 py-2 text-right">{formatPct(row.actualPct)}</td>
              {targetsSet && (
                <>
                  <td className="px-3 py-2 text-right">{formatPct(row.targetPct)}</td>
                  <td className="px-3 py-2">
                    <DriftCell row={row} />
                  </td>
                </>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
