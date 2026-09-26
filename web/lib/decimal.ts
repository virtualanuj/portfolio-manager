/**
 * String-based decimal helpers for display. Values never pass through `Number`, so amounts above
 * 2^53 keep every digit. Only rounding is done here; there is no arithmetic on money.
 */

export type ScaledDecimal = {
  negative: boolean;
  /** Absolute value multiplied by 10^scale. */
  units: bigint;
  scale: number;
};

const DECIMAL = /^([+-])?(\d+)(?:\.(\d+))?$/;

export function parseDecimal(value: string): ScaledDecimal {
  const match = DECIMAL.exec(value.trim());
  if (!match) throw new Error(`Not a decimal string: ${value}`);
  const fraction = match[3] ?? "";
  return {
    negative: match[1] === "-",
    units: BigInt(`${match[2] ?? "0"}${fraction}`),
    scale: fraction.length,
  };
}

/** Rounds half away from zero, matching the API's HALF_UP. */
export function roundToScale(value: ScaledDecimal, scale: number): ScaledDecimal {
  if (value.scale <= scale) {
    return { ...value, units: value.units * 10n ** BigInt(scale - value.scale), scale };
  }
  const divisor = 10n ** BigInt(value.scale - scale);
  let units = value.units / divisor;
  if ((value.units % divisor) * 2n >= divisor) units += 1n;
  return { ...value, units, scale };
}

/** Splits into integer and fraction digit strings. */
export function toParts(value: ScaledDecimal): { integer: string; fraction: string } {
  const digits = value.units.toString().padStart(value.scale + 1, "0");
  const split = digits.length - value.scale;
  return { integer: digits.slice(0, split), fraction: digits.slice(split) };
}

export function isZero(value: ScaledDecimal): boolean {
  return value.units === 0n;
}

export function groupThousands(integer: string): string {
  return integer.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
}
