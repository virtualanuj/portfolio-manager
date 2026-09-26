import type { TransactionFormValues } from "./schemas";
import type { Transaction } from "./types";

/** Converts form strings to the API body; a split sends its ratio, everything else quantity and price. */
export function toTransactionRequest(values: TransactionFormValues) {
  const base = {
    accountId: values.accountId,
    instrumentId: values.instrumentId,
    type: values.type,
    tradeDate: values.tradeDate,
    ...(values.note && { note: values.note }),
  };
  if (values.type === "SPLIT") {
    return {
      ...base,
      splitNumerator: Number(values.splitNumerator),
      splitDenominator: Number(values.splitDenominator),
    };
  }
  return { ...base, quantity: values.quantity, unitPrice: values.unitPrice };
}

export function toFormValues(transaction: Transaction): TransactionFormValues {
  return {
    accountId: transaction.accountId,
    instrumentId: transaction.instrumentId,
    type: transaction.type,
    tradeDate: transaction.tradeDate,
    quantity: transaction.quantity ?? "",
    unitPrice: transaction.unitPrice ?? "",
    splitNumerator: transaction.splitNumerator?.toString() ?? "",
    splitDenominator: transaction.splitDenominator?.toString() ?? "",
    note: transaction.note ?? "",
  };
}
