import type { FieldValues, Path, UseFormSetError } from "react-hook-form";

import { ApiError } from "./api";

/**
 * Shows a failed submit next to the fields it names, or in a form-level message otherwise, so
 * server rules (a duplicate name, an oversell) read where the user is looking.
 */
export function showServerError<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  knownFields: readonly string[],
): void {
  if (!(error instanceof ApiError)) {
    setError("root", { message: "Something went wrong. Try again." });
    return;
  }
  let placed = false;
  for (const [field, message] of Object.entries(error.fieldErrors)) {
    if (knownFields.includes(field)) {
      setError(field as Path<T>, { message });
      placed = true;
    }
  }
  if (!placed) setError("root", { message: error.message });
}
