"use client";

import Link from "next/link";

import { AllocationDonut } from "@/components/AllocationDonut";
import { DriftTable } from "@/components/DriftTable";
import { TargetEditor } from "@/components/TargetEditor";
import { EmptyState } from "@/components/ui/EmptyState";
import { Skeleton } from "@/components/ui/Skeleton";
import { useToast } from "@/components/ui/Toast";
import { api } from "@/lib/api";
import type { AllocationData, AssetType, TargetAllocation } from "@/lib/types";
import { revalidateAll, useApi } from "@/lib/useApi";

export default function AllocationPage() {
  const toast = useToast();
  const allocation = useApi<AllocationData>("/api/allocation");
  const targets = useApi<TargetAllocation[]>("/api/allocation/targets");

  async function save(entries: Array<{ assetType: AssetType; targetPct: string }>) {
    await api.put("/api/allocation/targets", entries);
    await revalidateAll();
    toast.show(entries.length === 0 ? "Targets cleared" : "Targets saved");
  }

  if (allocation.error || targets.error) {
    const message = (allocation.error ?? targets.error)?.message;
    return (
      <p role="alert" className="text-sm text-loss">
        Could not load the allocation: {message}
      </p>
    );
  }
  if (!allocation.data || !targets.data) {
    return <Skeleton className="h-64 w-full" />;
  }

  const { rows, targetsSet } = allocation.data;
  return (
    <div className="flex flex-col gap-8">
      <h1 className="text-2xl font-semibold">Allocation</h1>
      {rows.length === 0 ? (
        <EmptyState
          title="No priced holdings yet"
          description="Allocation shows how your priced holdings split across stocks, ETFs, funds and crypto."
          action={
            <Link href="/transactions" className="text-sm font-medium text-accent underline">
              Add a transaction
            </Link>
          }
        />
      ) : (
        <>
          <AllocationDonut rows={rows} />
          <DriftTable rows={rows} targetsSet={targetsSet} />
        </>
      )}
      <section id="targets" aria-labelledby="targets-heading" className="flex flex-col gap-3">
        <h2 id="targets-heading" className="text-lg font-semibold">
          Targets
        </h2>
        <p className="max-w-prose text-sm text-muted">
          Set the share you want each asset type to have. The targets must add up to exactly
          100.00%. Leave a type empty for 0%.
        </p>
        <TargetEditor key={JSON.stringify(targets.data)} initial={targets.data} onSave={save} />
      </section>
    </div>
  );
}
