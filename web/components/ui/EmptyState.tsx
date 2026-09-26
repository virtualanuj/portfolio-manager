import type { ReactNode } from "react";

type Props = { title: string; description?: string; action?: ReactNode };

/** An empty screen says what is missing and offers the next step. */
export function EmptyState({ title, description, action }: Props) {
  return (
    <div className="flex flex-col items-start gap-3 rounded-panel border border-dashed border-line p-8">
      <h2 className="text-lg font-semibold">{title}</h2>
      {description && <p className="max-w-prose text-sm text-muted">{description}</p>}
      {action}
    </div>
  );
}
