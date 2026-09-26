import { describe, expect, it } from "vitest";

import { compareDecimals, sumDecimals } from "./decimal";

describe("compareDecimals", () => {
  it("orders by numeric value, not by text", () => {
    expect(compareDecimals("9", "10")).toBeLessThan(0);
    expect(compareDecimals("10.5", "9.99")).toBeGreaterThan(0);
    expect(compareDecimals("1.50", "1.5")).toBe(0);
  });

  it("handles negatives and values beyond 2^53", () => {
    expect(compareDecimals("-5", "3")).toBeLessThan(0);
    expect(compareDecimals("-5", "-10")).toBeGreaterThan(0);
    expect(compareDecimals("12345678901234567891", "12345678901234567890")).toBeGreaterThan(0);
  });
});

describe("sumDecimals", () => {
  it("adds exactly, without floating point drift", () => {
    expect(sumDecimals(["0.1", "0.2"])).toBe("0.3");
    expect(sumDecimals(["33.33", "33.33", "33.34"], 2)).toBe("100.00");
  });

  it("keeps at least the requested scale and handles negatives and no input", () => {
    expect(sumDecimals(["60", "40"], 2)).toBe("100.00");
    expect(sumDecimals(["5", "-7.5"])).toBe("-2.5");
    expect(sumDecimals([], 2)).toBe("0.00");
  });
});
