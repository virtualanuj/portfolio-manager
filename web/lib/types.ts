/**
 * A decimal carried as a string, exactly as the API sends it. Money and quantities never become
 * numbers in the browser, so passing a `number` where a DecimalString is expected fails to compile.
 */
export type DecimalString = string & { readonly __brand: "DecimalString" };

export type AccountType = "BROKERAGE" | "RETIREMENT" | "CRYPTO" | "OTHER";
export type AssetType = "STOCK" | "ETF" | "MUTUAL_FUND" | "CRYPTO";
export type PriceSource = "YAHOO" | "COINGECKO" | "MANUAL";
export type TxnType = "BUY" | "SELL" | "SPLIT" | "REINVEST";
export type PriceStatus = "OK" | "STALE" | "MANUAL" | "MANUAL_STALE" | "UNPRICED";

export type Account = { id: string; name: string; type: AccountType; createdAt: string };

export type Instrument = {
  id: string;
  symbol: string;
  name: string | null;
  assetType: AssetType;
  priceSource: PriceSource;
  sourceId: string | null;
  manualPrice: DecimalString | null;
  manualAsOf: string | null;
};

export type Transaction = {
  id: string;
  accountId: string;
  accountName: string;
  instrumentId: string;
  symbol: string;
  type: TxnType;
  tradeDate: string;
  quantity: DecimalString | null;
  unitPrice: DecimalString | null;
  splitNumerator: number | null;
  splitDenominator: number | null;
  note: string | null;
};

export type TransactionPage = {
  items: Transaction[];
  page: number;
  size: number;
  totalItems: number;
};

export type HoldingRow = {
  accountId: string;
  accountName: string;
  instrumentId: string;
  symbol: string;
  assetType: AssetType;
  quantity: DecimalString;
  avgCost: DecimalString | null;
  costBasis: DecimalString;
  price: DecimalString | null;
  value: DecimalString | null;
  unrealized: DecimalString | null;
  unrealizedPct: DecimalString | null;
  dayChange: DecimalString | null;
  priceStatus: PriceStatus;
  priceAsOf: string | null;
};

export type Dashboard = {
  totalValue: DecimalString;
  totalCostBasis: DecimalString;
  unrealized: DecimalString;
  unrealizedPct: DecimalString | null;
  dayChange: DecimalString | null;
  pricedPositions: number;
  stalePositions: number;
  unpricedPositions: number;
  lastRefresh: { runId: string; status: string; finishedAt: string | null } | null;
};

export type TargetAllocation = { assetType: AssetType; targetPct: DecimalString };

/** RFC 7807 problem body returned by the API. */
export type Problem = {
  status: number;
  title?: string;
  detail?: string;
  errors?: Array<{ field: string; message: string }>;
  [extra: string]: unknown;
};

export type RefreshStatus = "RUNNING" | "SUCCEEDED" | "PARTIAL" | "FAILED";

export type RefreshRun = {
  id: string;
  status: RefreshStatus;
  startedAt: string;
  finishedAt: string | null;
  results: Array<{ symbol: string; ok: boolean; message: string | null }>;
};

export type SnapshotPoint = {
  date: string;
  totalValue: DecimalString;
  totalCostBasis: DecimalString;
};

export type RowStatus = "OK" | "WILL_CREATE" | "WARNING" | "ERROR";

export type ImportIssue = { field: string; message: string };

export type ImportRow = {
  lineNo: number;
  status: RowStatus;
  values: Record<string, string>;
  errors: ImportIssue[];
  warnings: ImportIssue[];
  createsAccount: boolean;
  createsInstrument: boolean;
  duplicate: boolean;
};

export type ImportSummary = {
  totalRows: number;
  ok: number;
  willCreate: number;
  warnings: number;
  errors: number;
  duplicates: number;
  newAccounts: number;
  newInstruments: number;
};

export type ImportPreviewData = {
  id: string;
  filename: string;
  status: "STAGED" | "COMMITTED";
  createdAt: string;
  summary: ImportSummary;
  fileWarnings: string[];
  rows: ImportRow[];
};

export type ImportCommitResult = {
  transactionsCreated: number;
  accountsCreated: number;
  instrumentsCreated: number;
  duplicatesSkipped: number;
};

export type AllocationRow = {
  assetType: AssetType;
  value: DecimalString;
  actualPct: DecimalString;
  targetPct: DecimalString | null;
  driftPct: DecimalString | null;
};

export type AllocationData = {
  totalValue: DecimalString;
  targetsSet: boolean;
  rows: AllocationRow[];
};
