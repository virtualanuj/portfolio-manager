import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { ConfirmDialog } from "@/components/ui/ConfirmDialog";

describe("ConfirmDialog", () => {
  it("runs the action when confirmed", async () => {
    const onConfirm = vi.fn().mockResolvedValue(undefined);
    render(
      <ConfirmDialog
        open
        title="Delete account"
        message="Delete Main?"
        confirmLabel="Delete account"
        onConfirm={onConfirm}
        onCancel={() => {}}
      />,
    );

    await userEvent.click(screen.getByRole("button", { name: "Delete account" }));

    expect(onConfirm).toHaveBeenCalledOnce();
  });

  it("shows the server's reason inline when the action fails", async () => {
    const onConfirm = vi
      .fn()
      .mockRejectedValue(new Error("Account Main has transactions and cannot be deleted"));
    render(
      <ConfirmDialog
        open
        title="Delete account"
        message="Delete Main?"
        confirmLabel="Delete account"
        onConfirm={onConfirm}
        onCancel={() => {}}
      />,
    );

    await userEvent.click(screen.getByRole("button", { name: "Delete account" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("has transactions");
  });

  it("does not render its content when closed", () => {
    render(
      <ConfirmDialog
        open={false}
        title="Delete account"
        message="Delete Main?"
        confirmLabel="Delete"
        onConfirm={vi.fn()}
        onCancel={() => {}}
      />,
    );

    expect(screen.queryByText("Delete Main?")).not.toBeInTheDocument();
  });
});
