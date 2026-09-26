import { z } from "zod";

const DECIMAL = /^\d+(\.\d{1,8})?$/;

export const accountTypes = ["BROKERAGE", "RETIREMENT", "CRYPTO", "OTHER"] as const;
export const assetTypes = ["STOCK", "ETF", "MUTUAL_FUND", "CRYPTO"] as const;
export const priceSources = ["YAHOO", "COINGECKO", "MANUAL"] as const;

export const accountSchema = z.object({
  name: z.string().trim().min(1, "Name is required").max(100, "Use 100 characters or fewer"),
  type: z.enum(accountTypes),
});
export type AccountFormValues = z.infer<typeof accountSchema>;

export const instrumentSchema = z
  .object({
    symbol: z.string().trim().min(1, "Symbol is required").max(30, "Use 30 characters or fewer"),
    name: z.string().trim().max(200, "Use 200 characters or fewer"),
    assetType: z.enum(assetTypes),
    /** Empty means "use the default for the asset type". */
    priceSource: z.enum(["", ...priceSources]),
    sourceId: z.string().trim().max(100, "Use 100 characters or fewer"),
  })
  .superRefine((value, context) => {
    if (value.assetType === "CRYPTO" && value.priceSource !== "MANUAL" && value.sourceId === "") {
      context.addIssue({
        code: "custom",
        path: ["sourceId"],
        message: "Enter the CoinGecko coin id, for example bitcoin",
      });
    }
  });
export type InstrumentFormValues = z.infer<typeof instrumentSchema>;

export const manualPriceSchema = z.object({
  price: z
    .string()
    .trim()
    .min(1, "Price is required")
    .regex(DECIMAL, "Enter a number of zero or more, with up to 8 decimals"),
  asOf: z
    .string()
    .min(1, "As-of date is required")
    .regex(/^\d{4}-\d{2}-\d{2}$/, "Use a valid date"),
});
export type ManualPriceFormValues = z.infer<typeof manualPriceSchema>;
