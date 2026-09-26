import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

import {
  refresh,
  resetBackend,
  scriptPrices,
  seedAccount,
  seedBuy,
  seedInstrument,
  setPrice,
  today,
} from "./fixtures";

const PAGES = [
  { path: "/", heading: "Dashboard" },
  { path: "/holdings", heading: "Holdings" },
  { path: "/history", heading: "History" },
  { path: "/allocation", heading: "Allocation" },
  { path: "/transactions", heading: "Transactions" },
  { path: "/import", heading: "Import" },
  { path: "/settings", heading: "Settings" },
];

test.beforeEach(async ({ request, page }) => {
  await resetBackend(request);
  await scriptPrices(request, {
    yahoo: { prices: { VTI: { price: "120", prevPrice: "118", priceDate: today() } } },
    coingecko: { prices: { bitcoin: { price: "60000", prevPrice: "59000", priceDate: today() } } },
  });
  const account = await seedAccount(request, "Brokerage");
  const vti = await seedInstrument(request, { symbol: "VTI", assetType: "ETF" });
  const btc = await seedInstrument(request, {
    symbol: "BTC",
    assetType: "CRYPTO",
    sourceId: "bitcoin",
  });
  const fund = await seedInstrument(request, {
    symbol: "VTSAX",
    assetType: "MUTUAL_FUND",
    priceSource: "MANUAL",
  });
  await seedBuy(request, {
    accountId: account,
    instrumentId: vti,
    quantity: "10",
    unitPrice: "100",
  });
  await seedBuy(request, {
    accountId: account,
    instrumentId: btc,
    quantity: "0.5",
    unitPrice: "40000",
  });
  await seedBuy(request, {
    accountId: account,
    instrumentId: fund,
    quantity: "20",
    unitPrice: "50",
  });
  await setPrice(request, fund, "118.4");
  await request.put("/api/allocation/targets", {
    data: [
      { assetType: "ETF", targetPct: "20" },
      { assetType: "CRYPTO", targetPct: "70" },
      { assetType: "MUTUAL_FUND", targetPct: "10" },
    ],
  });
  await page.goto("/");
  await refresh(page);
});

async function seriousViolations(page: Page) {
  const results = await new AxeBuilder({ page }).analyze();
  return results.violations
    .filter((v) => v.impact === "serious" || v.impact === "critical")
    .map((v) => `${v.id} (${v.impact}): ${v.nodes.map((n) => n.target.join(" ")).join(" | ")}`);
}

for (const scheme of ["light", "dark"] as const) {
  test.describe(`${scheme} theme`, () => {
    test.use({ colorScheme: scheme });

    for (const { path, heading } of PAGES) {
      test(`${path} has no serious or critical accessibility violations`, async ({ page }) => {
        await page.goto(path);
        await expect(page.getByRole("heading", { level: 1, name: heading })).toBeVisible();
        await page.waitForTimeout(400); // let charts and data settle
        expect(await seriousViolations(page)).toEqual([]);
      });
    }

    test("the add-transaction dialog has no serious or critical violations", async ({ page }) => {
      await page.goto("/transactions");
      await page.getByRole("button", { name: "Add transaction" }).click();
      await expect(page.getByRole("dialog", { name: "Add transaction" })).toBeVisible();
      expect(await seriousViolations(page)).toEqual([]);
    });
  });
}

test("a transaction can be added with the keyboard alone", async ({ page }) => {
  await page.goto("/transactions");
  const open = page.getByRole("button", { name: "Add transaction" });
  await expect(open).toBeEnabled();
  await open.focus();
  await page.keyboard.press("Enter");

  const dialog = page.getByRole("dialog", { name: "Add transaction" });
  await expect(dialog).toBeVisible();
  // Focus starts inside the dialog; walk the fields with Tab and type or pick values
  await expect(dialog.locator(":focus")).toHaveCount(1);
  await page.keyboard.press("Tab"); // account: type-ahead picks the option
  await page.keyboard.type("Brok");
  await page.keyboard.press("Tab"); // instrument
  await page.keyboard.type("VTI");
  // A date field has four Tab stops in Chromium (month, day, year, calendar button); the default
  // of today is kept
  for (let stop = 0; stop < 4; stop++) await page.keyboard.press("Tab");
  await page.keyboard.press("Tab"); // quantity
  await page.keyboard.type("2");
  await page.keyboard.press("Tab"); // price
  await page.keyboard.type("101");
  await page.keyboard.press("Enter");

  await expect(page.getByText("Transaction saved")).toBeVisible();
  await expect(dialog).toBeHidden();
});

test("Escape closes a dialog and returns to the page", async ({ page }) => {
  await page.goto("/settings");
  await page.getByRole("button", { name: "Add account" }).click();
  const dialog = page.getByRole("dialog", { name: "Add account" });
  await expect(dialog).toBeVisible();

  await page.keyboard.press("Escape");

  await expect(dialog).toBeHidden();
});

test.describe("at 400 px", () => {
  test.use({ viewport: { width: 400, height: 800 } });

  for (const { path, heading } of PAGES) {
    test(`${path} has no page-level horizontal scroll`, async ({ page }) => {
      await page.goto(path);
      await expect(page.getByRole("heading", { level: 1, name: heading })).toBeVisible();
      await page.waitForTimeout(400);
      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth - window.innerWidth,
      );
      expect(overflow).toBeLessThanOrEqual(0);
    });
  }
});
