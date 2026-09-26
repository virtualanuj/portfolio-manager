import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { TransactionFilterBar } from "@/components/TransactionFilterBar";
import { NO_FILTERS } from "@/lib/transactionQuery";
import type { Account, Instrument } from "@/lib/types";

const accounts: Account[] = [
  { id: "a1", name: "Main", type: "BROKERAGE", createdAt: "2026-01-01T00:00:00Z" },
];
const instruments: Instrument[] = [
  {
    id: "i1",
    symbol: "VTI",
    name: null,
    assetType: "ETF",
    priceSource: "YAHOO",
    sourceId: "VTI",
    manualPrice: null,
    manualAsOf: null,
  },
];

describe("TransactionFilterBar", () => {
  function setup() {
    const onChange = vi.fn();
    render(
      <TransactionFilterBar
        accounts={accounts}
        instruments={instruments}
        filters={NO_FILTERS}
        onChange={onChange}
      />,
    );
    return onChange;
  }

  it("reports an account filter", async () => {
    const onChange = setup();
    await userEvent.selectOptions(screen.getByLabelText("Account"), "a1");
    expect(onChange).toHaveBeenLastCalledWith({ ...NO_FILTERS, accountId: "a1" });
  });

  it("reports an instrument filter", async () => {
    const onChange = setup();
    await userEvent.selectOptions(screen.getByLabelText("Instrument"), "i1");
    expect(onChange).toHaveBeenLastCalledWith({ ...NO_FILTERS, instrumentId: "i1" });
  });

  it("reports a type filter", async () => {
    const onChange = setup();
    await userEvent.selectOptions(screen.getByLabelText("Type"), "SELL");
    expect(onChange).toHaveBeenLastCalledWith({ ...NO_FILTERS, type: "SELL" });
  });

  it("reports a date range", async () => {
    const onChange = setup();
    await userEvent.type(screen.getByLabelText("From"), "2026-01-01");
    expect(onChange).toHaveBeenLastCalledWith({ ...NO_FILTERS, from: "2026-01-01" });
  });
});
