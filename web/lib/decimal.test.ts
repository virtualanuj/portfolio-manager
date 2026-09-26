import { describe, expect, it } from "vitest";

import { compareDecimals } from "./decimal";

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
