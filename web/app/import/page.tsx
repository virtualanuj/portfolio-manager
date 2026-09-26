"use client";

import { useState } from "react";

import { ImportDropzone } from "@/components/ImportDropzone";
import { ImportPreview } from "@/components/ImportPreview";
import { useToast } from "@/components/ui/Toast";
import { api } from "@/lib/api";
import type { ImportCommitResult, ImportPreviewData } from "@/lib/types";
import { revalidateAll } from "@/lib/useApi";

const SCHEMA: Array<{ column: string; required: string; values: string }> = [
  { column: "date", required: "Yes", values: "YYYY-MM-DD" },
  { column: "account", required: "Yes", values: "Account name" },
  { column: "symbol", required: "Yes", values: "Ticker or coin symbol" },
  { column: "asset_type", required: "Yes", values: "STOCK, ETF, MUTUAL_FUND or CRYPTO" },
  { column: "type", required: "Yes", values: "BUY, SELL, SPLIT or REINVEST" },
  {
    column: "quantity",
    required: "Buy, sell, reinvest",
    values: "Number above 0, up to 8 decimals",
  },
  { column: "price", required: "Buy, sell, reinvest", values: "Price per unit, 0 or more" },
  { column: "split_ratio", required: "Split", values: "New:old shares, for example 2:1" },
  { column: "source_id", required: "New crypto", values: "CoinGecko coin id, for example bitcoin" },
  {
    column: "account_type",
    required: "No",
    values: "BROKERAGE, RETIREMENT, CRYPTO or OTHER (default OTHER)",
  },
  { column: "note", required: "No", values: "Free text" },
];

function plural(count: number, one: string, many = `${one}s`): string {
  return `${count} ${count === 1 ? one : many}`;
}

export default function ImportPage() {
  const toast = useToast();
  const [preview, setPreview] = useState<ImportPreviewData | null>(null);
  const [result, setResult] = useState<ImportCommitResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function upload(file: File) {
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      setPreview(await api.upload<ImportPreviewData>("/api/imports", file));
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "The upload failed. Try again.");
    } finally {
      setBusy(false);
    }
  }

  async function commit(includeDuplicates: boolean) {
    if (!preview) return;
    setBusy(true);
    setError(null);
    try {
      const outcome = await api.post<ImportCommitResult>(
        `/api/imports/${preview.id}/commit?includeDuplicates=${includeDuplicates}`,
      );
      await revalidateAll();
      setResult(outcome);
      setPreview(null);
      toast.show("Import complete");
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "The import failed. Try again.");
    } finally {
      setBusy(false);
    }
  }

  async function discard() {
    if (!preview) return;
    setBusy(true);
    try {
      await api.del(`/api/imports/${preview.id}`);
      setPreview(null);
      setError(null);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Could not discard the upload.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-8">
      <h1 className="text-2xl font-semibold">Import</h1>

      {result && (
        <section
          aria-label="Import result"
          className="rounded-panel bg-gain-bg px-4 py-3 text-gain"
        >
          <p className="font-medium">
            {plural(result.transactionsCreated, "transaction")} imported
          </p>
          <p className="text-sm">
            {plural(result.accountsCreated, "account")} created,{" "}
            {plural(result.instrumentsCreated, "instrument")} created
            {result.duplicatesSkipped > 0 &&
              `, ${plural(result.duplicatesSkipped, "duplicate")} skipped`}
            .
          </p>
        </section>
      )}

      {error && (
        <p role="alert" className="text-sm text-loss">
          {error}
        </p>
      )}

      {preview ? (
        <ImportPreview preview={preview} onCommit={commit} onDiscard={discard} busy={busy} />
      ) : (
        <ImportDropzone onFile={upload} disabled={busy} />
      )}

      <section aria-labelledby="schema-heading" className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 id="schema-heading" className="text-lg font-semibold">
            File format
          </h2>
          <a
            href="/sample-transactions.csv"
            download
            className="text-sm font-medium text-accent underline"
          >
            Download a sample CSV
          </a>
        </div>
        <p className="max-w-prose text-sm text-muted">
          One header row, UTF-8, comma-separated. Column names are not case-sensitive and extra
          columns are ignored. Rows are checked before anything is saved, and you review them first.
        </p>
        <div className="relative overflow-x-auto rounded-panel border border-line bg-surface">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr>
                <th
                  scope="col"
                  className="border-b border-line px-3 py-2 text-left font-medium text-muted"
                >
                  Column
                </th>
                <th
                  scope="col"
                  className="border-b border-line px-3 py-2 text-left font-medium text-muted"
                >
                  Needed for
                </th>
                <th
                  scope="col"
                  className="border-b border-line px-3 py-2 text-left font-medium text-muted"
                >
                  Values
                </th>
              </tr>
            </thead>
            <tbody>
              {SCHEMA.map((entry) => (
                <tr key={entry.column} className="border-b border-line last:border-b-0">
                  <th scope="row" className="px-3 py-2 text-left font-mono font-normal">
                    {entry.column}
                  </th>
                  <td className="px-3 py-2">{entry.required}</td>
                  <td className="px-3 py-2">{entry.values}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
