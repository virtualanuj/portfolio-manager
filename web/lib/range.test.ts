import { describe, expect, it } from "vitest";

import { filterByRange } from "./range";
import type { DecimalString, SnapshotPoint } from "./types";

const point = (date: string): SnapshotPoint => ({
  date,
  totalValue: "100.0000" as DecimalString,
  totalCostBasis: "90.0000" as DecimalString,
});

const points = [
  point("2025-08-01"),
  point("2026-03-01"),
  point("2026-08-25"),
  point("2026-09-20"),
  point("2026-09-26"),
];

describe("filterByRange", () => {
  it("keeps every point for All", () => {
    expect(filterByRange(points, "All", "2026-09-26")).toHaveLength(5);
  });

  it("keeps the last month, boundary included", () => {
    expect(filterByRange(points, "1M", "2026-09-26").map((p) => p.date)).toEqual([
      "2026-09-20",
      "2026-09-26",
    ]);
    expect(filterByRange([point("2026-08-26")], "1M", "2026-09-26")).toHaveLength(1);
    expect(filterByRange([point("2026-08-25")], "1M", "2026-09-26")).toHaveLength(0);
  });

  it("keeps the last six months and the last year", () => {
    expect(filterByRange(points, "6M", "2026-09-26").map((p) => p.date)).toEqual([
      "2026-08-25",
      "2026-09-20",
      "2026-09-26",
    ]);
    expect(filterByRange(points, "1Y", "2026-09-26").map((p) => p.date)).toEqual([
      "2026-03-01",
      "2026-08-25",
      "2026-09-20",
      "2026-09-26",
    ]);
  });

  it("clamps month arithmetic at the end of shorter months", () => {
    expect(filterByRange([point("2026-02-28")], "1M", "2026-03-31")).toHaveLength(1);
    expect(filterByRange([point("2026-02-27")], "1M", "2026-03-31")).toHaveLength(0);
  });
});
