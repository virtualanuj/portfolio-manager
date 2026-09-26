import type { ReactNode } from "react";

type Props = {
  id: string;
  label: string;
  error?: string;
  hint?: string;
  children: ReactNode;
};

/** A label, its control and its message, linked for screen readers. Pass `id` to the control too. */
export function Field({ id, label, error, hint, children }: Props) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      {children}
      {hint && !error && (
        <p id={`${id}-message`} className="text-xs text-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${id}-message`} role="alert" className="text-xs text-loss">
          {error}
        </p>
      )}
    </div>
  );
}
