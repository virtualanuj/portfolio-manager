"use client";

import { useMemo, useState } from "react";
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import { Tabs } from "@/components/ui/Tabs";
import { formatDate, formatMoney, todayIso } from "@/lib/format";
import { RANGES, filterByRange, type Range } from "@/lib/range";
import type { SnapshotPoint } from "@/lib/types";

type Props = {
  points: SnapshotPoint[];
  /** Today as YYYY-MM-DD; only used to anchor the range selector. */
  today?: string;
};

type TooltipProps = { active?: boolean; payload?: Array<{ payload: SnapshotPoint }> };

/** Hover details, formatted straight from the API's decimal strings. */
export function ValueTooltip({ active, payload }: TooltipProps) {
  const point = payload?.[0]?.payload;
  if (!active || !point) return null;
  return (
    <div className="rounded-control border border-line bg-surface px-3 py-2 text-sm shadow-lg">
      <p className="font-medium">{formatDate(point.date)}</p>
      <p>Value {formatMoney(point.totalValue)}</p>
      <p className="text-muted">Cost basis {formatMoney(point.totalCostBasis)}</p>
    </div>
  );
}

const DAY_MS = 86_400_000;

function toTime(iso: string): number {
  return Date.parse(`${iso}T00:00:00Z`);
}

/**
 * Value over time from stored snapshots. Only days that have a snapshot get a point, and the time
 * axis is proportional, so a gap looks like a gap instead of being filled in.
 */
export function ValueChart({ points, today = todayIso() }: Props) {
  const [range, setRange] = useState<Range>("All");
  const visible = useMemo(() => filterByRange(points, range, today), [points, range, today]);

  if (points.length === 0) {
    return (
      <div className="rounded-panel border border-dashed border-line p-8">
        <h2 className="text-lg font-semibold">History starts at your first refresh</h2>
        <p className="mt-2 max-w-prose text-sm text-muted">
          Each time you refresh prices, the day&apos;s portfolio value is saved. Days without a
          refresh have no point, and nothing is filled in afterwards.
        </p>
      </div>
    );
  }

  // Recharts needs numbers to plot; the exact strings stay in the tooltip and the table.
  const data = visible.map((point) => ({
    ...point,
    time: toTime(point.date),
    value: Number(point.totalValue),
    basis: Number(point.totalCostBasis),
  }));

  return (
    <div className="flex flex-col gap-4">
      <Tabs
        label="Date range"
        value={range}
        onChange={(id) => setRange(id as Range)}
        tabs={RANGES.map((id) => ({ id, label: id }))}
      />
      {visible.length === 0 ? (
        <p className="text-sm text-muted">No snapshots in this range.</p>
      ) : (
        <div
          role="img"
          aria-label="Line chart of portfolio value and cost basis over time"
          className="h-80 w-full"
        >
          <ResponsiveContainer
            width="100%"
            height="100%"
            initialDimension={{ width: 800, height: 320 }}
          >
            <LineChart data={data} margin={{ top: 8, right: 16, bottom: 8, left: 8 }}>
              <CartesianGrid stroke="var(--line)" strokeDasharray="3 3" />
              <XAxis
                dataKey="time"
                type="number"
                scale="time"
                domain={[(min: number) => min - DAY_MS, (max: number) => max + DAY_MS]}
                tickFormatter={(time: number) =>
                  formatDate(new Date(time).toISOString().slice(0, 10))
                }
                stroke="var(--muted)"
              />
              <YAxis
                domain={["auto", "auto"]}
                tickFormatter={(amount: number) => `$${Math.round(amount).toLocaleString("en-US")}`}
                width={80}
                stroke="var(--muted)"
              />
              <Tooltip content={<ValueTooltip />} />
              <Line
                className="value-line"
                type="linear"
                dataKey="value"
                name="Value"
                stroke="var(--accent)"
                strokeWidth={2}
                dot={{ r: 3 }}
                isAnimationActive={false}
              />
              <Line
                className="basis-line"
                type="linear"
                dataKey="basis"
                name="Cost basis"
                stroke="var(--muted)"
                strokeDasharray="5 4"
                dot={false}
                isAnimationActive={false}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      )}
      <details className="text-sm">
        <summary className="cursor-pointer text-muted">Show the values as a table</summary>
        <div className="relative mt-2 max-h-72 overflow-auto rounded-panel border border-line">
          <table aria-label="Portfolio value by date" className="w-full border-collapse">
            <thead>
              <tr>
                <th scope="col" className="px-3 py-2 text-left font-medium text-muted">
                  Date
                </th>
                <th scope="col" className="px-3 py-2 text-right font-medium text-muted">
                  Value
                </th>
                <th scope="col" className="px-3 py-2 text-right font-medium text-muted">
                  Cost basis
                </th>
              </tr>
            </thead>
            <tbody>
              {visible.map((point) => (
                <tr key={point.date} className="border-t border-line">
                  <td className="px-3 py-1.5">{formatDate(point.date)}</td>
                  <td className="px-3 py-1.5 text-right">{formatMoney(point.totalValue)}</td>
                  <td className="px-3 py-1.5 text-right">{formatMoney(point.totalCostBasis)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </div>
  );
}
