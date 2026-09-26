"use client";

import Link from "next/link";

import { Delta } from "@/components/Delta";
import { KpiCard } from "@/components/KpiCard";
import { Money } from "@/components/Money";
import { PricingBanner } from "@/components/PricingBanner";
import { EmptyState } from "@/components/ui/EmptyState";
import { Skeleton } from "@/components/ui/Skeleton";
import type { Dashboard } from "@/lib/types";
import { useApi } from "@/lib/useApi";

export default function DashboardPage() {
  const { data, error, isLoading } = useApi<Dashboard>("/api/dashboard");

  if (error) {
    return (
      <p role="alert" className="text-sm text-loss">
        Could not load the dashboard: {error.message}
      </p>
    );
  }
  if (isLoading || !data) {
    return (
      <div className="flex flex-col gap-4">
        <Skeleton className="h-8 w-40" />
        <Skeleton className="h-32 w-full" />
      </div>
    );
  }

  const positions = data.pricedPositions + data.unpricedPositions;
  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-2xl font-semibold">Dashboard</h1>
      {positions === 0 ? (
        <EmptyState
          title="No holdings yet"
          description="Record your first buy and your portfolio value appears here."
          action={
            <Link href="/transactions" className="text-sm font-medium text-accent underline">
              Add your first transaction
            </Link>
          }
        />
      ) : (
        <>
          <PricingBanner
            stalePositions={data.stalePositions}
            unpricedPositions={data.unpricedPositions}
          />
          <KpiCard label="Total value" large>
            <Money value={data.totalValue} />
          </KpiCard>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <KpiCard label="Cost basis">
              <Money value={data.totalCostBasis} />
            </KpiCard>
            <KpiCard label="Unrealized gain/loss">
              <Delta amount={data.unrealized} percent={data.unrealizedPct} />
            </KpiCard>
            {data.dayChange !== null && (
              <KpiCard label="Day change">
                <Delta amount={data.dayChange} />
              </KpiCard>
            )}
          </div>
        </>
      )}
    </div>
  );
}
