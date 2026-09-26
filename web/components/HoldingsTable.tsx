"use client";

import type { ColumnDef } from "@tanstack/react-table";
import Link from "next/link";
import { useMemo, useState } from "react";

import { Delta, PctDelta } from "@/components/Delta";
import { Money } from "@/components/Money";
import { StaleBadge } from "@/components/StaleBadge";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { EmptyState } from "@/components/ui/EmptyState";
import { Field } from "@/components/ui/Field";
import { Select } from "@/components/ui/Select";
import { compareDecimals } from "@/lib/decimal";
import { formatQty, formatUnitPrice } from "@/lib/format";
import type { AssetType, HoldingRow } from "@/lib/types";

const ASSET_LABELS: Record<AssetType, string> = {
  STOCK: "Stock",
  ETF: "ETF",
  MUTUAL_FUND: "Mutual fund",
  CRYPTO: "Crypto",
};

/** Sorts decimal strings numerically; rows without a value go last in either direction. */
function decimalColumn(
  id: string,
  header: string,
  read: (row: HoldingRow) => string | null,
  cell: (row: HoldingRow) => React.ReactNode,
): ColumnDef<HoldingRow> {
  return {
    id,
    header,
    accessorFn: (row) => read(row) ?? undefined,
    sortUndefined: "last",
    sortingFn: (a, b, columnId) =>
      compareDecimals(a.getValue<string>(columnId), b.getValue<string>(columnId)),
    meta: { numeric: true },
    cell: ({ row }) => cell(row.original),
  };
}

type Props = { rows: HoldingRow[]; loading?: boolean };

export function HoldingsTable({ rows, loading }: Props) {
  const [accountId, setAccountId] = useState("");
  const [assetType, setAssetType] = useState("");

  const accounts = useMemo(() => {
    const seen = new Map<string, string>();
    for (const row of rows) seen.set(row.accountId, row.accountName);
    return [...seen.entries()];
  }, [rows]);

  const filtered = useMemo(
    () =>
      rows.filter(
        (row) =>
          (accountId === "" || row.accountId === accountId) &&
          (assetType === "" || row.assetType === assetType),
      ),
    [rows, accountId, assetType],
  );

  const columns = useMemo<ColumnDef<HoldingRow>[]>(
    () => [
      { accessorKey: "symbol", header: "Symbol" },
      { accessorKey: "accountName", header: "Account" },
      {
        accessorKey: "assetType",
        header: "Type",
        cell: ({ getValue }) => ASSET_LABELS[getValue<AssetType>()],
      },
      decimalColumn(
        "quantity",
        "Quantity",
        (r) => r.quantity,
        (r) => formatQty(r.quantity),
      ),
      decimalColumn(
        "avgCost",
        "Avg cost",
        (r) => r.avgCost,
        (r) => formatUnitPrice(r.avgCost),
      ),
      decimalColumn(
        "price",
        "Price",
        (r) => r.price,
        (r) =>
          r.priceStatus === "UNPRICED" ? (
            <span className="text-warn">No price</span>
          ) : (
            <span className="inline-flex flex-wrap items-center justify-end gap-2">
              {formatUnitPrice(r.price)}
              <StaleBadge status={r.priceStatus} asOf={r.priceAsOf} />
            </span>
          ),
      ),
      decimalColumn(
        "value",
        "Value",
        (r) => r.value,
        (r) => <Money value={r.value} />,
      ),
      decimalColumn(
        "unrealized",
        "Gain/loss",
        (r) => r.unrealized,
        (r) => <Delta amount={r.unrealized} />,
      ),
      decimalColumn(
        "unrealizedPct",
        "Gain/loss %",
        (r) => r.unrealizedPct,
        (r) => <PctDelta value={r.unrealizedPct} />,
      ),
    ],
    [],
  );

  const filtersActive = accountId !== "" || assetType !== "";

  return (
    <div className="flex flex-col gap-4">
      <div className="grid max-w-md grid-cols-2 gap-3">
        <Field id="holdings-account" label="Account">
          <Select
            id="holdings-account"
            value={accountId}
            onChange={(event) => setAccountId(event.target.value)}
          >
            <option value="">All accounts</option>
            {accounts.map(([id, name]) => (
              <option key={id} value={id}>
                {name}
              </option>
            ))}
          </Select>
        </Field>
        <Field id="holdings-asset-type" label="Asset type">
          <Select
            id="holdings-asset-type"
            value={assetType}
            onChange={(event) => setAssetType(event.target.value)}
          >
            <option value="">All types</option>
            {(Object.keys(ASSET_LABELS) as AssetType[]).map((type) => (
              <option key={type} value={type}>
                {ASSET_LABELS[type]}
              </option>
            ))}
          </Select>
        </Field>
      </div>
      <DataTable
        columns={columns}
        data={filtered}
        loading={loading}
        emptyState={
          filtersActive ? (
            <EmptyState
              title="No holdings match these filters"
              action={
                <Button
                  onClick={() => {
                    setAccountId("");
                    setAssetType("");
                  }}
                >
                  Clear filters
                </Button>
              }
            />
          ) : (
            <EmptyState
              title="No holdings yet"
              description="Holdings appear once you record a buy."
              action={
                <Link href="/transactions" className="text-sm font-medium text-accent underline">
                  Add your first transaction
                </Link>
              }
            />
          )
        }
      />
    </div>
  );
}
