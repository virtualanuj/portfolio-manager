"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";

import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { showServerError } from "@/lib/formErrors";
import { accountSchema, accountTypes, type AccountFormValues } from "@/lib/schemas";

type Props = {
  initial?: AccountFormValues;
  onSubmit: (values: AccountFormValues) => Promise<void>;
  onCancel: () => void;
};

const TYPE_LABELS: Record<(typeof accountTypes)[number], string> = {
  BROKERAGE: "Brokerage",
  RETIREMENT: "Retirement",
  CRYPTO: "Crypto",
  OTHER: "Other",
};

export function AccountForm({ initial, onSubmit, onCancel }: Props) {
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<AccountFormValues>({
    resolver: zodResolver(accountSchema),
    defaultValues: initial ?? { name: "", type: "BROKERAGE" },
  });

  async function submit(values: AccountFormValues) {
    try {
      await onSubmit(values);
    } catch (error) {
      showServerError(error, setError, ["name", "type"]);
    }
  }

  return (
    <form onSubmit={handleSubmit(submit)} noValidate className="flex flex-col gap-4">
      <Field id="account-name" label="Name" error={errors.name?.message}>
        <Input
          id="account-name"
          autoComplete="off"
          invalid={!!errors.name}
          aria-describedby="account-name-message"
          {...register("name")}
        />
      </Field>
      <Field id="account-type" label="Type" error={errors.type?.message}>
        <Select id="account-type" {...register("type")}>
          {accountTypes.map((type) => (
            <option key={type} value={type}>
              {TYPE_LABELS[type]}
            </option>
          ))}
        </Select>
      </Field>
      {errors.root && (
        <p role="alert" className="text-sm text-loss">
          {errors.root.message}
        </p>
      )}
      <div className="flex justify-end gap-2">
        <Button onClick={onCancel}>Cancel</Button>
        <Button type="submit" variant="primary" disabled={isSubmitting}>
          Save account
        </Button>
      </div>
    </form>
  );
}
