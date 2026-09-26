import type { ReactNode } from "react";

import { ThemeToggle } from "@/components/ThemeToggle";

/** Top bar: page-level actions (such as Refresh) on the left, theme toggle on the right. */
export function TopBar({ actions }: { actions?: ReactNode }) {
  return (
    <header className="flex items-center justify-between gap-3 border-b border-line bg-surface px-4 py-2.5 md:px-8">
      <div className="flex items-center gap-3">{actions}</div>
      <ThemeToggle />
    </header>
  );
}
