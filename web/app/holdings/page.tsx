"use client";

import { HoldingsTable } from "@/components/HoldingsTable";
import type { HoldingRow } from "@/lib/types";
import { useApi } from "@/lib/useApi";

export default function HoldingsPage() {
  const { data, error, isLoading } = useApi<HoldingRow[]>("/api/holdings");

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-2xl font-semibold">Holdings</h1>
      {error ? (
        <p role="alert" className="text-sm text-loss">
          Could not load holdings: {error.message}
        </p>
      ) : (
        <HoldingsTable rows={data ?? []} loading={isLoading} />
      )}
    </div>
  );
}
