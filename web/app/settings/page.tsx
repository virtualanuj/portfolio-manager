import Link from "next/link";

import { AccountsSection } from "@/components/AccountsSection";
import { ExportPanel } from "@/components/ExportPanel";
import { InstrumentsSection } from "@/components/InstrumentsSection";

export default function SettingsPage() {
  return (
    <div className="flex flex-col gap-10">
      <h1 className="text-2xl font-semibold">Settings</h1>
      <AccountsSection />
      <InstrumentsSection />
      <section aria-labelledby="targets-link-heading" className="flex flex-col gap-2">
        <h2 id="targets-link-heading" className="text-lg font-semibold">
          Target allocation
        </h2>
        <p className="text-sm text-muted">Choose the share you want each asset type to have.</p>
        <Link href="/allocation#targets" className="text-sm font-medium text-accent underline">
          Edit target allocation
        </Link>
      </section>
      <ExportPanel />
    </div>
  );
}
