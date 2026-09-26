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

export const txnTypes = ["BUY", "SELL", "SPLIT", "REINVEST"] as const;

const POSITIVE_DECIMAL = /^\d+(\.\d{1,8})?$/;

export const transactionSchema = z
  .object({
    accountId: z.string().min(1, "Choose an account"),
    instrumentId: z.string().min(1, "Choose an instrument"),
    type: z.enum(txnTypes),
    tradeDate: z
      .string()
      .min(1, "Date is required")
      .regex(/^\d{4}-\d{2}-\d{2}$/, "Use a valid date"),
    quantity: z.string().trim(),
    unitPrice: z.string().trim(),
    splitNumerator: z.string().trim(),
    splitDenominator: z.string().trim(),
    note: z.string().trim().max(500, "Use 500 characters or fewer"),
  })
  .superRefine((value, context) => {
    const add = (path: string, message: string) =>
      context.addIssue({ code: "custom", path: [path], message });

    if (value.type === "SPLIT") {
      for (const field of ["splitNumerator", "splitDenominator"] as const) {
        if (!/^[1-9]\d{0,5}$/.test(value[field])) add(field, "Enter a whole number above zero");
      }
      return;
    }
    if (!POSITIVE_DECIMAL.test(value.quantity) || /^0+(\.0+)?$/.test(value.quantity)) {
      add("quantity", "Quantity must be greater than zero");
    }
    if (!POSITIVE_DECIMAL.test(value.unitPrice)) {
      add("unitPrice", "Price must be zero or more");
    }
  });
export type TransactionFormValues = z.infer<typeof transactionSchema>;
