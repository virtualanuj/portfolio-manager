"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";

import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { Input } from "@/components/ui/Input";
import { todayIso } from "@/lib/format";
import { showServerError } from "@/lib/formErrors";
import { manualPriceSchema, type ManualPriceFormValues } from "@/lib/schemas";

type Props = {
  initial?: ManualPriceFormValues;
  onSubmit: (values: ManualPriceFormValues) => Promise<void>;
  onCancel: () => void;
};

export function ManualPriceForm({ initial, onSubmit, onCancel }: Props) {
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ManualPriceFormValues>({
    resolver: zodResolver(manualPriceSchema),
    defaultValues: initial ?? { price: "", asOf: todayIso() },
  });

  async function submit(values: ManualPriceFormValues) {
    try {
      await onSubmit(values);
    } catch (error) {
      showServerError(error, setError, ["price", "asOf"]);
    }
  }

  return (
    <form onSubmit={handleSubmit(submit)} noValidate className="flex flex-col gap-4">
      <Field id="manual-price" label="Price" error={errors.price?.message}>
        <Input
          id="manual-price"
          inputMode="decimal"
          autoComplete="off"
          invalid={!!errors.price}
          aria-describedby="manual-price-message"
          {...register("price")}
        />
      </Field>
      <Field id="manual-as-of" label="As of" error={errors.asOf?.message}>
        <Input
          id="manual-as-of"
          type="date"
          invalid={!!errors.asOf}
          aria-describedby="manual-as-of-message"
          {...register("asOf")}
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
          Save price
        </Button>
      </div>
    </form>
  );
}
