import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import type { ColumnDef } from "@tanstack/react-table";

import { DataTable } from "@/components/ui/DataTable";

type Row = { symbol: string; value: number };

const columns: ColumnDef<Row>[] = [
  { accessorKey: "symbol", header: "Symbol" },
  { accessorKey: "value", header: "Value" },
];

const rows: Row[] = [
  { symbol: "BBB", value: 2 },
  { symbol: "AAA", value: 3 },
  { symbol: "CCC", value: 1 },
];

function firstColumn() {
  return screen
    .getAllByRole("row")
    .slice(1)
    .map((row) => within(row).getAllByRole("cell")[0]?.textContent);
}

describe("DataTable", () => {
  it("shows the empty state instead of rows when there is no data", () => {
    render(<DataTable columns={columns} data={[]} emptyState={<p>Nothing here yet</p>} />);

    expect(screen.getByText("Nothing here yet")).toBeInTheDocument();
    expect(screen.queryAllByRole("row")).toHaveLength(0);
  });

  it("renders a row per item", () => {
    render(<DataTable columns={columns} data={rows} emptyState={<p>empty</p>} />);

    expect(screen.getAllByRole("row")).toHaveLength(4);
  });

  it("sorts by a column when its header is activated and reports aria-sort", async () => {
    render(<DataTable columns={columns} data={rows} emptyState={<p>empty</p>} />);
    const header = screen.getByRole("columnheader", { name: /symbol/i });

    await userEvent.click(within(header).getByRole("button"));
    expect(firstColumn()).toEqual(["AAA", "BBB", "CCC"]);
    expect(header).toHaveAttribute("aria-sort", "ascending");

    await userEvent.click(within(header).getByRole("button"));
    expect(firstColumn()).toEqual(["CCC", "BBB", "AAA"]);
    expect(header).toHaveAttribute("aria-sort", "descending");
  });

  it("shows skeleton rows while loading", () => {
    render(<DataTable columns={columns} data={[]} loading emptyState={<p>empty</p>} />);

    expect(screen.getByTestId("table-skeleton")).toBeInTheDocument();
    expect(screen.queryByText("empty")).not.toBeInTheDocument();
  });
});
