"use client";

import { Skeleton } from "@/components/ui/Skeleton";
import { ValueChart } from "@/components/ValueChart";
import type { SnapshotPoint } from "@/lib/types";
import { useApi } from "@/lib/useApi";

export default function HistoryPage() {
  const { data, error, isLoading } = useApi<SnapshotPoint[]>("/api/snapshots");

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-2xl font-semibold">History</h1>
      {error ? (
        <p role="alert" className="text-sm text-loss">
          Could not load history: {error.message}
        </p>
      ) : isLoading || !data ? (
        <Skeleton className="h-80 w-full" />
      ) : (
        <ValueChart points={data} />
      )}
    </div>
  );
}
