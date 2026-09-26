type Props = { stalePositions: number; unpricedPositions: number };

function plural(count: number, one: string, many: string): string {
  return `${count} ${count === 1 ? one : many}`;
}

/** Warns when totals rest on old prices or leave out positions, so they are never silently wrong. */
export function PricingBanner({ stalePositions, unpricedPositions }: Props) {
  if (stalePositions === 0 && unpricedPositions === 0) return null;
  const parts: string[] = [];
  if (stalePositions > 0) {
    parts.push(
      plural(stalePositions, "position uses a stale price", "positions use a stale price"),
    );
  }
  if (unpricedPositions > 0) {
    parts.push(
      `${plural(unpricedPositions, "position has no price", "positions have no price")} and ${
        unpricedPositions === 1 ? "is" : "are"
      } left out of the totals`,
    );
  }
  return (
    <div
      role="status"
      aria-label="Pricing warning"
      className="rounded-panel bg-warn-bg px-4 py-3 text-sm text-warn"
    >
      {parts.join("; ")}. Refresh prices, or set a manual price in Settings.
    </div>
  );
}
