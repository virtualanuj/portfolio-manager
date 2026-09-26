import { describe, expect, it } from "vitest";

import { NO_FILTERS, transactionsUrl } from "./transactionQuery";

describe("transactionsUrl", () => {
  it("only sends paging when no filter is set", () => {
    expect(transactionsUrl(NO_FILTERS, 0)).toBe("/api/transactions?page=0&size=25");
  });

  it("includes each filter that is set", () => {
    const url = transactionsUrl(
      { accountId: "a1", instrumentId: "i1", type: "SELL", from: "2026-01-01", to: "2026-02-01" },
      2,
    );

    expect(url).toBe(
      "/api/transactions?accountId=a1&instrumentId=i1&type=SELL&from=2026-01-01&to=2026-02-01&page=2&size=25",
    );
  });
});
