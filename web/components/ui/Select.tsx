import { forwardRef, type SelectHTMLAttributes } from "react";

import { cn } from "@/lib/cn";

type Props = SelectHTMLAttributes<HTMLSelectElement> & { invalid?: boolean };

export const Select = forwardRef<HTMLSelectElement, Props>(function Select(
  { className, invalid, ...rest },
  ref,
) {
  return (
    <select
      ref={ref}
      aria-invalid={invalid || undefined}
      className={cn(
        "h-9 w-full rounded-control border bg-surface px-2.5 text-sm text-ink",
        invalid ? "border-loss" : "border-line",
        className,
      )}
      {...rest}
    />
  );
});
