import { formatMoney } from "@/lib/format";
import type { DecimalString } from "@/lib/types";

export function Money({ value }: { value: DecimalString | null }) {
  return <span>{formatMoney(value)}</span>;
}
