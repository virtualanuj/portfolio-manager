import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { DriftTable } from "@/components/DriftTable";
import type { AllocationRow, DecimalString } from "@/lib/types";

const d = (value: string) => value as DecimalString;

const rows: AllocationRow[] = [
  {
    assetType: "ETF",
    value: d("750.0000"),
    actualPct: d("75.0000"),
    targetPct: d("60.0000"),
    driftPct: d("15.0000"),
  },
  {
    assetType: "CRYPTO",
    value: d("250.0000"),
    actualPct: d("25.0000"),
    targetPct: d("40.0000"),
    driftPct: d("-15.0000"),
  },
];

describe("DriftTable", () => {
  it("shows actual, target and drift with sign and arrow", () => {
    render(<DriftTable rows={rows} targetsSet />);

    const etf = screen.getByRole("row", { name: /etf/i });
    expect(within(etf).getByText("75.00%")).toBeInTheDocument();
    expect(within(etf).getByText("60.00%")).toBeInTheDocument();
    expect(within(etf).getByText(/▲/)).toBeInTheDocument();
    expect(within(etf).getByText(/\+15\.00 pts/)).toBeInTheDocument();

    const crypto = screen.getByRole("row", { name: /crypto/i });
    expect(within(crypto).getByText(/▼/)).toBeInTheDocument();
    expect(within(crypto).getByText(/-15\.00 pts/)).toBeInTheDocument();
  });

  it("draws a bar per drift whose length is exposed as text", () => {
    render(<DriftTable rows={rows} targetsSet />);

    expect(screen.getAllByRole("img", { name: /drift/i })).toHaveLength(2);
  });

  it("shows actuals only when no targets are set", () => {
    render(
      <DriftTable
        rows={rows.map((r) => ({ ...r, targetPct: null, driftPct: null }))}
        targetsSet={false}
      />,
    );

    expect(screen.queryByRole("columnheader", { name: /target/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("columnheader", { name: /drift/i })).not.toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: /actual/i })).toBeInTheDocument();
  });

  it("formats values from the exact strings", () => {
    render(<DriftTable rows={rows} targetsSet />);

    expect(screen.getByText("$750.00")).toBeInTheDocument();
  });
});
