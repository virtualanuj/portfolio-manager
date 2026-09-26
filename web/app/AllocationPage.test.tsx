import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import AllocationPage from "@/app/allocation/page";
import { ToastProvider } from "@/components/ui/Toast";
import type { AllocationData, DecimalString } from "@/lib/types";

const useApi = vi.fn();
vi.mock("@/lib/useApi", () => ({
  useApi: (path: string) => useApi(path),
  revalidateAll: vi.fn(),
}));
vi.mock("@/lib/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api")>()),
  api: { put: vi.fn() },
}));

const d = (value: string) => value as DecimalString;

function serve(allocation: AllocationData, targets: unknown[] = []) {
  useApi.mockImplementation((path: string) => ({
    data: path === "/api/allocation" ? allocation : targets,
    error: undefined,
    isLoading: false,
  }));
}

function setup() {
  render(
    <ToastProvider>
      <AllocationPage />
    </ToastProvider>,
  );
}

describe("AllocationPage", () => {
  beforeEach(() => useApi.mockReset());

  it("shows an empty state with a next step when there are no holdings", () => {
    serve({ totalValue: d("0.0000"), targetsSet: false, rows: [] });
    setup();

    expect(screen.getByText(/no priced holdings yet/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /add a transaction/i })).toHaveAttribute(
      "href",
      "/transactions",
    );
  });

  it("shows only actuals when no targets are set, and still offers the editor", () => {
    serve({
      totalValue: d("1000.0000"),
      targetsSet: false,
      rows: [
        {
          assetType: "ETF",
          value: d("1000.0000"),
          actualPct: d("100.0000"),
          targetPct: null,
          driftPct: null,
        },
      ],
    });
    setup();

    expect(screen.getByRole("columnheader", { name: /actual/i })).toBeInTheDocument();
    expect(screen.queryByRole("columnheader", { name: /drift/i })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /save targets/i })).toBeInTheDocument();
  });

  it("shows target and drift once targets exist", () => {
    serve(
      {
        totalValue: d("1000.0000"),
        targetsSet: true,
        rows: [
          {
            assetType: "ETF",
            value: d("1000.0000"),
            actualPct: d("100.0000"),
            targetPct: d("60.0000"),
            driftPct: d("40.0000"),
          },
        ],
      },
      [{ assetType: "ETF", targetPct: "60.00" }],
    );
    setup();

    expect(screen.getByRole("columnheader", { name: /drift/i })).toBeInTheDocument();
    expect(screen.getByText(/\+40\.00 pts/)).toBeInTheDocument();
  });
});
