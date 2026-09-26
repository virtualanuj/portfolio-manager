"use client";

import type { ReactNode } from "react";

import { RefreshContext, useRefreshController } from "@/lib/useRefresh";

/** One shared refresh state, so the top bar button and the warning banner stay in step. */
export function RefreshProvider({ children }: { children: ReactNode }) {
  const controller = useRefreshController();
  return <RefreshContext.Provider value={controller}>{children}</RefreshContext.Provider>;
}
