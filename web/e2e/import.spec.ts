import path from "node:path";

import { expect, test } from "@playwright/test";

import { resetBackend } from "./fixtures";

const SAMPLE = path.resolve(__dirname, "../public/sample-transactions.csv");

test.beforeEach(async ({ request }) => {
  await resetBackend(request);
});

test("import the sample CSV: preview, commit, and holdings match the hand calculation", async ({
  page,
}) => {
  await page.goto("/import");
  await page.getByLabel("Choose a CSV file").setInputFiles(SAMPLE);

  await expect(page.getByRole("heading", { name: "sample-transactions.csv" })).toBeVisible();
  await expect(page.getByText(/6 rows.*3 new accounts.*3 new instruments/)).toBeVisible();
  await expect(page.getByText("Will create")).toHaveCount(6);
  await page.getByRole("button", { name: "Commit import" }).click();

  await expect(page.getByText("6 transactions imported")).toBeVisible();

  await page.goto("/holdings");
  // VTI: buy 10@100, buy 10@120, sell 15, split 2:1 leaves 10 shares at $60.00
  const vti = page.getByRole("row", { name: /VTI/ });
  await expect(vti).toContainText("10");
  await expect(vti).toContainText("$60.00");
  await expect(page.getByRole("row", { name: /BTC/ })).toContainText("$40,000.00");
  await expect(page.getByRole("row", { name: /VTSAX/ })).toContainText("$50.00");
});

test("a broken file shows row errors and cannot be committed", async ({ page }, testInfo) => {
  const broken = testInfo.outputPath("broken.csv");
  await import("node:fs").then((fs) =>
    fs.writeFileSync(
      broken,
      "date,account,symbol,asset_type,type,quantity,price\n2026-01-05,Main,VTI,ETF,SELL,5,100\nnot-a-date,Main,VTI,ETF,BUY,1,1\n",
    ),
  );
  await page.goto("/import");
  await page.getByLabel("Choose a CSV file").setInputFiles(broken);

  await expect(page.getByText(/exceeds the 0 held/)).toBeVisible();
  await expect(page.getByText("Use a valid date as YYYY-MM-DD")).toBeVisible();
  await expect(page.getByRole("button", { name: "Commit import" })).toBeDisabled();
});

test("importing the same file twice flags duplicates and skips them by default", async ({
  page,
}) => {
  await page.goto("/import");
  await page.getByLabel("Choose a CSV file").setInputFiles(SAMPLE);
  await page.getByRole("button", { name: "Commit import" }).click();
  await expect(page.getByText("6 transactions imported")).toBeVisible();

  await page.getByLabel("Choose a CSV file").setInputFiles(SAMPLE);
  await expect(page.getByLabel(/include duplicate rows/i)).toBeVisible();
  await page.getByRole("button", { name: "Commit import" }).click();

  await expect(page.getByText("0 transactions imported")).toBeVisible();
  await expect(page.getByText(/6 duplicates skipped/)).toBeVisible();
});
