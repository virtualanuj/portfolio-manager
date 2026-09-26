import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { TransactionForm } from "@/components/TransactionForm";
import { ApiError } from "@/lib/api";
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

function renderForm(onSubmit = vi.fn().mockResolvedValue(undefined)) {
  render(
    <TransactionForm
      accounts={accounts}
      instruments={instruments}
      onSubmit={onSubmit}
      onCancel={() => {}}
    />,
  );
  return onSubmit;
}

async function fillBasics() {
  await userEvent.selectOptions(screen.getByLabelText("Account"), "a1");
  await userEvent.selectOptions(screen.getByLabelText("Instrument"), "i1");
  const date = screen.getByLabelText("Date");
  await userEvent.clear(date);
  await userEvent.type(date, "2026-03-05");
}

describe("TransactionForm", () => {
  it("shows quantity and price for a buy but no split ratio", () => {
    renderForm();

    expect(screen.getByLabelText("Quantity")).toBeInTheDocument();
    expect(screen.getByLabelText("Price per unit")).toBeInTheDocument();
    expect(screen.queryByLabelText("Split ratio, new shares")).not.toBeInTheDocument();
  });

  it("shows only the ratio for a split", async () => {
    renderForm();

    await userEvent.selectOptions(screen.getByLabelText("Type"), "SPLIT");

    expect(screen.getByLabelText("Split ratio, new shares")).toBeInTheDocument();
    expect(screen.getByLabelText("Split ratio, old shares")).toBeInTheDocument();
    expect(screen.queryByLabelText("Quantity")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Price per unit")).not.toBeInTheDocument();
  });

  it.each(["SELL", "REINVEST"])("shows quantity and price for %s", async (type) => {
    renderForm();

    await userEvent.selectOptions(screen.getByLabelText("Type"), type);

    expect(screen.getByLabelText("Quantity")).toBeInTheDocument();
    expect(screen.getByLabelText("Price per unit")).toBeInTheDocument();
  });

  it("rejects a quantity of zero", async () => {
    const onSubmit = renderForm();
    await fillBasics();
    await userEvent.type(screen.getByLabelText("Quantity"), "0");
    await userEvent.type(screen.getByLabelText("Price per unit"), "100");

    await userEvent.click(screen.getByRole("button", { name: /save transaction/i }));

    expect(await screen.findByText("Quantity must be greater than zero")).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("rejects a negative quantity", async () => {
    renderForm();
    await fillBasics();
    await userEvent.type(screen.getByLabelText("Quantity"), "-3");
    await userEvent.type(screen.getByLabelText("Price per unit"), "100");

    await userEvent.click(screen.getByRole("button", { name: /save transaction/i }));

    expect(await screen.findByText("Quantity must be greater than zero")).toBeInTheDocument();
  });

  it("requires an account and an instrument", async () => {
    renderForm();

    await userEvent.click(screen.getByRole("button", { name: /save transaction/i }));

    expect(await screen.findByText("Choose an account")).toBeInTheDocument();
    expect(screen.getByText("Choose an instrument")).toBeInTheDocument();
  });

  it("submits a buy with quantity and price as strings", async () => {
    const onSubmit = renderForm();
    await fillBasics();
    await userEvent.type(screen.getByLabelText("Quantity"), "10.5");
    await userEvent.type(screen.getByLabelText("Price per unit"), "100");

    await userEvent.click(screen.getByRole("button", { name: /save transaction/i }));

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith(
        expect.objectContaining({
          accountId: "a1",
          instrumentId: "i1",
          type: "BUY",
          tradeDate: "2026-03-05",
          quantity: "10.5",
          unitPrice: "100",
        }),
      ),
    );
  });

  it("submits a split ratio", async () => {
    const onSubmit = renderForm();
    await fillBasics();
    await userEvent.selectOptions(screen.getByLabelText("Type"), "SPLIT");
    await userEvent.type(screen.getByLabelText("Split ratio, new shares"), "2");
    await userEvent.type(screen.getByLabelText("Split ratio, old shares"), "1");

    await userEvent.click(screen.getByRole("button", { name: /save transaction/i }));

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith(
        expect.objectContaining({ type: "SPLIT", splitNumerator: "2", splitDenominator: "1" }),
      ),
    );
  });

  it("shows an oversell message from the server beside the form", async () => {
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError(422, "Sell of 15 exceeds the 10 held on 2026-03-05"));
    renderForm(onSubmit);
    await fillBasics();
    await userEvent.selectOptions(screen.getByLabelText("Type"), "SELL");
    await userEvent.type(screen.getByLabelText("Quantity"), "15");
    await userEvent.type(screen.getByLabelText("Price per unit"), "100");

    await userEvent.click(screen.getByRole("button", { name: /save transaction/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Sell of 15 exceeds the 10 held on 2026-03-05",
    );
  });
});
