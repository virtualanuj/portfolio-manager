"use client";

import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";

import { useToast } from "@/components/ui/Toast";

import { ApiError, api } from "./api";
import type { RefreshRun } from "./types";
import { revalidateAll } from "./useApi";

export const POLL_INTERVAL_MS = 1000;
export const WAKE_HINT_AFTER_MS = 3000;

export type RefreshState = {
  running: boolean;
  /** The request has been pending for a while; a sleeping server may still be starting. */
  waking: boolean;
  /** The most recent finished run, kept so a partial result can be shown. */
  run: RefreshRun | null;
};

export type RefreshApi = RefreshState & { start: () => Promise<void>; dismiss: () => void };

export const RefreshContext = createContext<RefreshApi | null>(null);

export function useRefresh(): RefreshApi {
  const api = useContext(RefreshContext);
  if (!api) throw new Error("useRefresh must be used inside RefreshProvider");
  return api;
}

/** Starts a run, or attaches to the one already running when the server says 409. */
async function beginRun(): Promise<string> {
  try {
    return (await api.post<{ runId: string }>("/api/refresh")).runId;
  } catch (error) {
    const existing =
      error instanceof ApiError && error.status === 409 ? error.problem?.runId : null;
    if (typeof existing === "string") return existing;
    throw error;
  }
}

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

/** Drives a refresh: start, poll once a second until it finishes, then reload every read. */
export function useRefreshController(): RefreshApi {
  const toast = useToast();
  const [state, setState] = useState<RefreshState>({ running: false, waking: false, run: null });
  const busy = useRef(false);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const start = useCallback(async () => {
    if (busy.current) return;
    busy.current = true;
    setState({ running: true, waking: false, run: null });
    const wakeTimer = setTimeout(() => {
      if (mounted.current) setState((current) => ({ ...current, waking: true }));
    }, WAKE_HINT_AFTER_MS);

    try {
      const runId = await beginRun();
      clearTimeout(wakeTimer);
      setState((current) => ({ ...current, waking: false }));

      let run = await api.get<RefreshRun>(`/api/refresh/${runId}`);
      while (run.status === "RUNNING" && mounted.current) {
        await sleep(POLL_INTERVAL_MS);
        run = await api.get<RefreshRun>(`/api/refresh/${runId}`);
      }
      await revalidateAll();
      if (mounted.current) setState({ running: false, waking: false, run });
      if (run.status === "FAILED") {
        toast.show(
          "Prices could not be refreshed. Your last known prices are still shown.",
          "error",
        );
      } else if (run.status === "SUCCEEDED") {
        toast.show("Prices refreshed");
      }
    } catch (error) {
      if (mounted.current) setState({ running: false, waking: false, run: null });
      toast.show(error instanceof Error ? error.message : "Refresh failed", "error");
    } finally {
      clearTimeout(wakeTimer);
      busy.current = false;
    }
  }, [toast]);

  const dismiss = useCallback(() => setState((current) => ({ ...current, run: null })), []);

  return { ...state, start, dismiss };
}
