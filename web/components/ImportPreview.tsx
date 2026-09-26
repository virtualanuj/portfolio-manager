"use client";

import { useState } from "react";

import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import type { ImportPreviewData, ImportRow, RowStatus } from "@/lib/types";

type Props = {
  preview: ImportPreviewData;
  onCommit: (includeDuplicates: boolean) => void;
  onDiscard: () => void;
  busy?: boolean;
};

const STATUS: Record<RowStatus, { label: string; tone: "neutral" | "gain" | "warn" | "loss" }> = {
  OK: { label: "OK", tone: "gain" },
  WILL_CREATE: { label: "Will create", tone: "neutral" },
  WARNING: { label: "Warning", tone: "warn" },
  ERROR: { label: "Error", tone: "loss" },
};

function plural(count: number, one: string, many = `${one}s`): string {
  return `${count} ${count === 1 ? one : many}`;
}

function messagesOf(row: ImportRow): string[] {
  const notes: string[] = [];
  if (row.createsAccount) notes.push("New account");
  if (row.createsInstrument) notes.push("New instrument");
  return notes;
}

export function ImportPreview({ preview, onCommit, onDiscard, busy }: Props) {
  const [includeDuplicates, setIncludeDuplicates] = useState(false);
  const { summary } = preview;
  const blocked = summary.errors > 0;

  const parts = [plural(summary.totalRows, "row")];
  if (summary.errors > 0) parts.push(plural(summary.errors, "error"));
  if (summary.warnings > 0) parts.push(plural(summary.warnings, "warning"));
  if (summary.newAccounts > 0) parts.push(plural(summary.newAccounts, "new account"));
  if (summary.newInstruments > 0) parts.push(plural(summary.newInstruments, "new instrument"));

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h2 className="text-lg font-semibold">{preview.filename}</h2>
        <p className="text-sm text-muted">{parts.join(", ")}</p>
      </div>
      {preview.fileWarnings.length > 0 && (
        <ul className="rounded-panel bg-warn-bg px-4 py-3 text-sm text-warn">
          {preview.fileWarnings.map((warning) => (
            <li key={warning}>{warning}</li>
          ))}
        </ul>
      )}
      <div className="max-h-[60vh] overflow-auto rounded-panel border border-line bg-surface">
        <table className="w-full border-collapse text-sm">
          <thead className="sticky top-0 bg-surface">
            <tr>
              {[
                "Line",
                "Status",
                "Date",
                "Account",
                "Symbol",
                "Type",
                "Quantity or ratio",
                "Price",
                "Notes",
              ].map((heading) => (
                <th
                  key={heading}
                  scope="col"
                  className="border-b border-line px-3 py-2 text-left font-medium whitespace-nowrap text-muted"
                >
                  {heading}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {preview.rows.map((row) => {
              const status = STATUS[row.status];
              return (
                <tr key={row.lineNo} className="border-b border-line align-top last:border-b-0">
                  <td className="px-3 py-2">{row.lineNo}</td>
                  <td className="px-3 py-2">
                    <Badge tone={status.tone}>{status.label}</Badge>
                  </td>
                  <td className="px-3 py-2 whitespace-nowrap">{row.values.date}</td>
                  <td className="px-3 py-2">{row.values.account}</td>
                  <td className="px-3 py-2">{row.values.symbol}</td>
                  <td className="px-3 py-2">{row.values.type}</td>
                  <td className="px-3 py-2">{row.values.split_ratio || row.values.quantity}</td>
                  <td className="px-3 py-2">{row.values.price}</td>
                  <td className="px-3 py-2">
                    <ul className="flex flex-col gap-0.5">
                      {row.errors.map((issue) => (
                        <li key={`e-${issue.field}-${issue.message}`} className="text-loss">
                          {issue.message}
                        </li>
                      ))}
                      {row.warnings.map((issue) => (
                        <li key={`w-${issue.field}-${issue.message}`} className="text-warn">
                          {issue.message}
                        </li>
                      ))}
                      {messagesOf(row).map((note) => (
                        <li key={note} className="text-muted">
                          {note}
                        </li>
                      ))}
                    </ul>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {summary.duplicates > 0 && (
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={includeDuplicates}
            onChange={(event) => setIncludeDuplicates(event.target.checked)}
          />
          Include duplicate rows ({summary.duplicates}). They are skipped otherwise.
        </label>
      )}
      <div className="flex flex-wrap items-center gap-3">
        <Button
          variant="primary"
          disabled={blocked || busy}
          onClick={() => onCommit(includeDuplicates)}
        >
          Commit import
        </Button>
        <Button onClick={onDiscard} disabled={busy}>
          Discard
        </Button>
        {blocked && (
          <p className="text-sm text-loss">Fix the errors in your file and upload it again.</p>
        )}
      </div>
    </div>
  );
}
