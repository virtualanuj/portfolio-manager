package com.portfoliomanager.domain;

/** Kinds of transaction that change a position. Cash dividends are deliberately not modeled. */
public enum TxnType {
    BUY,
    SELL,
    SPLIT,
    REINVEST
}
