import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { ImportDropzone, MAX_UPLOAD_BYTES } from "@/components/ImportDropzone";

function fileOf(size: number, name = "broker.csv") {
  return new File([new Uint8Array(size)], name, { type: "text/csv" });
}

describe("ImportDropzone", () => {
  it("passes a small CSV to the caller", async () => {
    const onFile = vi.fn();
    render(<ImportDropzone onFile={onFile} />);

    await userEvent.upload(screen.getByLabelText(/choose a csv file/i), fileOf(100));

    expect(onFile).toHaveBeenCalledOnce();
  });

  it("rejects a file over 2 MB before uploading it", async () => {
    const onFile = vi.fn();
    render(<ImportDropzone onFile={onFile} />);

    await userEvent.upload(
      screen.getByLabelText(/choose a csv file/i),
      fileOf(MAX_UPLOAD_BYTES + 1),
    );

    expect(onFile).not.toHaveBeenCalled();
    expect(screen.getByRole("alert")).toHaveTextContent("larger than 2 MB");
  });

  it("accepts a file of exactly 2 MB", async () => {
    const onFile = vi.fn();
    render(<ImportDropzone onFile={onFile} />);

    await userEvent.upload(screen.getByLabelText(/choose a csv file/i), fileOf(MAX_UPLOAD_BYTES));

    expect(onFile).toHaveBeenCalledOnce();
  });

  it("is disabled while an upload is running", () => {
    render(<ImportDropzone onFile={vi.fn()} disabled />);

    expect(screen.getByLabelText(/choose a csv file/i)).toBeDisabled();
  });
});
