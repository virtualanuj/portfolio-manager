"use client";

import useSWR, { mutate } from "swr";

import { api, type ApiError } from "./api";

/** Reads `path` with SWR; pass null to skip the request. */
export function useApi<T>(path: string | null) {
  return useSWR<T, ApiError>(path, (key: string) => api.get<T>(key));
}

/** Refetches every /api/* read, so holdings, dashboard and lists all reflect a change. */
export function revalidateAll() {
  return mutate((key) => typeof key === "string" && key.startsWith("/api/"));
}
