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
        "inline-flex items-baseline gap-1.5",
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
