import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Delta } from "@/components/Delta";
import { Money } from "@/components/Money";
import type { DecimalString } from "@/lib/types";

const d = (value: string) => value as DecimalString;

describe("Delta", () => {
  it("shows an up arrow and a plus sign for gains", () => {
    render(<Delta amount={d("1200")} percent={d("20")} />);

    expect(screen.getByText(/▲/)).toBeInTheDocument();
    expect(screen.getByText(/\+\$1,200\.00/)).toBeInTheDocument();
    expect(screen.getByText(/\+20\.00%/)).toBeInTheDocument();
  });

  it("shows a down arrow and a minus sign for losses", () => {
    render(<Delta amount={d("-50.5")} percent={d("-5")} />);

    expect(screen.getByText(/▼/)).toBeInTheDocument();
    expect(screen.getByText(/-\$50\.50/)).toBeInTheDocument();
  });

  it("shows a dash when there is no amount", () => {
    render(<Delta amount={null} percent={null} />);

    expect(screen.getByText("—")).toBeInTheDocument();
  });
});

describe("Money", () => {
  it("formats the value and shows a dash for null", () => {
    const { rerender } = render(<Money value={d("1234.5")} />);
    expect(screen.getByText("$1,234.50")).toBeInTheDocument();

    rerender(<Money value={null} />);
    expect(screen.getByText("—")).toBeInTheDocument();
  });
});
