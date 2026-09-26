import type { HTMLAttributes } from "react";

import { cn } from "@/lib/cn";

type Tone = "neutral" | "warn" | "gain" | "loss";

const tones: Record<Tone, string> = {
  neutral: "bg-sunken text-muted",
  warn: "bg-warn-bg text-warn",
  gain: "bg-gain-bg text-gain",
  loss: "bg-loss-bg text-loss",
};

export function Badge({
  tone = "neutral",
  className,
  ...rest
}: HTMLAttributes<HTMLSpanElement> & { tone?: Tone }) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium",
        tones[tone],
        className,
      )}
      {...rest}
    />
  );
}
