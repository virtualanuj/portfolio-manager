import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { AllocationDonut } from "@/components/AllocationDonut";
import type { AllocationRow, DecimalString } from "@/lib/types";

const d = (value: string) => value as DecimalString;

const rows: AllocationRow[] = [
  {
    assetType: "ETF",
    value: d("750.0000"),
    actualPct: d("75.0000"),
    targetPct: null,
    driftPct: null,
  },
  {
    assetType: "CRYPTO",
    value: d("250.0000"),
    actualPct: d("25.0000"),
    targetPct: null,
    driftPct: null,
  },
];

describe("AllocationDonut", () => {
  it("describes the chart in words", () => {
    render(<AllocationDonut rows={rows} />);

    expect(
      screen.getByRole("img", { name: /allocation by asset type: etf 75\.00%, crypto 25\.00%/i }),
    ).toBeInTheDocument();
  });

  it("labels every slice with text so colour is not the only cue", () => {
    render(<AllocationDonut rows={rows} />);

    const legend = screen.getByRole("list", { name: /asset types/i });
    expect(legend).toHaveTextContent("ETF");
    expect(legend).toHaveTextContent("75.00%");
    expect(legend).toHaveTextContent("Crypto");
    expect(legend).toHaveTextContent("25.00%");
  });
});
