import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { ImportPreview } from "@/components/ImportPreview";
import type { ImportPreviewData, ImportRow, RowStatus } from "@/lib/types";

function row(lineNo: number, status: RowStatus, extra: Partial<ImportRow> = {}): ImportRow {
  return {
    lineNo,
    status,
    values: {
      date: "2026-01-05",
      account: "Main",
      symbol: "VTI",
      type: "BUY",
      quantity: "10",
      price: "100",
    },
    errors: [],
    warnings: [],
    createsAccount: false,
    createsInstrument: false,
    duplicate: false,
    ...extra,
  };
}

function preview(
  rows: ImportRow[],
  summary: Partial<ImportPreviewData["summary"]> = {},
): ImportPreviewData {
  return {
    id: "b1",
    filename: "broker.csv",
    status: "STAGED",
    createdAt: "2026-09-26T10:00:00Z",
    fileWarnings: [],
    summary: {
      totalRows: rows.length,
      ok: rows.filter((r) => r.status === "OK").length,
      willCreate: rows.filter((r) => r.status === "WILL_CREATE").length,
      warnings: rows.filter((r) => r.status === "WARNING").length,
      errors: rows.filter((r) => r.status === "ERROR").length,
      duplicates: rows.filter((r) => r.duplicate).length,
      newAccounts: 0,
      newInstruments: 0,
      ...summary,
    },
    rows,
  };
}

function setup(data: ImportPreviewData, props: { onCommit?: (include: boolean) => void } = {}) {
  const onCommit = props.onCommit ?? vi.fn();
  render(<ImportPreview preview={data} onCommit={onCommit} onDiscard={() => {}} />);
  return onCommit;
}

describe("ImportPreview", () => {
  it("shows each row's status with its messages", () => {
    setup(
      preview([
        row(2, "OK"),
        row(3, "WILL_CREATE", { createsAccount: true, createsInstrument: true }),
        row(4, "WARNING", {
          duplicate: true,
          warnings: [{ field: "row", message: "Identical to line 2" }],
        }),
        row(5, "ERROR", {
          errors: [{ field: "quantity", message: "Sell of 15 exceeds the 10 held on 2026-01-06" }],
        }),
      ]),
    );

    const rows = screen.getAllByRole("row").slice(1);
    expect(within(rows[0]!).getByText("OK")).toBeInTheDocument();
    expect(within(rows[1]!).getByText("Will create")).toBeInTheDocument();
    expect(within(rows[1]!).getByText(/new account/i)).toBeInTheDocument();
    expect(within(rows[2]!).getByText("Warning")).toBeInTheDocument();
    expect(within(rows[2]!).getByText("Identical to line 2")).toBeInTheDocument();
    expect(within(rows[3]!).getByText("Error")).toBeInTheDocument();
    expect(within(rows[3]!).getByText(/exceeds the 10 held/)).toBeInTheDocument();
    expect(within(rows[3]!).getByText("5")).toBeInTheDocument();
  });

  it("disables Commit while any row has an error and says why", () => {
    setup(
      preview([
        row(2, "OK"),
        row(3, "ERROR", { errors: [{ field: "date", message: "Date is required" }] }),
      ]),
    );

    expect(screen.getByRole("button", { name: /commit import/i })).toBeDisabled();
    expect(screen.getByText(/fix the errors/i)).toBeInTheDocument();
  });

  it("enables Commit when there are no errors and commits without duplicates by default", async () => {
    const onCommit = setup(preview([row(2, "OK")]));

    await userEvent.click(screen.getByRole("button", { name: /commit import/i }));

    expect(onCommit).toHaveBeenCalledWith(false);
  });

  it("offers to include duplicates and passes the choice on commit", async () => {
    const onCommit = setup(
      preview([
        row(2, "WARNING", {
          duplicate: true,
          warnings: [{ field: "row", message: "Identical to line 1" }],
        }),
      ]),
    );

    await userEvent.click(screen.getByLabelText(/include duplicate rows/i));
    await userEvent.click(screen.getByRole("button", { name: /commit import/i }));

    expect(onCommit).toHaveBeenCalledWith(true);
  });

  it("hides the duplicates toggle when there are none", () => {
    setup(preview([row(2, "OK")]));

    expect(screen.queryByLabelText(/include duplicate rows/i)).not.toBeInTheDocument();
  });

  it("summarises the rows and what will be created", () => {
    setup(
      preview([row(2, "OK"), row(3, "ERROR", { errors: [{ field: "date", message: "x" }] })], {
        newAccounts: 2,
        newInstruments: 1,
      }),
    );

    expect(screen.getByText(/2 rows/)).toBeInTheDocument();
    expect(screen.getByText(/1 error/)).toBeInTheDocument();
    expect(screen.getByText(/2 new accounts/)).toBeInTheDocument();
    expect(screen.getByText(/1 new instrument\b/)).toBeInTheDocument();
  });

  it("shows warnings about the file itself", () => {
    const data = preview([row(2, "OK")]);
    data.fileWarnings = ['Column "broker_ref" is not part of the schema and was ignored'];
    setup(data);

    expect(screen.getByText(/broker_ref/)).toBeInTheDocument();
  });
});
