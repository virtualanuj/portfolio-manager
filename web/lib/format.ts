import {
  groupThousands,
  isZero,
  parseDecimal,
  roundToScale,
  toParts,
  type ScaledDecimal,
} from "./decimal";
import type { DecimalString } from "./types";

/** Display formatters. They take decimal strings and never do arithmetic. */

const DASH = "—";
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

type SignOption = { signed?: boolean };

function withSign(value: ScaledDecimal, text: string, signed: boolean | undefined): string {
  if (isZero(value)) return text;
  if (value.negative) return `-${text}`;
  return signed ? `+${text}` : text;
}

function fixed(value: DecimalString, scale: number): { rounded: ScaledDecimal; text: string } {
  const rounded = roundToScale(parseDecimal(value), scale);
  const { integer, fraction } = toParts(rounded);
  return { rounded, text: `${groupThousands(integer)}${scale > 0 ? `.${fraction}` : ""}` };
}

export function formatMoney(value: DecimalString | null, options: SignOption = {}): string {
  if (value === null) return DASH;
  const { rounded, text } = fixed(value, 2);
  return withSign(rounded, `$${text}`, options.signed);
}

/** Quantities keep every significant digit and drop trailing zeros. */
export function formatQty(value: DecimalString | null): string {
  if (value === null) return DASH;
  const parsed = parseDecimal(value);
  const { integer, fraction } = toParts(parsed);
  const trimmed = fraction.replace(/0+$/, "");
  const text = `${groupThousands(integer)}${trimmed ? `.${trimmed}` : ""}`;
  return withSign(parsed, text, false);
}

export function formatPct(value: DecimalString | null, options: SignOption = {}): string {
  if (value === null) return DASH;
  const { rounded, text } = fixed(value, 2);
  return withSign(rounded, `${text}%`, options.signed);
}

/** Formats an ISO date (YYYY-MM-DD) as "Sep 26, 2026" without any time zone conversion. */
export function formatDate(iso: string | null): string {
  if (iso === null) return DASH;
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (!match) return iso;
  const month = MONTHS[Number(match[2]) - 1];
  return month ? `${month} ${Number(match[3])}, ${match[1]}` : iso;
}

export function signOf(value: DecimalString | null): "positive" | "negative" | "zero" {
  if (value === null) return "zero";
  const parsed = parseDecimal(value);
  if (isZero(parsed)) return "zero";
  return parsed.negative ? "negative" : "positive";
}

/** Today's date in the browser's time zone as YYYY-MM-DD, for date inputs. */
export function todayIso(now: Date = new Date()): string {
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${month}-${day}`;
}
