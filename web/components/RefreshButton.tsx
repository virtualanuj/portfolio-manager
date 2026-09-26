"use client";

import { Button } from "@/components/ui/Button";
import { useRefresh } from "@/lib/useRefresh";

export function RefreshButton() {
  const { running, waking, start } = useRefresh();
  return (
    <div className="flex items-center gap-3">
      <Button variant="primary" disabled={running} onClick={() => void start()}>
        {running ? "Refreshing…" : "Refresh prices"}
      </Button>
      {waking && (
        <span role="status" className="text-sm text-muted">
          Waking up the server… this can take a minute.
        </span>
      )}
    </div>
  );
}
