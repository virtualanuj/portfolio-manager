import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { HoldingsTable } from "@/components/HoldingsTable";
import type { DecimalString, HoldingRow } from "@/lib/types";

const d = (value: string) => value as DecimalString;

function row(overrides: Partial<HoldingRow> & { symbol: string }): HoldingRow {
  return {
    accountId: "a1",
    accountName: "Brokerage",
    instrumentId: `i-${overrides.symbol}`,
    assetType: "ETF",
    quantity: d("10.00000000"),
    avgCost: d("100.0000"),
    costBasis: d("1000.0000"),
    price: d("110.00000000"),
    value: d("1100.0000"),
    unrealized: d("100.0000"),
    unrealizedPct: d("10.0000"),
    dayChange: null,
    priceStatus: "OK",
    priceAsOf: "2026-09-25",
    ...overrides,
  };
}

const rows: HoldingRow[] = [
  row({ symbol: "MID", value: d("2000.0000"), unrealizedPct: d("5.0000") }),
  row({ symbol: "LOW", value: d("500.0000"), unrealizedPct: d("30.0000") }),
  row({
    symbol: "TOP",
    value: d("9000.0000"),
    unrealizedPct: d("-2.0000"),
    accountId: "a2",
    accountName: "Retirement",
    assetType: "MUTUAL_FUND",
  }),
  row({
    symbol: "NEW",
    price: null,
    value: null,
    unrealized: null,
    unrealizedPct: null,
    priceStatus: "UNPRICED",
    priceAsOf: null,
    assetType: "CRYPTO",
  }),
];

function symbols() {
  return screen
    .getAllByRole("row")
    .slice(1)
    .map((r) => within(r).getAllByRole("cell")[0]?.textContent);
}

describe("HoldingsTable", () => {
  it("sorts by value ascending then descending, keeping unpriced rows last", async () => {
    render(<HoldingsTable rows={rows} />);
    const header = screen.getByRole("columnheader", { name: /^value/i });

    await userEvent.click(within(header).getByRole("button"));
    expect(symbols()).toEqual(["LOW", "MID", "TOP", "NEW"]);

    await userEvent.click(within(header).getByRole("button"));
    expect(symbols()).toEqual(["TOP", "MID", "LOW", "NEW"]);
  });

  it("sorts by gain percentage", async () => {
    render(<HoldingsTable rows={rows} />);
    const header = screen.getByRole("columnheader", { name: /gain\/loss %/i });

    await userEvent.click(within(header).getByRole("button"));
    expect(symbols()).toEqual(["TOP", "MID", "LOW", "NEW"]);
  });

  it("filters by account", async () => {
    render(<HoldingsTable rows={rows} />);

    await userEvent.selectOptions(screen.getByLabelText("Account"), "a2");

    expect(symbols()).toEqual(["TOP"]);
  });

  it("filters by asset type", async () => {
    render(<HoldingsTable rows={rows} />);

    await userEvent.selectOptions(screen.getByLabelText("Asset type"), "CRYPTO");

    expect(symbols()).toEqual(["NEW"]);
  });

  it("shows No price for unpriced holdings", () => {
    render(<HoldingsTable rows={rows} />);

    expect(screen.getByText("No price")).toBeInTheDocument();
  });

  it("badges stale feed prices and stale manual prices", () => {
    render(
      <HoldingsTable
        rows={[
          row({ symbol: "OLD", priceStatus: "STALE", priceAsOf: "2026-09-10" }),
          row({ symbol: "FUND", priceStatus: "MANUAL_STALE", priceAsOf: "2026-06-01" }),
          row({ symbol: "FRESH" }),
        ]}
      />,
    );

    expect(screen.getByText(/Stale, as of Sep 10, 2026/)).toBeInTheDocument();
    expect(screen.getByText(/Manual, stale, as of Jun 1, 2026/)).toBeInTheDocument();
    expect(screen.getAllByText(/stale/i)).toHaveLength(2);
  });

  it("formats money, quantities and gains with signs", () => {
    render(<HoldingsTable rows={[row({ symbol: "ONE", quantity: d("0.00000012") })]} />);

    expect(screen.getByText("$1,100.00")).toBeInTheDocument();
    expect(screen.getByText("0.00000012")).toBeInTheDocument();
    expect(screen.getByText(/\+\$100\.00/)).toBeInTheDocument();
    expect(screen.getByText(/\+10\.00%/)).toBeInTheDocument();
  });

  it("shows an empty state when there are no holdings", () => {
    render(<HoldingsTable rows={[]} />);

    expect(screen.getByText(/no holdings yet/i)).toBeInTheDocument();
  });

  it("says when the filters hide every holding", async () => {
    render(<HoldingsTable rows={rows} />);

    await userEvent.selectOptions(screen.getByLabelText("Account"), "a2");
    await userEvent.selectOptions(screen.getByLabelText("Asset type"), "ETF");

    expect(screen.getByText(/no holdings match these filters/i)).toBeInTheDocument();
  });
});
