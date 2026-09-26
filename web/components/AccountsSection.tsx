"use client";

import type { ColumnDef } from "@tanstack/react-table";
import { useMemo, useState } from "react";
import { mutate } from "swr";

import { AccountForm } from "@/components/AccountForm";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { DataTable } from "@/components/ui/DataTable";
import { Dialog } from "@/components/ui/Dialog";
import { EmptyState } from "@/components/ui/EmptyState";
import { useToast } from "@/components/ui/Toast";
import { api } from "@/lib/api";
import type { AccountFormValues } from "@/lib/schemas";
import type { Account } from "@/lib/types";
import { useApi } from "@/lib/useApi";

const KEY = "/api/accounts";

const TYPE_LABELS = {
  BROKERAGE: "Brokerage",
  RETIREMENT: "Retirement",
  CRYPTO: "Crypto",
  OTHER: "Other",
};

export function AccountsSection() {
  const { data, error, isLoading } = useApi<Account[]>(KEY);
  const toast = useToast();
  const [editing, setEditing] = useState<Account | "new" | null>(null);
  const [deleting, setDeleting] = useState<Account | null>(null);

  const columns = useMemo<ColumnDef<Account>[]>(
    () => [
      { accessorKey: "name", header: "Name" },
      {
        accessorKey: "type",
        header: "Type",
        cell: ({ getValue }) => TYPE_LABELS[getValue<Account["type"]>()],
      },
      {
        id: "actions",
        header: () => <span className="sr-only">Actions</span>,
        enableSorting: false,
        cell: ({ row }) => (
          <div className="flex justify-end gap-1">
            <Button variant="ghost" onClick={() => setEditing(row.original)}>
              Edit<span className="sr-only"> {row.original.name}</span>
            </Button>
            <Button variant="ghost" onClick={() => setDeleting(row.original)}>
              Delete<span className="sr-only"> {row.original.name}</span>
            </Button>
          </div>
        ),
      },
    ],
    [],
  );

  async function save(values: AccountFormValues) {
    if (editing && editing !== "new") await api.put(`${KEY}/${editing.id}`, values);
    else await api.post(KEY, values);
    await mutate(KEY);
    setEditing(null);
    toast.show("Account saved");
  }

  async function remove() {
    if (!deleting) return;
    await api.del(`${KEY}/${deleting.id}`);
    await mutate(KEY);
    setDeleting(null);
    toast.show("Account deleted");
  }

  return (
    <section aria-labelledby="accounts-heading" className="flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <h2 id="accounts-heading" className="text-lg font-semibold">
          Accounts
        </h2>
        <Button variant="primary" onClick={() => setEditing("new")}>
          Add account
        </Button>
      </div>
      {error ? (
        <p role="alert" className="text-sm text-loss">
          Could not load accounts: {error.message}
        </p>
      ) : (
        <DataTable
          columns={columns}
          data={data ?? []}
          loading={isLoading}
          emptyState={
            <EmptyState
              title="No accounts yet"
              description="An account groups your holdings, such as a brokerage or a 401(k)."
              action={<Button onClick={() => setEditing("new")}>Add your first account</Button>}
            />
          }
        />
      )}
      <Dialog
        open={editing !== null}
        onClose={() => setEditing(null)}
        title={editing === "new" ? "Add account" : "Edit account"}
      >
        <AccountForm
          initial={editing && editing !== "new" ? editing : undefined}
          onSubmit={save}
          onCancel={() => setEditing(null)}
        />
      </Dialog>
      <ConfirmDialog
        open={deleting !== null}
        title="Delete account"
        message={`Delete ${deleting?.name ?? "this account"}? Accounts with transactions cannot be deleted.`}
        confirmLabel="Delete account"
        onConfirm={remove}
        onCancel={() => setDeleting(null)}
      />
    </section>
  );
}
