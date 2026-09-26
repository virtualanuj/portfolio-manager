"use client";

import { Cell, Pie, PieChart, ResponsiveContainer } from "recharts";

import { formatPct } from "@/lib/format";
import type { AllocationRow, AssetType } from "@/lib/types";

const LABELS: Record<AssetType, string> = {
  STOCK: "Stock",
  ETF: "ETF",
  MUTUAL_FUND: "Mutual fund",
  CRYPTO: "Crypto",
};

const COLORS: Record<AssetType, string> = {
  ETF: "var(--chart-1)",
  STOCK: "var(--chart-2)",
  MUTUAL_FUND: "var(--chart-3)",
  CRYPTO: "var(--chart-4)",
};

/** Donut of actual allocation. Every slice is also named in text with its percentage. */
export function AllocationDonut({ rows }: { rows: AllocationRow[] }) {
  const description = `Allocation by asset type: ${rows
    .map((row) => `${LABELS[row.assetType]} ${formatPct(row.actualPct)}`)
    .join(", ")}`;
  // Recharts needs numbers to size slices; the labels below come from the exact strings.
  const data = rows.map((row) => ({
    name: LABELS[row.assetType],
    type: row.assetType,
    value: Number(row.value),
  }));

  return (
    <div className="flex flex-col items-center gap-4 sm:flex-row sm:gap-8">
      <div role="img" aria-label={description} className="h-56 w-56 shrink-0">
        <ResponsiveContainer
          width="100%"
          height="100%"
          initialDimension={{ width: 224, height: 224 }}
        >
          <PieChart>
            <Pie
              data={data}
              dataKey="value"
              nameKey="name"
              innerRadius="60%"
              outerRadius="100%"
              stroke="var(--surface)"
              strokeWidth={2}
              isAnimationActive={false}
            >
              {data.map((slice) => (
                <Cell key={slice.type} fill={COLORS[slice.type]} />
              ))}
            </Pie>
          </PieChart>
        </ResponsiveContainer>
      </div>
      <ul aria-label="Asset types" className="flex flex-col gap-2 text-sm">
        {rows.map((row) => (
          <li key={row.assetType} className="flex items-center gap-2">
            <span
              aria-hidden="true"
              className="inline-block h-3 w-3 rounded-sm"
              style={{ background: COLORS[row.assetType] }}
            />
            <span className="font-medium">{LABELS[row.assetType]}</span>
            <span className="text-muted">{formatPct(row.actualPct)}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
