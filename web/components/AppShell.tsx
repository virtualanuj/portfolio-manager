import type { ReactNode } from "react";

import { RefreshProgress } from "@/components/RefreshProgress";
import { RefreshProvider } from "@/components/RefreshProvider";
import { Sidebar } from "@/components/Sidebar";
import { TopBar } from "@/components/TopBar";
import { ToastProvider } from "@/components/ui/Toast";

export function AppShell({ children }: { children: ReactNode }) {
  return (
    <ToastProvider>
      <RefreshProvider>
        <a
          href="#content"
          className="sr-only focus:not-sr-only focus:absolute focus:z-50 focus:m-2 focus:rounded-control focus:bg-surface focus:px-3 focus:py-2"
        >
          Skip to content
        </a>
        <div className="flex min-h-screen flex-col md:flex-row">
          <Sidebar />
          <div className="flex min-w-0 flex-1 flex-col">
            <TopBar />
            <main
              id="content"
              className="mx-auto w-full max-w-6xl flex-1 px-4 pt-6 pb-24 md:px-8 md:pb-10"
            >
              <div className="mb-6 empty:hidden">
                <RefreshProgress />
              </div>
              {children}
            </main>
          </div>
        </div>
      </RefreshProvider>
    </ToastProvider>
  );
}
