"use client";

import useSWR from "swr";

import { api, type ApiError } from "./api";

/** Reads `path` with SWR; pass null to skip the request. */
export function useApi<T>(path: string | null) {
  return useSWR<T, ApiError>(path, (key: string) => api.get<T>(key));
}
