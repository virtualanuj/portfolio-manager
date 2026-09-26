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

const PAGES = [
  "/",
  "/holdings",
  "/history",
  "/allocation",
  "/transactions",
  "/import",
  "/settings",
];

test.beforeEach(async ({ request }) => {
  await resetBackend(request);
  await scriptPrices(request, {
    yahoo: { prices: { VTI: { price: "120", prevPrice: "118", priceDate: today() } } },
  });
  const account = await seedAccount(request, "Brokerage");
  const vti = await seedInstrument(request, { symbol: "VTI", assetType: "ETF" });
  await seedBuy(request, {
    accountId: account,
    instrumentId: vti,
    quantity: "10",
    unitPrice: "100",
  });
});

test("the browser only ever talks to its own origin", async ({ page, baseURL }) => {
  const own = new URL(baseURL ?? "").origin;
  const foreign: string[] = [];
  page.on("request", (request) => {
    const url = request.url();
    if (url.startsWith("data:") || url.startsWith("blob:")) return;
    if (new URL(url).origin !== own) foreign.push(url);
  });

  for (const path of PAGES) {
    await page.goto(path);
    await page.waitForLoadState("networkidle");
  }
  await page.goto("/");
  await refresh(page); // a price refresh must not make the browser call a feed itself
  await page.getByRole("button", { name: /switch to (dark|light) theme/i }).click();
  await page.goto("/history");
  await page.waitForLoadState("networkidle");

  expect(foreign).toEqual([]);
});

test("pages load no third-party scripts, stylesheets, fonts or frames", async ({
  page,
  baseURL,
}) => {
  const own = new URL(baseURL ?? "").origin;
  await page.goto("/");
  const external = await page.evaluate((origin) => {
    const urls = [
      ...document.querySelectorAll("script[src], link[href], iframe[src], img[src]"),
    ].map((element) => element.getAttribute("src") ?? element.getAttribute("href") ?? "");
    return urls.filter((url) => /^https?:\/\//.test(url) && new URL(url).origin !== origin);
  }, own);

  expect(external).toEqual([]);
});
