"use client";

import { Button } from "@/components/ui/Button";
import { useRefresh } from "@/lib/useRefresh";

/** After a partial refresh, names the instruments that kept their old price and why. */
export function RefreshProgress() {
  const { run, dismiss } = useRefresh();
  if (run?.status !== "PARTIAL") return null;
  const failed = run.results.filter((result) => !result.ok);
  return (
    <div
      role="status"
      aria-label="Refresh warning"
      className="flex items-start justify-between gap-4 rounded-panel bg-warn-bg px-4 py-3 text-sm text-warn"
    >
      <div>
        <p className="font-medium">
          {failed.length === 1 ? "1 price" : `${failed.length} prices`} could not be refreshed and
          still show the last known value.
        </p>
        <ul className="mt-1 list-disc pl-5">
          {failed.map((result) => (
            <li key={result.symbol}>
              {result.symbol}: {result.message ?? "no reason given"}
            </li>
          ))}
        </ul>
      </div>
      <Button variant="ghost" onClick={dismiss}>
        Dismiss
      </Button>
    </div>
  );
}
