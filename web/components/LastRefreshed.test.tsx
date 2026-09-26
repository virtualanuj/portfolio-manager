import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { LastRefreshed } from "@/components/LastRefreshed";

const useApi = vi.fn();
vi.mock("@/lib/useApi", () => ({ useApi: (path: string) => useApi(path) }));

describe("LastRefreshed", () => {
  beforeEach(() => useApi.mockReset());

  it("says when prices were last refreshed", () => {
    useApi.mockReturnValue({
      data: {
        lastRefresh: { runId: "r", status: "SUCCEEDED", finishedAt: "2026-09-26T10:28:00Z" },
      },
    });
    render(<LastRefreshed />);

    expect(screen.getByText(/last refreshed/i)).toBeInTheDocument();
  });

  it("invites a first refresh when none has run", () => {
    useApi.mockReturnValue({ data: { lastRefresh: null } });
    render(<LastRefreshed />);

    expect(screen.getByText(/not refreshed yet/i)).toBeInTheDocument();
  });

  it("shows nothing while loading", () => {
    useApi.mockReturnValue({ data: undefined });
    const { container } = render(<LastRefreshed />);

    expect(container).toBeEmptyDOMElement();
  });
});
