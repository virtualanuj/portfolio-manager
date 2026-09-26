"use client";

import { useState } from "react";

import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";

type Props = {
  open: boolean;
  title: string;
  message: string;
  confirmLabel: string;
  onConfirm: () => Promise<void>;
  onCancel: () => void;
};

/** In-page confirmation for destructive actions; shows why the action failed, if it does. */
export function ConfirmDialog({ open, title, message, confirmLabel, onConfirm, onCancel }: Props) {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function confirm() {
    setBusy(true);
    setError(null);
    try {
      await onConfirm();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Something went wrong. Try again.");
    } finally {
      setBusy(false);
    }
  }

  function cancel() {
    setError(null);
    onCancel();
  }

  return (
    <Dialog open={open} onClose={cancel} title={title}>
      <p className="text-sm">{message}</p>
      {error && (
        <p role="alert" className="mt-3 text-sm text-loss">
          {error}
        </p>
      )}
      <div className="mt-5 flex justify-end gap-2">
        <Button onClick={cancel}>Cancel</Button>
        <Button variant="danger" onClick={confirm} disabled={busy}>
          {confirmLabel}
        </Button>
      </div>
    </Dialog>
  );
}
