"use client";

import { useState, type ChangeEvent, type DragEvent } from "react";

import { cn } from "@/lib/cn";

export const MAX_UPLOAD_BYTES = 2 * 1024 * 1024;

type Props = { onFile: (file: File) => void; disabled?: boolean };

/** A file picker that also takes a dropped file. Checks the size before anything is uploaded. */
export function ImportDropzone({ onFile, disabled }: Props) {
  const [error, setError] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);

  function accept(file: File | undefined) {
    if (!file) return;
    if (file.size > MAX_UPLOAD_BYTES) {
      setError("The file is larger than 2 MB. Split it into smaller files and upload each one.");
      return;
    }
    setError(null);
    onFile(file);
  }

  function onChange(event: ChangeEvent<HTMLInputElement>) {
    accept(event.target.files?.[0]);
    event.target.value = "";
  }

  function onDrop(event: DragEvent<HTMLLabelElement>) {
    event.preventDefault();
    setDragging(false);
    if (!disabled) accept(event.dataTransfer.files[0]);
  }

  return (
    <div className="flex flex-col gap-2">
      <label
        onDragOver={(event) => {
          event.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        className={cn(
          "flex cursor-pointer flex-col items-center gap-2 rounded-panel border-2 border-dashed border-line p-8 text-center",
          "focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-accent",
          dragging && "border-accent bg-sunken",
          disabled && "cursor-not-allowed opacity-60",
        )}
      >
        <span className="font-medium">Choose a CSV file</span>
        <span className="text-sm text-muted">or drop it here. Up to 2 MB and 5,000 rows.</span>
        <input
          type="file"
          accept=".csv,text/csv"
          className="sr-only"
          disabled={disabled}
          onChange={onChange}
        />
      </label>
      {error && (
        <p role="alert" className="text-sm text-loss">
          {error}
        </p>
      )}
    </div>
  );
}
