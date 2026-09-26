import type { TxnType } from "./types";

export type TransactionFilters = {
  accountId: string;
  instrumentId: string;
  type: "" | TxnType;
  from: string;
  to: string;
};

export const NO_FILTERS: TransactionFilters = {
  accountId: "",
  instrumentId: "",
  type: "",
  from: "",
  to: "",
};

export const PAGE_SIZE = 25;

/** Builds the list URL, leaving out every filter that is not set. */
export function transactionsUrl(filters: TransactionFilters, page: number): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) {
    if (value) params.set(key, value);
  }
  params.set("page", String(page));
  params.set("size", String(PAGE_SIZE));
  return `/api/transactions?${params.toString()}`;
}
