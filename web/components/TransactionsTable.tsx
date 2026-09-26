"use client";

import type { ColumnDef } from "@tanstack/react-table";
import { useMemo } from "react";

import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { formatDate, formatQty, formatUnitPrice } from "@/lib/format";
import type { Transaction } from "@/lib/types";

type Props = {
  transactions: Transaction[];
  loading?: boolean;
  emptyState: React.ReactNode;
  onEdit: (transaction: Transaction) => void;
  onDelete: (transaction: Transaction) => void;
};

const TYPE_LABELS = { BUY: "Buy", SELL: "Sell", SPLIT: "Split", REINVEST: "Reinvest" };

export function TransactionsTable({ transactions, loading, emptyState, onEdit, onDelete }: Props) {
  const columns = useMemo<ColumnDef<Transaction>[]>(
    () => [
      {
        accessorKey: "tradeDate",
        header: "Date",
        cell: ({ getValue }) => formatDate(getValue<string>()),
      },
      { accessorKey: "accountName", header: "Account" },
      { accessorKey: "symbol", header: "Symbol" },
      {
        accessorKey: "type",
        header: "Type",
        cell: ({ getValue }) => <Badge>{TYPE_LABELS[getValue<Transaction["type"]>()]}</Badge>,
      },
      {
        id: "quantity",
        header: "Quantity",
        enableSorting: false,
        meta: { numeric: true },
        cell: ({ row }) =>
          row.original.type === "SPLIT"
            ? `${row.original.splitNumerator}:${row.original.splitDenominator}`
            : formatQty(row.original.quantity),
      },
      {
        id: "unitPrice",
        header: "Price",
        enableSorting: false,
        meta: { numeric: true },
        cell: ({ row }) =>
          row.original.type === "SPLIT" ? "—" : formatUnitPrice(row.original.unitPrice),
      },
      {
        accessorKey: "note",
        header: "Note",
        enableSorting: false,
        cell: ({ getValue }) => getValue<string | null>() ?? "",
      },
      {
        id: "actions",
        header: () => <span className="sr-only">Actions</span>,
        enableSorting: false,
        cell: ({ row }) => (
          <div className="flex justify-end gap-1">
            <Button variant="ghost" onClick={() => onEdit(row.original)}>
              Edit
              <span className="sr-only">
                {" "}
                {row.original.symbol} on {row.original.tradeDate}
              </span>
            </Button>
            <Button variant="ghost" onClick={() => onDelete(row.original)}>
              Delete
              <span className="sr-only">
                {" "}
                {row.original.symbol} on {row.original.tradeDate}
              </span>
            </Button>
          </div>
        ),
      },
    ],
    [onEdit, onDelete],
  );

  return (
    <DataTable
      columns={columns}
      data={transactions}
      loading={loading}
      emptyState={emptyState}
      initialSorting={[]}
    />
  );
}
