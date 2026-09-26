import { describe, expect, it } from "vitest";

import {
  formatCost,
  formatDate,
  formatMoney,
  formatPct,
  formatPoints,
  formatQty,
  formatUnitPrice,
  signOf,
} from "./format";
import type { DecimalString } from "./types";

const d = (value: string) => value as DecimalString;

describe("formatMoney", () => {
  it("groups thousands and shows two decimals", () => {
    expect(formatMoney(d("1234.5"))).toBe("$1,234.50");
    expect(formatMoney(d("0"))).toBe("$0.00");
  });

  it("puts the minus sign before the currency symbol", () => {
    expect(formatMoney(d("-1234.567"))).toBe("-$1,234.57");
  });

  it("rounds half away from zero like the API", () => {
    expect(formatMoney(d("0.005"))).toBe("$0.01");
    expect(formatMoney(d("-0.005"))).toBe("-$0.01");
    expect(formatMoney(d("1.004"))).toBe("$1.00");
  });

  it("shows a dash for missing values", () => {
    expect(formatMoney(null)).toBe("—");
  });

  it("does not show a negative zero", () => {
    expect(formatMoney(d("-0.001"))).toBe("$0.00");
  });

  it("keeps values above 2^53 exact", () => {
    expect(formatMoney(d("12345678901234567890.1234"))).toBe("$12,345,678,901,234,567,890.12");
  });

  it("can show an explicit plus sign", () => {
    expect(formatMoney(d("20"), { signed: true })).toBe("+$20.00");
    expect(formatMoney(d("-20"), { signed: true })).toBe("-$20.00");
  });
});

describe("formatQty", () => {
  it("keeps every digit of an 8 decimal crypto quantity", () => {
    expect(formatQty(d("0.12345678"))).toBe("0.12345678");
    expect(formatQty(d("0.00000001"))).toBe("0.00000001");
  });

  it("drops trailing zeros and groups thousands", () => {
    expect(formatQty(d("10.00000000"))).toBe("10");
    expect(formatQty(d("1234.50000000"))).toBe("1,234.5");
  });

  it("shows a dash for missing values", () => {
    expect(formatQty(null)).toBe("—");
  });
});

describe("formatPct", () => {
  it("shows two decimals and a percent sign", () => {
    expect(formatPct(d("16.9500"))).toBe("16.95%");
    expect(formatPct(d("-3.333333"))).toBe("-3.33%");
  });

  it("can show an explicit plus sign and handles missing values", () => {
    expect(formatPct(d("5"), { signed: true })).toBe("+5.00%");
    expect(formatPct(null)).toBe("—");
  });
});

describe("formatDate", () => {
  it("formats an ISO date without time zone shifts", () => {
    expect(formatDate("2026-09-26")).toBe("Sep 26, 2026");
    expect(formatDate("2026-01-01")).toBe("Jan 1, 2026");
  });

  it("shows a dash for missing dates", () => {
    expect(formatDate(null)).toBe("—");
  });
});

describe("signOf", () => {
  it("classifies decimal strings", () => {
    expect(signOf(d("1.5"))).toBe("positive");
    expect(signOf(d("-0.0001"))).toBe("negative");
    expect(signOf(d("0.0000"))).toBe("zero");
    expect(signOf(null)).toBe("zero");
  });
});

describe("formatUnitPrice", () => {
  it("shows at least two decimals and keeps the ones a small price needs", () => {
    expect(formatUnitPrice(d("100"))).toBe("$100.00");
    expect(formatUnitPrice(d("0.00012000"))).toBe("$0.00012");
    expect(formatUnitPrice(d("65000.50000000"))).toBe("$65,000.50");
    expect(formatUnitPrice(null)).toBe("—");
  });
});

describe("formatPoints", () => {
  it("shows percentage points with a sign", () => {
    expect(formatPoints(d("15"), { signed: true })).toBe("+15.00 pts");
    expect(formatPoints(d("-2.5"), { signed: true })).toBe("-2.50 pts");
    expect(formatPoints(null)).toBe("—");
  });
});

describe("formatCost", () => {
  it("rounds to cents from one dollar up", () => {
    expect(formatCost(d("119.0410"))).toBe("$119.04");
    expect(formatCost(d("62333.3333"))).toBe("$62,333.33");
  });

  it("keeps the digits a small cost needs", () => {
    expect(formatCost(d("0.1800"))).toBe("$0.18");
    expect(formatCost(d("0.00012"))).toBe("$0.00012");
    expect(formatCost(null)).toBe("—");
  });
});
