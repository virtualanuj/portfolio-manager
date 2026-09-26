"use client";

import { useState } from "react";

import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { Input } from "@/components/ui/Input";
import { compareDecimals, sumDecimals } from "@/lib/decimal";
import type { AssetType, TargetAllocation } from "@/lib/types";

type Props = {
  initial: TargetAllocation[];
  onSave: (targets: Array<{ assetType: AssetType; targetPct: string }>) => Promise<void>;
};

const TYPES: Array<{ type: AssetType; label: string }> = [
  { type: "STOCK", label: "Stock" },
  { type: "ETF", label: "ETF" },
  { type: "MUTUAL_FUND", label: "Mutual fund" },
  { type: "CRYPTO", label: "Crypto" },
];

const VALID = /^\d{1,3}(\.\d{1,2})?$/;

function isValid(text: string): boolean {
  return VALID.test(text) && compareDecimals(text, "100") <= 0;
}

/**
 * Sets the target percentage per asset type. It keeps a running total so the sum can be brought to
 * exactly 100.00 before saving; the server checks it again.
 */
export function TargetEditor({ initial, onSave }: Props) {
  const [values, setValues] = useState<Record<AssetType, string>>(() => {
    const start: Record<AssetType, string> = { STOCK: "", ETF: "", MUTUAL_FUND: "", CRYPTO: "" };
    for (const target of initial) start[target.assetType] = target.targetPct;
    return start;
  });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const entered = TYPES.filter(({ type }) => values[type].trim() !== "");
  const anyInvalid = entered.some(({ type }) => !isValid(values[type].trim()));
  const total = sumDecimals(
    entered
      .filter(({ type }) => isValid(values[type].trim()))
      .map(({ type }) => values[type].trim()),
    2,
  );
  const comparison = compareDecimals(total, "100");
  const difference = sumDecimals(["100", `-${total}`], 2);
  const canSave = !anyInvalid && comparison === 0 && !busy;

  async function save(payload: Array<{ assetType: AssetType; targetPct: string }>) {
    setBusy(true);
    setError(null);
    try {
      await onSave(payload);
    } catch (failure) {
      setError(
        failure instanceof Error ? failure.message : "Could not save the targets. Try again.",
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        if (canSave) {
          void save(
            entered.map(({ type }) => ({ assetType: type, targetPct: values[type].trim() })),
          );
        }
      }}
      noValidate
      className="flex flex-col gap-4"
    >
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        {TYPES.map(({ type, label }) => {
          const text = values[type].trim();
          const invalid = text !== "" && !isValid(text);
          return (
            <Field
              key={type}
              id={`target-${type}`}
              label={label}
              error={
                invalid
                  ? "Enter a whole or decimal number up to 100, with at most 2 decimals"
                  : undefined
              }
            >
              <Input
                id={`target-${type}`}
                inputMode="decimal"
                autoComplete="off"
                invalid={invalid}
                aria-describedby={`target-${type}-message`}
                value={values[type]}
                onChange={(event) => setValues({ ...values, [type]: event.target.value })}
              />
            </Field>
          );
        })}
      </div>
      <p className="text-sm" aria-live="polite">
        <span className="font-medium">Total {total}%</span>
        {" · "}
        {comparison === 0 && <span className="text-gain">All 100.00% assigned</span>}
        {comparison < 0 && <span>{difference}% left to assign</span>}
        {comparison > 0 && (
          <span className="text-loss">{difference.replace("-", "")}% over 100.00%</span>
        )}
      </p>
      {error && (
        <p role="alert" className="text-sm text-loss">
          {error}
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" variant="primary" disabled={!canSave}>
          Save targets
        </Button>
        {initial.length > 0 && (
          <Button
            disabled={busy}
            onClick={() => {
              setValues({ STOCK: "", ETF: "", MUTUAL_FUND: "", CRYPTO: "" });
              void save([]);
            }}
          >
            Clear targets
          </Button>
        )}
      </div>
    </form>
  );
}
