"use client";

import { Field } from "@/components/ui/Field";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import type { TransactionFilters } from "@/lib/transactionQuery";
import type { Account, Instrument, TxnType } from "@/lib/types";

type Props = {
  accounts: Account[];
  instruments: Instrument[];
  filters: TransactionFilters;
  onChange: (filters: TransactionFilters) => void;
};

export function TransactionFilterBar({ accounts, instruments, filters, onChange }: Props) {
  return (
    <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
      <Field id="filter-account" label="Account">
        <Select
          id="filter-account"
          value={filters.accountId}
          onChange={(event) => onChange({ ...filters, accountId: event.target.value })}
        >
          <option value="">All accounts</option>
          {accounts.map((account) => (
            <option key={account.id} value={account.id}>
              {account.name}
            </option>
          ))}
        </Select>
      </Field>
      <Field id="filter-instrument" label="Instrument">
        <Select
          id="filter-instrument"
          value={filters.instrumentId}
          onChange={(event) => onChange({ ...filters, instrumentId: event.target.value })}
        >
          <option value="">All instruments</option>
          {instruments.map((instrument) => (
            <option key={instrument.id} value={instrument.id}>
              {instrument.symbol}
            </option>
          ))}
        </Select>
      </Field>
      <Field id="filter-type" label="Type">
        <Select
          id="filter-type"
          value={filters.type}
          onChange={(event) => onChange({ ...filters, type: event.target.value as "" | TxnType })}
        >
          <option value="">All types</option>
          <option value="BUY">Buy</option>
          <option value="SELL">Sell</option>
          <option value="SPLIT">Split</option>
          <option value="REINVEST">Dividend reinvestment</option>
        </Select>
      </Field>
      <Field id="filter-from" label="From">
        <Input
          id="filter-from"
          type="date"
          value={filters.from}
          onChange={(event) => onChange({ ...filters, from: event.target.value })}
        />
      </Field>
      <Field id="filter-to" label="To">
        <Input
          id="filter-to"
          type="date"
          value={filters.to}
          onChange={(event) => onChange({ ...filters, to: event.target.value })}
        />
      </Field>
    </div>
  );
}
