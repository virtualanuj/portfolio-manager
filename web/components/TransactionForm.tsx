"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useForm, useWatch } from "react-hook-form";

import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { todayIso } from "@/lib/format";
import { showServerError } from "@/lib/formErrors";
import { transactionSchema, type TransactionFormValues } from "@/lib/schemas";
import type { Account, Instrument } from "@/lib/types";

type Props = {
  accounts: Account[];
  instruments: Instrument[];
  initial?: TransactionFormValues;
  onSubmit: (values: TransactionFormValues) => Promise<void>;
  onCancel: () => void;
};

const FIELDS = [
  "accountId",
  "instrumentId",
  "type",
  "tradeDate",
  "quantity",
  "unitPrice",
  "splitNumerator",
  "splitDenominator",
  "note",
] as const;

const TYPE_LABELS = {
  BUY: "Buy",
  SELL: "Sell",
  SPLIT: "Split",
  REINVEST: "Dividend reinvestment",
};

export function TransactionForm({ accounts, instruments, initial, onSubmit, onCancel }: Props) {
  const {
    register,
    handleSubmit,
    setError,
    control,
    formState: { errors, isSubmitting },
  } = useForm<TransactionFormValues>({
    resolver: zodResolver(transactionSchema),
    defaultValues: initial ?? {
      accountId: "",
      instrumentId: "",
      type: "BUY",
      tradeDate: todayIso(),
      quantity: "",
      unitPrice: "",
      splitNumerator: "",
      splitDenominator: "",
      note: "",
    },
  });
  const type = useWatch({ control, name: "type" });

  async function submit(values: TransactionFormValues) {
    try {
      await onSubmit(values);
    } catch (error) {
      showServerError(error, setError, FIELDS);
    }
  }

  return (
    <form onSubmit={handleSubmit(submit)} noValidate className="flex flex-col gap-4">
      <Field id="txn-type" label="Type" error={errors.type?.message}>
        <Select id="txn-type" {...register("type")}>
          {(Object.keys(TYPE_LABELS) as Array<keyof typeof TYPE_LABELS>).map((key) => (
            <option key={key} value={key}>
              {TYPE_LABELS[key]}
            </option>
          ))}
        </Select>
      </Field>
      <Field id="txn-account" label="Account" error={errors.accountId?.message}>
        <Select
          id="txn-account"
          invalid={!!errors.accountId}
          aria-describedby="txn-account-message"
          {...register("accountId")}
        >
          <option value="">Select an account</option>
          {accounts.map((account) => (
            <option key={account.id} value={account.id}>
              {account.name}
            </option>
          ))}
        </Select>
      </Field>
      <Field id="txn-instrument" label="Instrument" error={errors.instrumentId?.message}>
        <Select
          id="txn-instrument"
          invalid={!!errors.instrumentId}
          aria-describedby="txn-instrument-message"
          {...register("instrumentId")}
        >
          <option value="">Select an instrument</option>
          {instruments.map((instrument) => (
            <option key={instrument.id} value={instrument.id}>
              {instrument.symbol}
            </option>
          ))}
        </Select>
      </Field>
      <Field id="txn-date" label="Date" error={errors.tradeDate?.message}>
        <Input
          id="txn-date"
          type="date"
          invalid={!!errors.tradeDate}
          aria-describedby="txn-date-message"
          {...register("tradeDate")}
        />
      </Field>
      {type === "SPLIT" ? (
        <div className="grid grid-cols-2 gap-3">
          <Field
            id="txn-split-new"
            label="Split ratio, new shares"
            error={errors.splitNumerator?.message}
            hint="2 for a 2-for-1 split"
          >
            <Input
              id="txn-split-new"
              inputMode="numeric"
              invalid={!!errors.splitNumerator}
              aria-describedby="txn-split-new-message"
              {...register("splitNumerator")}
            />
          </Field>
          <Field
            id="txn-split-old"
            label="Split ratio, old shares"
            error={errors.splitDenominator?.message}
            hint="1 for a 2-for-1 split"
          >
            <Input
              id="txn-split-old"
              inputMode="numeric"
              invalid={!!errors.splitDenominator}
              aria-describedby="txn-split-old-message"
              {...register("splitDenominator")}
            />
          </Field>
        </div>
      ) : (
        <div className="grid grid-cols-2 gap-3">
          <Field id="txn-quantity" label="Quantity" error={errors.quantity?.message}>
            <Input
              id="txn-quantity"
              inputMode="decimal"
              autoComplete="off"
              invalid={!!errors.quantity}
              aria-describedby="txn-quantity-message"
              {...register("quantity")}
            />
          </Field>
          <Field id="txn-price" label="Price per unit" error={errors.unitPrice?.message}>
            <Input
              id="txn-price"
              inputMode="decimal"
              autoComplete="off"
              invalid={!!errors.unitPrice}
              aria-describedby="txn-price-message"
              {...register("unitPrice")}
            />
          </Field>
        </div>
      )}
      <Field id="txn-note" label="Note (optional)" error={errors.note?.message}>
        <Input id="txn-note" autoComplete="off" {...register("note")} />
      </Field>
      {errors.root && (
        <p role="alert" className="text-sm text-loss">
          {errors.root.message}
        </p>
      )}
      <div className="flex justify-end gap-2">
        <Button onClick={onCancel}>Cancel</Button>
        <Button type="submit" variant="primary" disabled={isSubmitting}>
          Save transaction
        </Button>
      </div>
    </form>
  );
}
