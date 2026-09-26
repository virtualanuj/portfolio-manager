import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { RefreshButton } from "@/components/RefreshButton";
import { RefreshProgress } from "@/components/RefreshProgress";
import { RefreshProvider } from "@/components/RefreshProvider";
import { ToastProvider } from "@/components/ui/Toast";
import { ApiError } from "@/lib/api";
import type { RefreshRun } from "@/lib/types";

const post = vi.fn();
const get = vi.fn();
const revalidateAll = vi.fn();

vi.mock("@/lib/api", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/lib/api")>();
  return { ...original, api: { get: (p: string) => get(p), post: (p: string) => post(p) } };
});
vi.mock("@/lib/useApi", () => ({ revalidateAll: () => revalidateAll() }));

function run(status: RefreshRun["status"], results: RefreshRun["results"] = []): RefreshRun {
  return {
    id: "run-1",
    status,
    startedAt: "2026-09-26T10:00:00Z",
    finishedAt: status === "RUNNING" ? null : "2026-09-26T10:00:03Z",
    results,
  };
}

function setup() {
  render(
    <ToastProvider>
      <RefreshProvider>
        <RefreshButton />
        <RefreshProgress />
      </RefreshProvider>
    </ToastProvider>,
  );
}

async function click() {
  await userEvent.click(screen.getByRole("button", { name: /refresh prices/i }));
}

describe("Refresh", () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    post.mockReset();
    get.mockReset();
    revalidateAll.mockReset().mockResolvedValue(undefined);
  });
  afterEach(() => vi.useRealTimers());

  it("posts once and disables the button while the run is going", async () => {
    post.mockResolvedValue({ runId: "run-1" });
    get.mockResolvedValue(run("RUNNING"));
    setup();

    await click();

    const button = screen.getByRole("button", { name: /refreshing/i });
    expect(button).toBeDisabled();
    expect(post).toHaveBeenCalledTimes(1);
    expect(post).toHaveBeenCalledWith("/api/refresh");
  });

  it("polls every second until the run finishes, then revalidates everything", async () => {
    post.mockResolvedValue({ runId: "run-1" });
    get
      .mockResolvedValueOnce(run("RUNNING"))
      .mockResolvedValueOnce(run("RUNNING"))
      .mockResolvedValue(run("SUCCEEDED"));
    setup();

    await click();
    await act(() => vi.advanceTimersByTimeAsync(1000));
    expect(get).toHaveBeenCalledTimes(2);
    await act(() => vi.advanceTimersByTimeAsync(1000));

    expect(get).toHaveBeenCalledTimes(3);
    expect(get).toHaveBeenCalledWith("/api/refresh/run-1");
    expect(revalidateAll).toHaveBeenCalled();
    expect(screen.getByRole("button", { name: /refresh prices/i })).toBeEnabled();
  });

  it("attaches to the running refresh when the server answers 409", async () => {
    post.mockRejectedValue(
      new ApiError(409, "A refresh is already running", { status: 409, runId: "run-9" }),
    );
    get.mockResolvedValue(run("SUCCEEDED"));
    setup();

    await click();

    expect(get).toHaveBeenCalledWith("/api/refresh/run-9");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.queryByText(/already running/i)).not.toBeInTheDocument();
  });

  it("lists the failed symbols in a warning when the run is partial", async () => {
    post.mockResolvedValue({ runId: "run-1" });
    get.mockResolvedValue(
      run("PARTIAL", [
        { symbol: "VTI", ok: true, message: null },
        { symbol: "BTC", ok: false, message: "CoinGecko returned HTTP 429" },
      ]),
    );
    setup();

    await click();

    const banner = await screen.findByRole("status", { name: /refresh warning/i });
    expect(banner).toHaveTextContent("BTC");
    expect(banner).toHaveTextContent("CoinGecko returned HTTP 429");
    expect(banner).not.toHaveTextContent("VTI");
  });

  it("shows an error toast when the run fails and leaves the button usable", async () => {
    post.mockResolvedValue({ runId: "run-1" });
    get.mockResolvedValue(
      run("FAILED", [{ symbol: "VTI", ok: false, message: "Yahoo returned HTTP 500" }]),
    );
    setup();

    await click();

    expect(await screen.findByText(/prices could not be refreshed/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /refresh prices/i })).toBeEnabled();
    expect(revalidateAll).toHaveBeenCalled();
  });

  it("shows an error toast when the request itself fails", async () => {
    post.mockRejectedValue(
      new ApiError(0, "Cannot reach the server. Check that the app is running."),
    );
    setup();

    await click();

    expect(await screen.findByText(/cannot reach the server/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /refresh prices/i })).toBeEnabled();
  });

  it("hints that the server is waking up after three seconds without an answer", async () => {
    post.mockReturnValue(new Promise(() => {}));
    setup();

    await click();
    expect(screen.queryByText(/waking up the server/i)).not.toBeInTheDocument();
    await act(() => vi.advanceTimersByTimeAsync(3100));

    expect(screen.getByText(/waking up the server/i)).toBeInTheDocument();
  });

  it("ignores extra clicks while a refresh is running", async () => {
    post.mockResolvedValue({ runId: "run-1" });
    get.mockResolvedValue(run("RUNNING"));
    setup();

    await click();
    await userEvent.click(screen.getByRole("button", { name: /refreshing/i }));

    expect(post).toHaveBeenCalledTimes(1);
  });
});
