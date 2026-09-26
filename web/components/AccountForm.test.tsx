import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { AccountForm } from "@/components/AccountForm";
import { ApiError } from "@/lib/api";

describe("AccountForm", () => {
  it("requires a name", async () => {
    const onSubmit = vi.fn();
    render(<AccountForm onSubmit={onSubmit} onCancel={() => {}} />);

    await userEvent.click(screen.getByRole("button", { name: /save account/i }));

    expect(await screen.findByText("Name is required")).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("submits the entered values", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<AccountForm onSubmit={onSubmit} onCancel={() => {}} />);

    await userEvent.type(screen.getByLabelText("Name"), "Fidelity 401k");
    await userEvent.selectOptions(screen.getByLabelText("Type"), "RETIREMENT");
    await userEvent.click(screen.getByRole("button", { name: /save account/i }));

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith({ name: "Fidelity 401k", type: "RETIREMENT" }),
    );
  });

  it("shows a server conflict inline without closing", async () => {
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError(409, "An account named Main already exists"));
    render(<AccountForm onSubmit={onSubmit} onCancel={() => {}} />);

    await userEvent.type(screen.getByLabelText("Name"), "Main");
    await userEvent.click(screen.getByRole("button", { name: /save account/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "An account named Main already exists",
    );
  });

  it("starts from existing values when editing", () => {
    render(
      <AccountForm
        initial={{ name: "Old", type: "CRYPTO" }}
        onSubmit={vi.fn()}
        onCancel={() => {}}
      />,
    );

    expect(screen.getByLabelText("Name")).toHaveValue("Old");
    expect(screen.getByLabelText("Type")).toHaveValue("CRYPTO");
  });
});
