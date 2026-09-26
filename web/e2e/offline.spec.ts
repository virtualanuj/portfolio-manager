import { expect, test } from "@playwright/test";

import {
  addTransaction,
  refresh,
  resetBackend,
  scriptPrices,
  seedAccount,
  seedBuy,
  seedInstrument,
  setPrice,
} from "./fixtures";

test.beforeEach(async ({ request }) => {
  await resetBackend(request);
  await scriptPrices(request, { yahoo: { unreachable: true }, coingecko: { unreachable: true } });
});

test("with every provider unreachable, everything but price fetching works and manual prices still value", async ({
  page,
  request,
}) => {
  const accountId = await seedAccount(request, "Retirement", "RETIREMENT");
  const fund = await seedInstrument(request, {
    symbol: "VTSAX",
    assetType: "MUTUAL_FUND",
    priceSource: "MANUAL",
  });
  const etf = await seedInstrument(request, { symbol: "VTI", assetType: "ETF" });
  await seedBuy(request, { accountId, instrumentId: fund, quantity: "20", unitPrice: "50" });
  await seedBuy(request, { accountId, instrumentId: etf, quantity: "10", unitPrice: "100" });
  await setPrice(request, fund, "118.4");

  await page.goto("/");
  await refresh(page);
  await expect(page.getByText(/prices could not be refreshed/i)).toBeVisible();

  // Manual price still values; the never-priced feed instrument is reported, not hidden
  await page.goto("/");
  await expect(page.getByText("$2,368.00")).toBeVisible();
  await expect(page.getByRole("status", { name: "Pricing warning" })).toContainText(
    "1 position has no price",
  );
  await page.goto("/holdings");
  await expect(page.getByRole("row", { name: /VTI/ })).toContainText("No price");

  // Data entry still works offline
  await addTransaction(page, {
    account: "Retirement",
    symbol: "VTSAX",
    type: "Buy",
    date: "2026-02-01",
    quantity: "5",
    price: "60",
  });
  await page.goto("/holdings");
  await expect(page.getByRole("row", { name: /VTSAX/ })).toContainText("25");
});
