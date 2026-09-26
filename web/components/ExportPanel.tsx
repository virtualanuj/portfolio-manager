"use client";

import { useSyncExternalStore } from "react";

import { Card } from "@/components/ui/Card";
import { formatDate, todayIso } from "@/lib/format";

const KEY = "lastExported";
const NUDGE_AFTER_DAYS = 30;
const DAY_MS = 86_400_000;

const listeners = new Set<() => void>();

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** Storage can be blocked, in which case the panel simply behaves as if nothing was exported. */
function readLastExported(): string | null {
  try {
    return window.localStorage.getItem(KEY);
  } catch {
    return null;
  }
}

function daysBetween(fromIso: string, to: Date): number {
  const [year = 0, month = 1, day = 1] = fromIso.split("-").map(Number);
  const from = Date.UTC(year, month - 1, day);
  const today = Date.UTC(to.getFullYear(), to.getMonth(), to.getDate());
  return Math.round((today - from) / DAY_MS);
}

const DOWNLOADS = [
  {
    href: "/api/export/transactions.csv",
    label: "Transactions CSV",
    hint: "Can be imported again to rebuild your positions.",
  },
  {
    href: "/api/export/snapshots.csv",
    label: "Snapshots CSV",
    hint: "Daily portfolio values, for spreadsheets.",
  },
  {
    href: "/api/export/all.json",
    label: "Full backup (JSON)",
    hint: "Accounts, instruments, transactions, manual prices, targets and snapshots.",
  },
];

export function ExportPanel({ now }: { now?: Date }) {
  const lastExported = useSyncExternalStore(subscribe, readLastExported, () => null);
  const current = now ?? new Date();

  function remember() {
    try {
      window.localStorage.setItem(KEY, todayIso(current));
    } catch {
      // Without storage the reminder cannot work, but the download still does.
    }
    listeners.forEach((listener) => listener());
  }

  const age = lastExported ? daysBetween(lastExported, current) : null;

  return (
    <section aria-labelledby="export-heading" className="flex flex-col gap-3">
      <h2 id="export-heading" className="text-lg font-semibold">
        Export your data
      </h2>
      <p className="max-w-prose text-sm text-muted">
        Your data lives only in this app&apos;s database. Export it now and then so you can restore
        it: import the transactions CSV, then re-enter manual prices and targets.
      </p>
      {age !== null && age > NUDGE_AFTER_DAYS && (
        <div
          role="status"
          aria-label="Backup reminder"
          className="rounded-panel bg-warn-bg px-4 py-3 text-sm text-warn"
        >
          It has been {age} days since your last export. Download a fresh copy below.
        </div>
      )}
      <Card className="flex flex-col gap-4">
        <ul className="flex flex-col gap-3">
          {DOWNLOADS.map((item) => (
            <li key={item.href}>
              <a
                href={item.href}
                download
                onClick={remember}
                className="font-medium text-accent underline"
              >
                {item.label}
              </a>
              <p className="text-sm text-muted">{item.hint}</p>
            </li>
          ))}
        </ul>
        <p className="text-sm text-muted">
          {lastExported ? `Last exported ${formatDate(lastExported)}` : "Not exported yet"}
        </p>
      </Card>
    </section>
  );
}
