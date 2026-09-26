import { Badge } from "@/components/ui/Badge";
import { formatDate } from "@/lib/format";
import type { PriceStatus } from "@/lib/types";

type Props = { status: PriceStatus; asOf: string | null };

/** Says when a price cannot be taken at face value: stale feed, manual, or old manual. */
export function StaleBadge({ status, asOf }: Props) {
  switch (status) {
    case "STALE":
      return <Badge tone="warn">Stale, as of {formatDate(asOf)}</Badge>;
    case "MANUAL_STALE":
      return <Badge tone="warn">Manual, stale, as of {formatDate(asOf)}</Badge>;
    case "MANUAL":
      return <Badge>Manual, as of {formatDate(asOf)}</Badge>;
    default:
      return null;
  }
}
