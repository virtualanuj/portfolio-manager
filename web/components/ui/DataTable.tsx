"use client";

import {
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  useReactTable,
  type ColumnDef,
  type SortingState,
} from "@tanstack/react-table";
import { useState, type ReactNode } from "react";

import { Skeleton } from "@/components/ui/Skeleton";
import { cn } from "@/lib/cn";

declare module "@tanstack/react-table" {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  interface ColumnMeta<TData, TValue> {
    /** Right-align the column; use for money and quantities. */
    numeric?: boolean;
  }
}

type Props<T> = {
  columns: ColumnDef<T>[];
  data: T[];
  emptyState: ReactNode;
  loading?: boolean;
  /** Initial sort, for example [{ id: "value", desc: true }]. */
  initialSorting?: SortingState;
};

/** Sortable table. It scrolls inside its own container so the page never scrolls sideways. */
export function DataTable<T>({ columns, data, emptyState, loading, initialSorting }: Props<T>) {
  // TanStack's table object is not safe to memoize, so opt this component out of the compiler.
  "use no memo";
  const [sorting, setSorting] = useState<SortingState>(initialSorting ?? []);
  // eslint-disable-next-line react-hooks/incompatible-library -- see "use no memo" above
  const table = useReactTable({
    data,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  });

  if (loading) {
    return (
      <div data-testid="table-skeleton" className="flex flex-col gap-2">
        {[0, 1, 2, 3].map((n) => (
          <Skeleton key={n} className="h-10 w-full" />
        ))}
      </div>
    );
  }
  if (data.length === 0) return <>{emptyState}</>;

  return (
    <div className="max-h-[70vh] overflow-auto rounded-panel border border-line bg-surface">
      <table className="w-full border-collapse text-sm">
        <thead className="sticky top-0 bg-surface">
          {table.getHeaderGroups().map((group) => (
            <tr key={group.id}>
              {group.headers.map((header) => {
                const sorted = header.column.getIsSorted();
                const sortable = header.column.getCanSort();
                return (
                  <th
                    key={header.id}
                    scope="col"
                    aria-sort={
                      sorted === "asc" ? "ascending" : sorted === "desc" ? "descending" : undefined
                    }
                    className={cn(
                      "border-b border-line px-3 py-2 font-medium whitespace-nowrap text-muted",
                      header.column.columnDef.meta?.numeric ? "text-right" : "text-left",
                    )}
                  >
                    {sortable ? (
                      <button
                        type="button"
                        onClick={header.column.getToggleSortingHandler()}
                        className="inline-flex items-center gap-1 hover:text-ink"
                      >
                        {flexRender(header.column.columnDef.header, header.getContext())}
                        <span aria-hidden="true">
                          {sorted === "asc" ? "↑" : sorted === "desc" ? "↓" : ""}
                        </span>
                      </button>
                    ) : (
                      flexRender(header.column.columnDef.header, header.getContext())
                    )}
                  </th>
                );
              })}
            </tr>
          ))}
        </thead>
        <tbody>
          {table.getRowModel().rows.map((row) => (
            <tr key={row.id} className="border-b border-line last:border-b-0">
              {row.getVisibleCells().map((cell) => (
                <td
                  key={cell.id}
                  className={cn(
                    "px-3 py-2 whitespace-nowrap",
                    cell.column.columnDef.meta?.numeric && "text-right",
                  )}
                >
                  {flexRender(cell.column.columnDef.cell, cell.getContext())}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
