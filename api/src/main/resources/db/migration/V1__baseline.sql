CREATE TABLE account (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       TEXT        NOT NULL UNIQUE,
    type       TEXT        NOT NULL CHECK (type IN ('BROKERAGE', 'RETIREMENT', 'CRYPTO', 'OTHER')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE instrument (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol       TEXT NOT NULL,
    name         TEXT,
    asset_type   TEXT NOT NULL CHECK (asset_type IN ('STOCK', 'ETF', 'MUTUAL_FUND', 'CRYPTO')),
    price_source TEXT NOT NULL CHECK (price_source IN ('YAHOO', 'COINGECKO', 'MANUAL')),
    source_id    TEXT,
    UNIQUE (symbol, asset_type)
);

CREATE TABLE import_batch (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    filename   TEXT        NOT NULL,
    status     TEXT        NOT NULL CHECK (status IN ('STAGED', 'COMMITTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE import_row (
    batch_id UUID  NOT NULL REFERENCES import_batch (id) ON DELETE CASCADE,
    line_no  INT   NOT NULL,
    raw      JSONB NOT NULL,
    parsed   JSONB,
    errors   JSONB NOT NULL DEFAULT '[]',
    warnings JSONB NOT NULL DEFAULT '[]',
    PRIMARY KEY (batch_id, line_no)
);

CREATE TABLE "transaction" (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seq               BIGSERIAL   NOT NULL UNIQUE,
    account_id        UUID        NOT NULL REFERENCES account (id) ON DELETE RESTRICT,
    instrument_id     UUID        NOT NULL REFERENCES instrument (id) ON DELETE RESTRICT,
    type              TEXT        NOT NULL CHECK (type IN ('BUY', 'SELL', 'SPLIT', 'REINVEST')),
    trade_date        DATE        NOT NULL,
    quantity          NUMERIC(24, 8),
    unit_price        NUMERIC(24, 8),
    split_numerator   INT,
    split_denominator INT,
    note              TEXT,
    import_batch_id   UUID REFERENCES import_batch (id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT transaction_shape CHECK (
        (type IN ('BUY', 'SELL', 'REINVEST')
            AND quantity > 0 AND unit_price >= 0
            AND split_numerator IS NULL AND split_denominator IS NULL)
        OR
        (type = 'SPLIT'
            AND quantity IS NULL AND unit_price IS NULL
            AND split_numerator > 0 AND split_denominator > 0)
    )
);

CREATE INDEX transaction_position_idx
    ON "transaction" (account_id, instrument_id, trade_date, seq);
CREATE INDEX transaction_instrument_idx ON "transaction" (instrument_id);
CREATE INDEX transaction_import_batch_idx ON "transaction" (import_batch_id);

CREATE TABLE price (
    instrument_id UUID PRIMARY KEY REFERENCES instrument (id) ON DELETE CASCADE,
    price         NUMERIC(24, 8),
    prev_price    NUMERIC(24, 8),
    price_date    DATE,
    fetched_at    TIMESTAMPTZ,
    status        TEXT NOT NULL DEFAULT 'OK' CHECK (status IN ('OK', 'ERROR')),
    last_error    TEXT,
    manual_price  NUMERIC(24, 8),
    manual_as_of  DATE
);

CREATE TABLE snapshot (
    snap_date          DATE PRIMARY KEY,
    total_value        NUMERIC(20, 4) NOT NULL,
    total_cost_basis   NUMERIC(20, 4) NOT NULL,
    priced_positions   INT            NOT NULL DEFAULT 0,
    stale_positions    INT            NOT NULL DEFAULT 0,
    unpriced_positions INT            NOT NULL DEFAULT 0,
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE TABLE snapshot_holding (
    snap_date     DATE           NOT NULL REFERENCES snapshot (snap_date) ON DELETE CASCADE,
    account_id    UUID           NOT NULL REFERENCES account (id) ON DELETE RESTRICT,
    instrument_id UUID           NOT NULL REFERENCES instrument (id) ON DELETE RESTRICT,
    quantity      NUMERIC(24, 8) NOT NULL,
    price         NUMERIC(24, 8),
    value         NUMERIC(20, 4),
    cost_basis    NUMERIC(20, 4) NOT NULL,
    is_stale      BOOLEAN        NOT NULL,
    PRIMARY KEY (snap_date, account_id, instrument_id)
);

CREATE INDEX snapshot_holding_account_idx ON snapshot_holding (account_id);
CREATE INDEX snapshot_holding_instrument_idx ON snapshot_holding (instrument_id);

CREATE TABLE target_allocation (
    asset_type TEXT PRIMARY KEY CHECK (asset_type IN ('STOCK', 'ETF', 'MUTUAL_FUND', 'CRYPTO')),
    target_pct NUMERIC(5, 2) NOT NULL CHECK (target_pct >= 0 AND target_pct <= 100)
);

CREATE TABLE refresh_run (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    started_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    status      TEXT        NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED')),
    results     JSONB       NOT NULL DEFAULT '[]'
);

CREATE UNIQUE INDEX refresh_run_single_running_idx ON refresh_run (status) WHERE status = 'RUNNING';
