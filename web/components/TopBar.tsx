import { LastRefreshed } from "@/components/LastRefreshed";
import { RefreshButton } from "@/components/RefreshButton";
import { ThemeToggle } from "@/components/ThemeToggle";

/** Top bar: refresh on the left, theme toggle on the right. */
export function TopBar() {
  return (
    <header className="flex items-center justify-between gap-3 border-b border-line bg-surface px-4 py-2.5 md:px-8">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
        <RefreshButton />
        <LastRefreshed />
      </div>
      <ThemeToggle />
    </header>
  );
}
