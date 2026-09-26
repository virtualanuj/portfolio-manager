"use client";

import type { ColumnDef } from "@tanstack/react-table";
import { useMemo, useState } from "react";
import { mutate } from "swr";

import { InstrumentForm } from "@/components/InstrumentForm";
import { ManualPriceForm } from "@/components/ManualPriceForm";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { DataTable } from "@/components/ui/DataTable";
import { Dialog } from "@/components/ui/Dialog";
import { EmptyState } from "@/components/ui/EmptyState";
import { useToast } from "@/components/ui/Toast";
import { api } from "@/lib/api";
import { formatDate, formatMoney } from "@/lib/format";
import type { InstrumentFormValues, ManualPriceFormValues } from "@/lib/schemas";
import type { Instrument } from "@/lib/types";
import { useApi } from "@/lib/useApi";

const KEY = "/api/instruments";

const ASSET_LABELS = { STOCK: "Stock", ETF: "ETF", MUTUAL_FUND: "Mutual fund", CRYPTO: "Crypto" };
const SOURCE_LABELS = { YAHOO: "Yahoo Finance", COINGECKO: "CoinGecko", MANUAL: "Manual" };

/** Drops empty optional fields so the API applies its defaults. */
function toRequest(values: InstrumentFormValues) {
  return {
    symbol: values.symbol,
    assetType: values.assetType,
    ...(values.name && { name: values.name }),
    ...(values.priceSource && { priceSource: values.priceSource }),
    ...(values.sourceId && { sourceId: values.sourceId }),
  };
}

function toFormValues(instrument: Instrument): InstrumentFormValues {
  return {
    symbol: instrument.symbol,
    name: instrument.name ?? "",
    assetType: instrument.assetType,
    priceSource: instrument.priceSource,
    sourceId: instrument.sourceId ?? "",
  };
}

export function InstrumentsSection() {
  const { data, error, isLoading } = useApi<Instrument[]>(KEY);
  const toast = useToast();
  const [editing, setEditing] = useState<Instrument | "new" | null>(null);
  const [pricing, setPricing] = useState<Instrument | null>(null);
  const [deleting, setDeleting] = useState<Instrument | null>(null);

  async function switchToManual(instrument: Instrument) {
    try {
      await api.put(`${KEY}/${instrument.id}`, {
        ...toRequest(toFormValues(instrument)),
        priceSource: "MANUAL",
      });
      await mutate(KEY);
      setPricing({ ...instrument, priceSource: "MANUAL" });
    } catch (failure) {
      toast.show(
        failure instanceof Error ? failure.message : "Could not switch to manual",
        "error",
      );
    }
  }

  const columns = useMemo<ColumnDef<Instrument>[]>(
    () => [
      { accessorKey: "symbol", header: "Symbol" },
      {
        accessorKey: "name",
        header: "Name",
        cell: ({ getValue }) => getValue<string | null>() ?? "—",
      },
      {
        accessorKey: "assetType",
        header: "Type",
        cell: ({ getValue }) => ASSET_LABELS[getValue<Instrument["assetType"]>()],
      },
      {
        accessorKey: "priceSource",
        header: "Price source",
        cell: ({ getValue }) => SOURCE_LABELS[getValue<Instrument["priceSource"]>()],
      },
      {
        id: "manualPrice",
        header: "Manual price",
        enableSorting: false,
        meta: { numeric: true },
        cell: ({ row }) =>
          row.original.manualPrice === null
            ? "—"
            : `${formatMoney(row.original.manualPrice)} as of ${formatDate(row.original.manualAsOf)}`,
      },
      {
        id: "actions",
        header: () => <span className="sr-only">Actions</span>,
        enableSorting: false,
        cell: ({ row }) => {
          const instrument = row.original;
          return (
            <div className="flex justify-end gap-1">
              <Button variant="ghost" onClick={() => setEditing(instrument)}>
                Edit<span className="sr-only"> {instrument.symbol}</span>
              </Button>
              <Button variant="ghost" onClick={() => setPricing(instrument)}>
                Set price<span className="sr-only"> for {instrument.symbol}</span>
              </Button>
              {instrument.priceSource !== "MANUAL" && (
                <Button variant="ghost" onClick={() => void switchToManual(instrument)}>
                  Switch to manual<span className="sr-only"> for {instrument.symbol}</span>
                </Button>
              )}
              <Button variant="ghost" onClick={() => setDeleting(instrument)}>
                Delete<span className="sr-only"> {instrument.symbol}</span>
              </Button>
            </div>
          );
        },
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps -- handlers only call stable state setters and toast
    [],
  );

  async function save(values: InstrumentFormValues) {
    if (editing && editing !== "new") await api.put(`${KEY}/${editing.id}`, toRequest(values));
    else await api.post(KEY, toRequest(values));
    await mutate(KEY);
    setEditing(null);
    toast.show("Instrument saved");
  }

  async function saveManualPrice(values: ManualPriceFormValues) {
    if (!pricing) return;
    await api.put(`${KEY}/${pricing.id}/manual-price`, values);
    await mutate(KEY);
    setPricing(null);
    toast.show("Price saved");
  }

  async function remove() {
    if (!deleting) return;
    await api.del(`${KEY}/${deleting.id}`);
    await mutate(KEY);
    setDeleting(null);
    toast.show("Instrument deleted");
  }

  return (
    <section aria-labelledby="instruments-heading" className="flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <h2 id="instruments-heading" className="text-lg font-semibold">
          Instruments
        </h2>
        <Button variant="primary" onClick={() => setEditing("new")}>
          Add instrument
        </Button>
      </div>
      {error ? (
        <p role="alert" className="text-sm text-loss">
          Could not load instruments: {error.message}
        </p>
      ) : (
        <DataTable
          columns={columns}
          data={data ?? []}
          loading={isLoading}
          emptyState={
            <EmptyState
              title="No instruments yet"
              description="Add the stocks, funds and coins you hold, then record transactions for them."
              action={<Button onClick={() => setEditing("new")}>Add your first instrument</Button>}
            />
          }
        />
      )}
      <Dialog
        open={editing !== null}
        onClose={() => setEditing(null)}
        title={editing === "new" ? "Add instrument" : "Edit instrument"}
      >
        <InstrumentForm
          initial={editing && editing !== "new" ? toFormValues(editing) : undefined}
          onSubmit={save}
          onCancel={() => setEditing(null)}
        />
      </Dialog>
      <Dialog
        open={pricing !== null}
        onClose={() => setPricing(null)}
        title={`Set price for ${pricing?.symbol ?? ""}`}
      >
        <ManualPriceForm onSubmit={saveManualPrice} onCancel={() => setPricing(null)} />
      </Dialog>
      <ConfirmDialog
        open={deleting !== null}
        title="Delete instrument"
        message={`Delete ${deleting?.symbol ?? "this instrument"}? Instruments with transactions cannot be deleted.`}
        confirmLabel="Delete instrument"
        onConfirm={remove}
        onCancel={() => setDeleting(null)}
      />
    </section>
  );
}
