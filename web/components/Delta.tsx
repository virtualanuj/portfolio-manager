import { formatMoney, formatPct, signOf } from "@/lib/format";
import type { DecimalString } from "@/lib/types";
import { cn } from "@/lib/cn";

type Props = { amount: DecimalString | null; percent?: DecimalString | null };

/**
 * A gain or loss. The sign and arrow carry the meaning; colour only reinforces it, so it reads the
 * same without colour vision.
 */
export function Delta({ amount, percent = null }: Props) {
  if (amount === null) return <span>—</span>;
  const sign = signOf(amount);
  const arrow = sign === "positive" ? "▲" : sign === "negative" ? "▼" : "•";
  return (
    <span
      className={cn(
        "inline-flex flex-wrap items-baseline gap-x-1.5 whitespace-nowrap",
        sign === "positive" && "text-gain",
        sign === "negative" && "text-loss",
      )}
    >
      <span aria-hidden="true">{arrow}</span>
      <span>{formatMoney(amount, { signed: true })}</span>
      {percent !== null && <span>({formatPct(percent, { signed: true })})</span>}
    </span>
  );
}

/** A percentage gain or loss on its own, with the same sign and arrow rules as {@link Delta}. */
export function PctDelta({ value }: { value: DecimalString | null }) {
  if (value === null) return <span>—</span>;
  const sign = signOf(value);
  const arrow = sign === "positive" ? "▲" : sign === "negative" ? "▼" : "•";
  return (
    <span
      className={cn(
        "inline-flex flex-wrap items-baseline gap-x-1.5 whitespace-nowrap",
        sign === "positive" && "text-gain",
        sign === "negative" && "text-loss",
      )}
    >
      <span aria-hidden="true">{arrow}</span>
      <span>{formatPct(value, { signed: true })}</span>
    </span>
  );
}
