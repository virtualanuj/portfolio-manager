import { expect, type APIRequestContext, type Page } from "@playwright/test";

/** Today as YYYY-MM-DD in local time, the date the stub prices are stamped with. */
export function today(): string {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/** Empties the e2e database and clears the price stubs. Only exists under the e2e profile. */
export async function resetBackend(request: APIRequestContext) {
  const response = await request.post("/api/e2e/reset");
  expect(response.status(), "e2e reset").toBe(204);
}

type Script = {
  yahoo?: { unreachable?: boolean; prices?: Record<string, StubPrice> };
  coingecko?: { unreachable?: boolean; prices?: Record<string, StubPrice> };
};
type StubPrice = { price?: string; prevPrice?: string; priceDate?: string; error?: string };

export async function scriptPrices(request: APIRequestContext, script: Script) {
  const response = await request.put("/api/e2e/prices", { data: script });
  expect(response.status(), "e2e price script").toBe(204);
}

export async function addAccount(page: Page, name: string, type: string) {
  await page.goto("/settings");
  await page.getByRole("button", { name: "Add account" }).click();
  const dialog = page.getByRole("dialog", { name: "Add account" });
  await dialog.getByLabel("Name").fill(name);
  await dialog.getByLabel("Type").selectOption({ label: type });
  await dialog.getByRole("button", { name: "Save account" }).click();
  await expect(page.getByRole("cell", { name, exact: true }).first()).toBeVisible();
}

export async function addInstrument(
  page: Page,
  options: { symbol: string; assetType: string; priceSource?: string; sourceId?: string },
) {
  await page.goto("/settings");
  await page.getByRole("button", { name: "Add instrument" }).click();
  const dialog = page.getByRole("dialog", { name: "Add instrument" });
  await dialog.getByLabel("Symbol").fill(options.symbol);
  await dialog.getByLabel("Asset type").selectOption({ label: options.assetType });
  if (options.priceSource) {
    await dialog.getByLabel("Price source").selectOption({ label: options.priceSource });
  }
  if (options.sourceId) await dialog.getByLabel("Source id").fill(options.sourceId);
  await dialog.getByRole("button", { name: "Save instrument" }).click();
  await expect(page.getByRole("cell", { name: options.symbol, exact: true })).toBeVisible();
}

export async function setManualPrice(page: Page, symbol: string, price: string, asOf = today()) {
  await page.goto("/settings");
  await page.getByRole("button", { name: `Set price for ${symbol}` }).click();
  const dialog = page.getByRole("dialog", { name: `Set price for ${symbol}` });
  await dialog.getByLabel("Price").fill(price);
  await dialog.getByLabel("As of").fill(asOf);
  await dialog.getByRole("button", { name: "Save price" }).click();
  await expect(dialog).toBeHidden();
}

type Trade = {
  account: string;
  symbol: string;
  type: "Buy" | "Sell" | "Split" | "Dividend reinvestment";
  date: string;
  quantity?: string;
  price?: string;
  ratio?: [string, string];
};

export async function addTransaction(page: Page, trade: Trade) {
  await page.goto("/transactions");
  await page.getByRole("button", { name: "Add transaction" }).click();
  const dialog = page.getByRole("dialog", { name: "Add transaction" });
  await dialog.getByLabel("Type").selectOption({ label: trade.type });
  await dialog.getByLabel("Account").selectOption({ label: trade.account });
  await dialog.getByLabel("Instrument").selectOption({ label: trade.symbol });
  await dialog.getByLabel("Date").fill(trade.date);
  if (trade.ratio) {
    await dialog.getByLabel("Split ratio, new shares").fill(trade.ratio[0]);
    await dialog.getByLabel("Split ratio, old shares").fill(trade.ratio[1]);
  } else {
    await dialog.getByLabel("Quantity").fill(trade.quantity ?? "");
    await dialog.getByLabel("Price per unit").fill(trade.price ?? "");
  }
  await dialog.getByRole("button", { name: "Save transaction" }).click();
  await expect(page.getByText("Transaction saved")).toBeVisible();
  await expect(dialog).toBeHidden();
}

/** Clicks the top-bar Refresh button and waits for the run to finish. */
export async function refresh(page: Page) {
  const button = page
    .getByRole("banner")
    .getByRole("button", { name: /refresh prices|refreshing/i });
  await button.click();
  await expect(
    page.getByRole("banner").getByRole("button", { name: "Refresh prices" }),
  ).toBeEnabled({
    timeout: 20_000,
  });
}

/** Direct API calls for setup that is not the point of a test; the UI paths are covered elsewhere. */
export async function seedAccount(request: APIRequestContext, name: string, type = "BROKERAGE") {
  const response = await request.post("/api/accounts", { data: { name, type } });
  expect(response.status()).toBe(201);
  return ((await response.json()) as { id: string }).id;
}

export async function seedInstrument(
  request: APIRequestContext,
  data: { symbol: string; assetType: string; priceSource?: string; sourceId?: string },
) {
  const response = await request.post("/api/instruments", { data });
  expect(response.status()).toBe(201);
  return ((await response.json()) as { id: string }).id;
}

export async function seedBuy(
  request: APIRequestContext,
  data: {
    accountId: string;
    instrumentId: string;
    quantity: string;
    unitPrice: string;
    tradeDate?: string;
  },
) {
  const response = await request.post("/api/transactions", {
    data: { type: "BUY", tradeDate: "2026-01-05", ...data },
  });
  expect(response.status()).toBe(201);
}

export async function setPrice(request: APIRequestContext, instrumentId: string, price: string) {
  const response = await request.put(`/api/instruments/${instrumentId}/manual-price`, {
    data: { price, asOf: today() },
  });
  expect(response.status()).toBe(200);
}
