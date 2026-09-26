import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ExportPanel } from "@/components/ExportPanel";

const NOW = new Date(2026, 8, 26, 12, 0, 0);
const KEY = "lastExported";

function daysAgo(days: number): string {
  const date = new Date(NOW);
  date.setDate(date.getDate() - days);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

describe("ExportPanel", () => {
  beforeEach(() => window.localStorage.clear());
  afterEach(() => vi.restoreAllMocks());

  it("offers the transactions CSV, the snapshots CSV and the full JSON as plain download links", () => {
    render(<ExportPanel now={NOW} />);

    expect(screen.getByRole("link", { name: /transactions csv/i })).toHaveAttribute(
      "href",
      "/api/export/transactions.csv",
    );
    expect(screen.getByRole("link", { name: /snapshots csv/i })).toHaveAttribute(
      "href",
      "/api/export/snapshots.csv",
    );
    expect(screen.getByRole("link", { name: /full backup/i })).toHaveAttribute(
      "href",
      "/api/export/all.json",
    );
  });

  it("remembers when a download was started and shows the date", async () => {
    render(<ExportPanel now={NOW} />);
    expect(screen.getByText(/not exported yet/i)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("link", { name: /transactions csv/i }));

    expect(window.localStorage.getItem(KEY)).toBe("2026-09-26");
    expect(screen.getByText(/last exported sep 26, 2026/i)).toBeInTheDocument();
  });

  it("nudges after more than 30 days without an export", () => {
    window.localStorage.setItem(KEY, daysAgo(31));
    render(<ExportPanel now={NOW} />);

    expect(screen.getByRole("status", { name: /backup reminder/i })).toHaveTextContent(/31 days/);
  });

  it("does not nudge at exactly 30 days or when recent", () => {
    window.localStorage.setItem(KEY, daysAgo(30));
    const { unmount } = render(<ExportPanel now={NOW} />);
    expect(screen.queryByRole("status", { name: /backup reminder/i })).not.toBeInTheDocument();
    unmount();

    window.localStorage.setItem(KEY, daysAgo(2));
    render(<ExportPanel now={NOW} />);
    expect(screen.queryByRole("status", { name: /backup reminder/i })).not.toBeInTheDocument();
  });

  it("still works when storage is unavailable", async () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    render(<ExportPanel now={NOW} />);

    await userEvent.click(screen.getByRole("link", { name: /full backup/i }));

    expect(screen.getByRole("link", { name: /full backup/i })).toBeInTheDocument();
  });
});
