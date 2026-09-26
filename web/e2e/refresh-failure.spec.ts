import { expect, test } from "@playwright/test";

import {
  refresh,
  resetBackend,
  scriptPrices,
  seedAccount,
  seedBuy,
  seedInstrument,
  today,
} from "./fixtures";

test.beforeEach(async ({ request }) => {
  await resetBackend(request);
  const accountId = await seedAccount(request, "Brokerage");
  const vti = await seedInstrument(request, { symbol: "VTI", assetType: "ETF" });
  const btc = await seedInstrument(request, {
    symbol: "BTC",
    assetType: "CRYPTO",
    sourceId: "bitcoin",
  });
  await seedBuy(request, { accountId, instrumentId: vti, quantity: "10", unitPrice: "100" });
  await seedBuy(request, { accountId, instrumentId: btc, quantity: "0.5", unitPrice: "40000" });
});

test("a failing feed keeps the last prices, marks them stale and warns without breaking any page", async ({
  page,
  request,
}) => {
  await scriptPrices(request, {
    yahoo: { prices: { VTI: { price: "120", prevPrice: "118", priceDate: today() } } },
    coingecko: { prices: { bitcoin: { price: "60000", prevPrice: "59000", priceDate: today() } } },
  });
  await page.goto("/");
  await refresh(page);
  await page.goto("/");
  await expect(page.getByText("$31,200.00")).toBeVisible();

  // Yahoo now fails for VTI; CoinGecko still answers, so the run is partial
  await scriptPrices(request, {
    yahoo: { prices: { VTI: { error: "Yahoo returned HTTP 500" } } },
    coingecko: { prices: { bitcoin: { price: "61000", prevPrice: "60000", priceDate: today() } } },
  });
  await refresh(page);

  const warning = page.getByRole("status", { name: "Refresh warning" });
  await expect(warning).toContainText("VTI");
  await expect(warning).toContainText("Yahoo returned HTTP 500");
  await expect(warning).not.toContainText("BTC");

  await page.goto("/holdings");
  const vti = page.getByRole("row", { name: /VTI/ });
  await expect(vti).toContainText("$120.00");
  await expect(vti).toContainText(/Stale, as of/);
  await expect(page.getByRole("row", { name: /BTC/ })).toContainText("$61,000.00");

  // The dashboard still renders, with the last known VTI price and the fresh BTC price
  await page.goto("/");
  await expect(page.getByText("$31,700.00")).toBeVisible();
  await expect(page.getByRole("status", { name: "Pricing warning" })).toContainText(
    "1 position uses a stale price",
  );
});

test("when every fetch fails the run fails, the last prices stay and the pages keep working", async ({
  page,
  request,
}) => {
  await scriptPrices(request, {
    yahoo: { prices: { VTI: { price: "120", priceDate: today() } } },
    coingecko: { prices: { bitcoin: { price: "60000", priceDate: today() } } },
  });
  await page.goto("/");
  await refresh(page);

  await scriptPrices(request, { yahoo: { unreachable: true }, coingecko: { unreachable: true } });
  await refresh(page);

  await expect(page.getByText(/prices could not be refreshed/i)).toBeVisible();
  for (const path of ["/", "/holdings", "/history", "/allocation", "/transactions", "/settings"]) {
    await page.goto(path);
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    await expect(page.getByText(/could not load/i)).toHaveCount(0);
  }
  await page.goto("/holdings");
  await expect(page.getByRole("row", { name: /VTI/ })).toContainText("$120.00");
});
