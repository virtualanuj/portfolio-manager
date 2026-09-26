import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { TargetEditor } from "@/components/TargetEditor";
import { ApiError } from "@/lib/api";
import type { DecimalString, TargetAllocation } from "@/lib/types";

const d = (value: string) => value as DecimalString;

function setup(initial: TargetAllocation[] = [], onSave = vi.fn().mockResolvedValue(undefined)) {
  render(<TargetEditor initial={initial} onSave={onSave} />);
  return onSave;
}

async function enter(label: string, value: string) {
  const input = screen.getByLabelText(label);
  await userEvent.clear(input);
  await userEvent.type(input, value);
}

describe("TargetEditor", () => {
  it("starts with nothing entered: total 0.00, 100.00 remaining, Save disabled", () => {
    setup();

    expect(screen.getByText(/total 0\.00%/i)).toBeInTheDocument();
    expect(screen.getByText(/100\.00% left to assign/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /save targets/i })).toBeDisabled();
  });

  it("shows a running total and the remaining amount as you type", async () => {
    setup();

    await enter("ETF", "60");
    expect(screen.getByText(/total 60\.00%/i)).toBeInTheDocument();
    expect(screen.getByText(/40\.00% left to assign/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /save targets/i })).toBeDisabled();

    await enter("Crypto", "40");
    expect(screen.getByText(/total 100\.00%/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /save targets/i })).toBeEnabled();
  });

  it("adds decimals exactly", async () => {
    setup();

    await enter("Stock", "33.33");
    await enter("ETF", "33.33");
    await enter("Mutual fund", "33.34");

    expect(screen.getByText(/total 100\.00%/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /save targets/i })).toBeEnabled();
  });

  it("says how far over 100 the total is and keeps Save disabled", async () => {
    setup();

    await enter("ETF", "70");
    await enter("Crypto", "40");

    expect(screen.getByText(/10\.00% over/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /save targets/i })).toBeDisabled();
  });

  it("keeps Save disabled for an invalid entry", async () => {
    setup();

    await enter("ETF", "abc");

    expect(screen.getByText(/whole or decimal number/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /save targets/i })).toBeDisabled();
  });

  it("loads existing targets", () => {
    setup([
      { assetType: "ETF", targetPct: d("60.00") },
      { assetType: "CRYPTO", targetPct: d("40.00") },
    ]);

    expect(screen.getByLabelText("ETF")).toHaveValue("60.00");
    expect(screen.getByText(/total 100\.00%/i)).toBeInTheDocument();
  });

  it("saves only the types that have a value, as decimal strings", async () => {
    const onSave = setup();

    await enter("ETF", "60");
    await enter("Crypto", "40");
    await userEvent.click(screen.getByRole("button", { name: /save targets/i }));

    expect(onSave).toHaveBeenCalledWith([
      { assetType: "ETF", targetPct: "60" },
      { assetType: "CRYPTO", targetPct: "40" },
    ]);
  });

  it("shows the server's 400 message", async () => {
    const onSave = vi
      .fn()
      .mockRejectedValue(new ApiError(400, "Targets must add up to 100.00 but add up to 99.99"));
    setup([], onSave);

    await enter("ETF", "100");
    await userEvent.click(screen.getByRole("button", { name: /save targets/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("must add up to 100.00");
  });

  it("can clear all targets", async () => {
    const onSave = setup([{ assetType: "ETF", targetPct: d("100.00") }]);

    await userEvent.click(screen.getByRole("button", { name: /clear targets/i }));

    expect(onSave).toHaveBeenCalledWith([]);
  });
});
