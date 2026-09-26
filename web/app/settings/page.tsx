import { AccountsSection } from "@/components/AccountsSection";
import { InstrumentsSection } from "@/components/InstrumentsSection";

export default function SettingsPage() {
  return (
    <div className="flex flex-col gap-10">
      <h1 className="text-2xl font-semibold">Settings</h1>
      <AccountsSection />
      <InstrumentsSection />
    </div>
  );
}
