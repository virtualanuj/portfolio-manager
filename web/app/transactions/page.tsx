"use client";

import Link from "next/link";
import { useCallback, useState } from "react";

import { TransactionFilterBar } from "@/components/TransactionFilterBar";
import { TransactionForm } from "@/components/TransactionForm";
import { TransactionsTable } from "@/components/TransactionsTable";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { Dialog } from "@/components/ui/Dialog";
import { EmptyState } from "@/components/ui/EmptyState";
import { useToast } from "@/components/ui/Toast";
import { api } from "@/lib/api";
import { formatDate, formatQty } from "@/lib/format";
import type { TransactionFormValues } from "@/lib/schemas";
import {
  NO_FILTERS,
  PAGE_SIZE,
  transactionsUrl,
  type TransactionFilters,
} from "@/lib/transactionQuery";
import { toFormValues, toTransactionRequest } from "@/lib/transactionRequest";
import type { Account, Instrument, Transaction, TransactionPage } from "@/lib/types";
import { revalidateAll, useApi } from "@/lib/useApi";

export default function TransactionsPage() {
  const toast = useToast();
  const [filters, setFilters] = useState<TransactionFilters>(NO_FILTERS);
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Transaction | "new" | null>(null);
  const [deleting, setDeleting] = useState<Transaction | null>(null);

  const accounts = useApi<Account[]>("/api/accounts");
  const instruments = useApi<Instrument[]>("/api/instruments");
  const transactions = useApi<TransactionPage>(transactionsUrl(filters, page));

  const onEdit = useCallback((transaction: Transaction) => setEditing(transaction), []);
  const onDelete = useCallback((transaction: Transaction) => setDeleting(transaction), []);

  const noSetup = (accounts.data?.length ?? 0) === 0 || (instruments.data?.length ?? 0) === 0;
  const total = transactions.data?.totalItems ?? 0;
  const lastPage = Math.max(Math.ceil(total / PAGE_SIZE) - 1, 0);

  async function save(values: TransactionFormValues) {
    const body = toTransactionRequest(values);
    if (editing && editing !== "new") await api.put(`/api/transactions/${editing.id}`, body);
    else await api.post("/api/transactions", body);
    await revalidateAll();
    setEditing(null);
    toast.show("Transaction saved");
  }

  async function remove() {
    if (!deleting) return;
    await api.del(`/api/transactions/${deleting.id}`);
    await revalidateAll();
    setDeleting(null);
    toast.show("Transaction deleted");
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Transactions</h1>
        <Button variant="primary" disabled={noSetup} onClick={() => setEditing("new")}>
          Add transaction
        </Button>
      </div>

      {noSetup && !accounts.isLoading && !instruments.isLoading && (
        <EmptyState
          title="Add an account and an instrument first"
          description="A transaction records a trade in one of your accounts, so it needs both."
          action={
            <Link href="/settings" className="text-sm font-medium text-accent underline">
              Go to Settings
            </Link>
          }
        />
      )}

      {!noSetup && (
        <>
          <TransactionFilterBar
            accounts={accounts.data ?? []}
            instruments={instruments.data ?? []}
            filters={filters}
            onChange={(next) => {
              setFilters(next);
              setPage(0);
            }}
          />
          {transactions.error ? (
            <p role="alert" className="text-sm text-loss">
              Could not load transactions: {transactions.error.message}
            </p>
          ) : (
            <TransactionsTable
              transactions={transactions.data?.items ?? []}
              loading={transactions.isLoading}
              onEdit={onEdit}
              onDelete={onDelete}
              emptyState={
                <EmptyState
                  title="No transactions yet"
                  description="Record a buy to start tracking a position, or change the filters."
                  action={
                    <Button onClick={() => setEditing("new")}>Add your first transaction</Button>
                  }
                />
              }
            />
          )}
          {total > PAGE_SIZE && (
            <div className="flex items-center justify-between text-sm">
              <span className="text-muted">
                Page {page + 1} of {lastPage + 1}
              </span>
              <div className="flex gap-2">
                <Button disabled={page === 0} onClick={() => setPage(page - 1)}>
                  Previous
                </Button>
                <Button disabled={page >= lastPage} onClick={() => setPage(page + 1)}>
                  Next
                </Button>
              </div>
            </div>
          )}
        </>
      )}

      <Dialog
        open={editing !== null}
        onClose={() => setEditing(null)}
        title={editing === "new" ? "Add transaction" : "Edit transaction"}
      >
        <TransactionForm
          accounts={accounts.data ?? []}
          instruments={instruments.data ?? []}
          initial={editing && editing !== "new" ? toFormValues(editing) : undefined}
          onSubmit={save}
          onCancel={() => setEditing(null)}
        />
      </Dialog>
      <ConfirmDialog
        open={deleting !== null}
        title="Delete transaction"
        message={
          deleting
            ? `Delete this ${deleting.type.toLowerCase()} of ${deleting.type === "SPLIT" ? `${deleting.splitNumerator}:${deleting.splitDenominator}` : formatQty(deleting.quantity)} ${deleting.symbol} on ${formatDate(deleting.tradeDate)}?`
            : ""
        }
        confirmLabel="Delete transaction"
        onConfirm={remove}
        onCancel={() => setDeleting(null)}
      />
    </div>
  );
}
