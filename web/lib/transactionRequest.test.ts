import { describe, expect, it } from "vitest";

import type { TransactionFormValues } from "./schemas";
import { toTransactionRequest } from "./transactionRequest";

const base: TransactionFormValues = {
  accountId: "a1",
  instrumentId: "i1",
  type: "BUY",
  tradeDate: "2026-03-05",
  quantity: "10.5",
  unitPrice: "100",
  splitNumerator: "",
  splitDenominator: "",
  note: "",
};

describe("toTransactionRequest", () => {
  it("sends quantity and price as strings for a buy and leaves out empty notes", () => {
    expect(toTransactionRequest(base)).toEqual({
      accountId: "a1",
      instrumentId: "i1",
      type: "BUY",
      tradeDate: "2026-03-05",
      quantity: "10.5",
      unitPrice: "100",
    });
  });

  it("sends only the ratio for a split", () => {
    const request = toTransactionRequest({
      ...base,
      type: "SPLIT",
      splitNumerator: "2",
      splitDenominator: "1",
      quantity: "ignored",
    });

    expect(request).toEqual({
      accountId: "a1",
      instrumentId: "i1",
      type: "SPLIT",
      tradeDate: "2026-03-05",
      splitNumerator: 2,
      splitDenominator: 1,
    });
  });
});
