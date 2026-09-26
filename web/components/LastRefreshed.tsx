"use client";

import type { Dashboard } from "@/lib/types";
import { useApi } from "@/lib/useApi";

export function LastRefreshed() {
  const { data } = useApi<Dashboard>("/api/dashboard");
  if (!data) return null;
  const finishedAt = data.lastRefresh?.finishedAt;
  if (!finishedAt) {
    return <span className="text-sm text-muted">Not refreshed yet</span>;
  }
  const when = new Date(finishedAt).toLocaleString(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  });
  return <span className="text-sm text-muted">Last refreshed {when}</span>;
}
