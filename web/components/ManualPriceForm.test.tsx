import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { ManualPriceForm } from "@/components/ManualPriceForm";

describe("ManualPriceForm", () => {
  it("requires a price", async () => {
    const onSubmit = vi.fn();
    render(<ManualPriceForm onSubmit={onSubmit} onCancel={() => {}} />);

    await userEvent.click(screen.getByRole("button", { name: /save price/i }));

    expect(await screen.findByText("Price is required")).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("requires an as-of date", async () => {
    const onSubmit = vi.fn();
    render(<ManualPriceForm onSubmit={onSubmit} onCancel={() => {}} />);

    await userEvent.type(screen.getByLabelText("Price"), "118.4");
    await userEvent.clear(screen.getByLabelText("As of"));
    await userEvent.click(screen.getByRole("button", { name: /save price/i }));

    expect(await screen.findByText("As-of date is required")).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("rejects a negative price", async () => {
    render(<ManualPriceForm onSubmit={vi.fn()} onCancel={() => {}} />);

    await userEvent.type(screen.getByLabelText("Price"), "-5");
    await userEvent.click(screen.getByRole("button", { name: /save price/i }));

    expect(await screen.findByText(/number of zero or more/i)).toBeInTheDocument();
  });

  it("submits the price as a string with its date", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<ManualPriceForm onSubmit={onSubmit} onCancel={() => {}} />);

    await userEvent.type(screen.getByLabelText("Price"), "118.4");
    await userEvent.click(screen.getByRole("button", { name: /save price/i }));

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith({
        price: "118.4",
        asOf: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
      }),
    );
  });
});
