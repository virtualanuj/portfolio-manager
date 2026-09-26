import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { ValueChart, ValueTooltip } from "@/components/ValueChart";
import type { DecimalString, SnapshotPoint } from "@/lib/types";

const d = (value: string) => value as DecimalString;

const point = (date: string, value: string, basis = "1000.0000"): SnapshotPoint => ({
  date,
  totalValue: d(value),
  totalCostBasis: d(basis),
});

/** Dots on the value line: one per snapshot. (A lone point also gets a cost basis dot.) */
function dots(container: HTMLElement) {
  return container.querySelectorAll('.recharts-dot[name="Value"]');
}

describe("ValueChart", () => {
  it("draws one point per snapshot", () => {
    const { container } = render(
      <ValueChart
        points={[
          point("2026-09-24", "1000.0000"),
          point("2026-09-25", "1100.0000"),
          point("2026-09-26", "1200.0000"),
        ]}
        today="2026-09-26"
      />,
    );

    expect(dots(container)).toHaveLength(3);
  });

  it("does not invent a point for a missing day", () => {
    const { container } = render(
      <ValueChart
        points={[point("2026-09-24", "1000.0000"), point("2026-09-26", "1200.0000")]}
        today="2026-09-26"
      />,
    );

    expect(dots(container)).toHaveLength(2);
    const table = screen.getByRole("table", { name: /portfolio value by date/i });
    expect(within(table).getAllByRole("row").slice(1)).toHaveLength(2);
    expect(within(table).queryByText(/Sep 25, 2026/)).not.toBeInTheDocument();
  });

  it("explains that history starts at the first refresh when there are no snapshots", () => {
    render(<ValueChart points={[]} today="2026-09-26" />);

    expect(screen.getByText(/history starts at your first refresh/i)).toBeInTheDocument();
    expect(screen.queryByRole("tablist")).not.toBeInTheDocument();
  });

  it("filters points with the range selector", async () => {
    const { container } = render(
      <ValueChart
        points={[
          point("2025-01-01", "900.0000"),
          point("2026-08-01", "1000.0000"),
          point("2026-09-20", "1200.0000"),
        ]}
        today="2026-09-26"
      />,
    );
    expect(dots(container)).toHaveLength(3);

    await userEvent.click(screen.getByRole("tab", { name: "1Y" }));
    expect(dots(container)).toHaveLength(2);

    await userEvent.click(screen.getByRole("tab", { name: "1M" }));
    expect(dots(container)).toHaveLength(1);
  });

  it("says when the selected range has no snapshots", async () => {
    render(<ValueChart points={[point("2025-01-01", "900.0000")]} today="2026-09-26" />);

    await userEvent.click(screen.getByRole("tab", { name: "1M" }));

    expect(screen.getByText(/no snapshots in this range/i)).toBeInTheDocument();
  });

  it("offers the same numbers as a table with formatted values", () => {
    render(
      <ValueChart points={[point("2026-09-26", "1234.5000", "1000.0000")]} today="2026-09-26" />,
    );

    const table = screen.getByRole("table", { name: /portfolio value by date/i });
    expect(within(table).getByText("Sep 26, 2026")).toBeInTheDocument();
    expect(within(table).getByText("$1,234.50")).toBeInTheDocument();
    expect(within(table).getByText("$1,000.00")).toBeInTheDocument();
  });
});

describe("ValueTooltip", () => {
  it("shows the date and both values formatted from the exact strings", () => {
    render(
      <ValueTooltip
        active
        payload={[{ payload: point("2026-09-26", "12345678901234567890.1234", "1000.0000") }]}
      />,
    );

    expect(screen.getByText("Sep 26, 2026")).toBeInTheDocument();
    expect(screen.getByText(/\$12,345,678,901,234,567,890\.12/)).toBeInTheDocument();
    expect(screen.getByText(/\$1,000\.00/)).toBeInTheDocument();
  });

  it("renders nothing when inactive", () => {
    const { container } = render(<ValueTooltip active={false} payload={[]} />);

    expect(container).toBeEmptyDOMElement();
  });
});
