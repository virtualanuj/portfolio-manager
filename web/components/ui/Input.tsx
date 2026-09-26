import { forwardRef, type InputHTMLAttributes } from "react";

import { cn } from "@/lib/cn";

type Props = InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean };

export const Input = forwardRef<HTMLInputElement, Props>(function Input(
  { className, invalid, ...rest },
  ref,
) {
  return (
    <input
      ref={ref}
      aria-invalid={invalid || undefined}
      className={cn(
        "h-9 w-full rounded-control border bg-surface px-3 text-sm text-ink placeholder:text-muted",
        invalid ? "border-loss" : "border-line",
        className,
      )}
      {...rest}
    />
  );
});
