import type { SnapshotPoint } from "./types";

export const RANGES = ["1M", "6M", "1Y", "All"] as const;
export type Range = (typeof RANGES)[number];

const MONTHS: Record<Exclude<Range, "All">, number> = { "1M": 1, "6M": 6, "1Y": 12 };

/** The date `months` before `iso`, clamped to the end of shorter months (Mar 31 minus 1 = Feb 28). */
function monthsBefore(iso: string, months: number): string {
  const [year = 0, month = 1, day = 1] = iso.split("-").map(Number);
  const target = year * 12 + (month - 1) - months;
  const targetYear = Math.floor(target / 12);
  const targetMonth = (target % 12) + 1;
  const lastDay = new Date(Date.UTC(targetYear, targetMonth, 0)).getUTCDate();
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${targetYear}-${pad(targetMonth)}-${pad(Math.min(day, lastDay))}`;
}

/** Keeps the points on or after the start of the range, counted back from `today` (YYYY-MM-DD). */
export function filterByRange(
  points: SnapshotPoint[],
  range: Range,
  today: string,
): SnapshotPoint[] {
  if (range === "All") return points;
  const cutoff = monthsBefore(today, MONTHS[range]);
  return points.filter((point) => point.date >= cutoff);
}
