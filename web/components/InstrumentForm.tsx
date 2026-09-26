"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";

import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { showServerError } from "@/lib/formErrors";
import { instrumentSchema, type InstrumentFormValues } from "@/lib/schemas";

type Props = {
  initial?: InstrumentFormValues;
  onSubmit: (values: InstrumentFormValues) => Promise<void>;
  onCancel: () => void;
};

const EMPTY: InstrumentFormValues = {
  symbol: "",
  name: "",
  assetType: "ETF",
  priceSource: "",
  sourceId: "",
};

export function InstrumentForm({ initial, onSubmit, onCancel }: Props) {
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<InstrumentFormValues>({
    resolver: zodResolver(instrumentSchema),
    defaultValues: initial ?? EMPTY,
  });

  async function submit(values: InstrumentFormValues) {
    try {
      await onSubmit(values);
    } catch (error) {
      showServerError(error, setError, ["symbol", "name", "assetType", "priceSource", "sourceId"]);
    }
  }

  return (
    <form onSubmit={handleSubmit(submit)} noValidate className="flex flex-col gap-4">
      <Field id="instrument-symbol" label="Symbol" error={errors.symbol?.message}>
        <Input
          id="instrument-symbol"
          autoComplete="off"
          invalid={!!errors.symbol}
          aria-describedby="instrument-symbol-message"
          {...register("symbol")}
        />
      </Field>
      <Field id="instrument-name" label="Name" error={errors.name?.message}>
        <Input id="instrument-name" autoComplete="off" {...register("name")} />
      </Field>
      <Field id="instrument-asset-type" label="Asset type" error={errors.assetType?.message}>
        <Select id="instrument-asset-type" {...register("assetType")}>
          <option value="STOCK">Stock</option>
          <option value="ETF">ETF</option>
          <option value="MUTUAL_FUND">Mutual fund</option>
          <option value="CRYPTO">Crypto</option>
        </Select>
      </Field>
      <Field
        id="instrument-price-source"
        label="Price source"
        error={errors.priceSource?.message}
        hint="Default is Yahoo Finance for stocks, ETFs and funds, and CoinGecko for crypto."
      >
        <Select id="instrument-price-source" {...register("priceSource")}>
          <option value="">Default for this asset type</option>
          <option value="YAHOO">Yahoo Finance</option>
          <option value="COINGECKO">CoinGecko</option>
          <option value="MANUAL">Manual (I enter the price)</option>
        </Select>
      </Field>
      <Field
        id="instrument-source-id"
        label="Source id"
        error={errors.sourceId?.message}
        hint="CoinGecko coin id such as bitcoin. Stocks, ETFs and funds use the symbol when empty."
      >
        <Input
          id="instrument-source-id"
          autoComplete="off"
          invalid={!!errors.sourceId}
          aria-describedby="instrument-source-id-message"
          {...register("sourceId")}
        />
      </Field>
      {errors.root && (
        <p role="alert" className="text-sm text-loss">
          {errors.root.message}
        </p>
      )}
      <div className="flex justify-end gap-2">
        <Button onClick={onCancel}>Cancel</Button>
        <Button type="submit" variant="primary" disabled={isSubmitting}>
          Save instrument
        </Button>
      </div>
    </form>
  );
}
