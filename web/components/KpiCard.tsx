import type { ReactNode } from "react";

import { Card } from "@/components/ui/Card";
import { cn } from "@/lib/cn";

type Props = { label: string; children: ReactNode; large?: boolean };

export function KpiCard({ label, children, large }: Props) {
  return (
    <Card className="flex flex-col gap-2">
      <h2 className="text-sm text-muted">{label}</h2>
      <p className={cn("font-semibold", large ? "text-4xl md:text-5xl" : "text-2xl")}>{children}</p>
    </Card>
  );
}
