import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { InstrumentForm } from "@/components/InstrumentForm";
import { ApiError } from "@/lib/api";

describe("InstrumentForm", () => {
  it("requires a symbol", async () => {
    render(<InstrumentForm onSubmit={vi.fn()} onCancel={() => {}} />);

    await userEvent.click(screen.getByRole("button", { name: /save instrument/i }));

    expect(await screen.findByText("Symbol is required")).toBeInTheDocument();
  });

  it("requires a coin id for crypto priced by CoinGecko", async () => {
    const onSubmit = vi.fn();
    render(<InstrumentForm onSubmit={onSubmit} onCancel={() => {}} />);

    await userEvent.type(screen.getByLabelText("Symbol"), "BTC");
    await userEvent.selectOptions(screen.getByLabelText("Asset type"), "CRYPTO");
    await userEvent.click(screen.getByRole("button", { name: /save instrument/i }));

    expect(
      await screen.findByText("Enter the CoinGecko coin id, for example bitcoin"),
    ).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("does not need a coin id when the crypto is priced manually", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<InstrumentForm onSubmit={onSubmit} onCancel={() => {}} />);

    await userEvent.type(screen.getByLabelText("Symbol"), "OBSCURE");
    await userEvent.selectOptions(screen.getByLabelText("Asset type"), "CRYPTO");
    await userEvent.selectOptions(screen.getByLabelText("Price source"), "MANUAL");
    await userEvent.click(screen.getByRole("button", { name: /save instrument/i }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalled());
  });

  it("submits a crypto with its coin id", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<InstrumentForm onSubmit={onSubmit} onCancel={() => {}} />);

    await userEvent.type(screen.getByLabelText("Symbol"), "BTC");
    await userEvent.selectOptions(screen.getByLabelText("Asset type"), "CRYPTO");
    await userEvent.type(screen.getByLabelText("Source id"), "bitcoin");
    await userEvent.click(screen.getByRole("button", { name: /save instrument/i }));

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith(
        expect.objectContaining({ symbol: "BTC", assetType: "CRYPTO", sourceId: "bitcoin" }),
      ),
    );
  });

  it("shows a duplicate instrument error inline", async () => {
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError(409, "An instrument VTI of type ETF already exists"));
    render(<InstrumentForm onSubmit={onSubmit} onCancel={() => {}} />);

    await userEvent.type(screen.getByLabelText("Symbol"), "VTI");
    await userEvent.click(screen.getByRole("button", { name: /save instrument/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("already exists");
  });
});
