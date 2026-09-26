import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import DashboardPage from "@/app/page";
import type { Dashboard, DecimalString } from "@/lib/types";

const useApi = vi.fn();
vi.mock("@/lib/useApi", () => ({ useApi: (path: string) => useApi(path) }));

const d = (value: string) => value as DecimalString;

const dashboard: Dashboard = {
  totalValue: d("12345.6700"),
  totalCostBasis: d("10000.0000"),
  unrealized: d("2345.6700"),
  unrealizedPct: d("23.4567"),
  dayChange: null,
  pricedPositions: 3,
  stalePositions: 0,
  unpricedPositions: 0,
  lastRefresh: null,
};

function ok(data: Dashboard) {
  useApi.mockReturnValue({ data, error: undefined, isLoading: false });
}

describe("DashboardPage", () => {
  beforeEach(() => useApi.mockReset());

  it("shows total value, cost basis and unrealized gain with sign", () => {
    ok(dashboard);
    render(<DashboardPage />);

    expect(screen.getByText("$12,345.67")).toBeInTheDocument();
    expect(screen.getByText("$10,000.00")).toBeInTheDocument();
    expect(screen.getByText(/\+\$2,345\.67/)).toBeInTheDocument();
    expect(screen.getByText(/\+23\.46%/)).toBeInTheDocument();
  });

  it("hides day change when the API has none", () => {
    ok(dashboard);
    render(<DashboardPage />);

    expect(screen.queryByText("Day change")).not.toBeInTheDocument();
  });

  it("shows day change when present", () => {
    ok({ ...dashboard, dayChange: d("-50.0000") });
    render(<DashboardPage />);

    expect(screen.getByText("Day change")).toBeInTheDocument();
    expect(screen.getByText(/-\$50\.00/)).toBeInTheDocument();
  });

  it("warns about stale and unpriced positions", () => {
    ok({ ...dashboard, stalePositions: 1, unpricedPositions: 2 });
    render(<DashboardPage />);

    expect(screen.getByRole("status")).toHaveTextContent("1 position uses a stale price");
    expect(screen.getByRole("status")).toHaveTextContent("2 positions have no price");
  });

  it("shows an empty state with a call to action when there are no holdings", () => {
    ok({ ...dashboard, pricedPositions: 0, totalValue: d("0.0000"), totalCostBasis: d("0.0000") });
    render(<DashboardPage />);

    expect(screen.getByText("No holdings yet")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Add your first transaction" })).toHaveAttribute(
      "href",
      "/transactions",
    );
  });

  it("shows a readable error when the API fails", () => {
    useApi.mockReturnValue({ data: undefined, error: new Error("boom"), isLoading: false });
    render(<DashboardPage />);

    expect(screen.getByRole("alert")).toHaveTextContent("Could not load the dashboard: boom");
  });
});
