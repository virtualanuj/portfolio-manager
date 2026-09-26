import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { PricingBanner } from "@/components/PricingBanner";

describe("PricingBanner", () => {
  it("renders nothing when every position is priced and fresh", () => {
    const { container } = render(<PricingBanner stalePositions={0} unpricedPositions={0} />);

    expect(container).toBeEmptyDOMElement();
  });

  it("explains stale prices", () => {
    render(<PricingBanner stalePositions={2} unpricedPositions={0} />);

    expect(screen.getByRole("status", { name: /pricing warning/i })).toHaveTextContent(
      "2 positions use a stale price",
    );
  });

  it("explains that unpriced positions are left out of the totals", () => {
    render(<PricingBanner stalePositions={0} unpricedPositions={1} />);

    expect(screen.getByRole("status", { name: /pricing warning/i })).toHaveTextContent(
      "1 position has no price",
    );
    expect(screen.getByRole("status", { name: /pricing warning/i })).toHaveTextContent(
      "left out of the totals",
    );
  });

  it("can report both", () => {
    render(<PricingBanner stalePositions={1} unpricedPositions={3} />);

    expect(screen.getByRole("status", { name: /pricing warning/i })).toHaveTextContent(
      "1 position uses a stale price",
    );
    expect(screen.getByRole("status", { name: /pricing warning/i })).toHaveTextContent(
      "3 positions have no price",
    );
  });
});
