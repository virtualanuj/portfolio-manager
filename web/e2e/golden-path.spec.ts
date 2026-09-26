import { expect, test } from "@playwright/test";

import {
  addAccount,
  addInstrument,
  addTransaction,
  refresh,
  resetBackend,
  scriptPrices,
  setManualPrice,
  today,
} from "./fixtures";

test.beforeEach(async ({ request }) => {
  await resetBackend(request);
});

test("golden path: enter data, refresh, and read the dashboard, holdings, allocation and history", async ({
  page,
  request,
}) => {
  await scriptPrices(request, {
    yahoo: { prices: { VTI: { price: "120", prevPrice: "118", priceDate: today() } } },
    coingecko: { prices: { bitcoin: { price: "60000", prevPrice: "59000", priceDate: today() } } },
  });

  await addAccount(page, "Brokerage", "Brokerage");
  await addInstrument(page, { symbol: "VTI", assetType: "ETF" });
  await addInstrument(page, { symbol: "BTC", assetType: "Crypto", sourceId: "bitcoin" });
  await addInstrument(page, {
    symbol: "VTSAX",
    assetType: "Mutual fund",
    priceSource: "Manual (I enter the price)",
  });
  await setManualPrice(page, "VTSAX", "118.4");

  await addTransaction(page, {
    account: "Brokerage",
    symbol: "VTI",
    type: "Buy",
    date: "2026-01-05",
    quantity: "10",
    price: "100",
  });
  await addTransaction(page, {
    account: "Brokerage",
    symbol: "VTI",
    type: "Buy",
    date: "2026-02-05",
    quantity: "10",
    price: "120",
  });
  await addTransaction(page, {
    account: "Brokerage",
    symbol: "VTI",
    type: "Sell",
    date: "2026-03-05",
    quantity: "4",
    price: "130",
  });
  await addTransaction(page, {
    account: "Brokerage",
    symbol: "VTI",
    type: "Split",
    date: "2026-04-05",
    ratio: ["2", "1"],
  });
  await addTransaction(page, {
    account: "Brokerage",
    symbol: "BTC",
    type: "Buy",
    date: "2026-01-07",
    quantity: "0.5",
    price: "40000",
  });
  await addTransaction(page, {
    account: "Brokerage",
    symbol: "VTSAX",
    type: "Buy",
    date: "2026-01-08",
    quantity: "20",
    price: "50",
  });

  await refresh(page);

  // Dashboard: 3840 + 30000 + 2368 = 36208 value on 22800 cost basis
  await page.goto("/");
  await expect(page.getByText("$36,208.00")).toBeVisible();
  await expect(page.getByText("$22,800.00")).toBeVisible();
  await expect(page.getByText("+$13,408.00")).toBeVisible();
  await expect(page.getByText("+58.81%")).toBeVisible();

  // Holdings: per-position gain/loss (VTI 32 shares after the 2:1 split, basis 1800)
  await page.goto("/holdings");
  const vti = page.getByRole("row", { name: /VTI/ });
  await expect(vti).toContainText("32");
  await expect(vti).toContainText("$56.25");
  await expect(vti).toContainText("+$2,040.00");
  await expect(page.getByRole("row", { name: /BTC/ })).toContainText("+$10,000.00");
  await expect(page.getByRole("row", { name: /VTSAX/ })).toContainText("+$1,368.00");

  // Allocation
  await page.goto("/allocation");
  await expect(page.getByRole("row", { name: /^ETF/ })).toContainText("10.61%");
  await expect(page.getByRole("row", { name: /^Crypto/ })).toContainText("82.85%");
  await expect(page.getByRole("row", { name: /^Mutual fund/ })).toContainText("6.54%");

  // History: exactly one point, and a second refresh the same day keeps it at one
  await page.goto("/history");
  await expect(page.locator('.recharts-dot[name="Value"]')).toHaveCount(1);
  await refresh(page);
  await page.goto("/history");
  await expect(page.locator('.recharts-dot[name="Value"]')).toHaveCount(1);
});
