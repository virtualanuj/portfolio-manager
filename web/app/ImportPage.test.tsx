import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import ImportPage from "@/app/import/page";
import { ToastProvider } from "@/components/ui/Toast";
import { ApiError } from "@/lib/api";
import type { ImportPreviewData } from "@/lib/types";

const upload = vi.fn();
const post = vi.fn();
const del = vi.fn();
const revalidateAll = vi.fn();

vi.mock("@/lib/api", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/lib/api")>();
  return {
    ...original,
    api: {
      upload: (p: string, f: File) => upload(p, f),
      post: (p: string) => post(p),
      del: (p: string) => del(p),
    },
  };
});
vi.mock("@/lib/useApi", () => ({ revalidateAll: () => revalidateAll() }));

const staged: ImportPreviewData = {
  id: "b1",
  filename: "broker.csv",
  status: "STAGED",
  createdAt: "2026-09-26T10:00:00Z",
  fileWarnings: [],
  summary: {
    totalRows: 1,
    ok: 1,
    willCreate: 0,
    warnings: 0,
    errors: 0,
    duplicates: 0,
    newAccounts: 0,
    newInstruments: 0,
  },
  rows: [
    {
      lineNo: 2,
      status: "OK",
      values: {
        date: "2026-01-05",
        account: "Main",
        symbol: "VTI",
        type: "BUY",
        quantity: "10",
        price: "100",
      },
      errors: [],
      warnings: [],
      createsAccount: false,
      createsInstrument: false,
      duplicate: false,
    },
  ],
};

function setup() {
  render(
    <ToastProvider>
      <ImportPage />
    </ToastProvider>,
  );
}

async function chooseFile() {
  await userEvent.upload(
    screen.getByLabelText(/choose a csv file/i),
    new File(["a"], "broker.csv", { type: "text/csv" }),
  );
}

describe("ImportPage", () => {
  beforeEach(() => {
    upload.mockReset();
    post.mockReset();
    del.mockReset();
    revalidateAll.mockReset().mockResolvedValue(undefined);
  });

  it("documents the schema and links the sample file", () => {
    setup();

    expect(screen.getByRole("rowheader", { name: "asset_type" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /sample csv/i })).toHaveAttribute(
      "href",
      "/sample-transactions.csv",
    );
  });

  it("uploads, shows the preview, commits and shows the result summary", async () => {
    upload.mockResolvedValue(staged);
    post.mockResolvedValue({
      transactionsCreated: 1,
      accountsCreated: 1,
      instrumentsCreated: 1,
      duplicatesSkipped: 0,
    });
    setup();

    await chooseFile();
    expect(upload).toHaveBeenCalledWith("/api/imports", expect.any(File));
    await userEvent.click(await screen.findByRole("button", { name: /commit import/i }));

    expect(post).toHaveBeenCalledWith("/api/imports/b1/commit?includeDuplicates=false");
    expect(await screen.findByText(/1 transaction imported/i)).toBeInTheDocument();
    expect(screen.getByText(/1 account created/i)).toBeInTheDocument();
    expect(revalidateAll).toHaveBeenCalled();
  });

  it("sends includeDuplicates=true when chosen", async () => {
    const data = structuredClone(staged);
    data.summary.duplicates = 1;
    data.summary.warnings = 1;
    data.rows[0]!.status = "WARNING";
    data.rows[0]!.duplicate = true;
    data.rows[0]!.warnings = [{ field: "row", message: "Identical to line 1" }];
    upload.mockResolvedValue(data);
    post.mockResolvedValue({
      transactionsCreated: 1,
      accountsCreated: 0,
      instrumentsCreated: 0,
      duplicatesSkipped: 0,
    });
    setup();

    await chooseFile();
    await userEvent.click(await screen.findByLabelText(/include duplicate rows/i));
    await userEvent.click(screen.getByRole("button", { name: /commit import/i }));

    expect(post).toHaveBeenCalledWith("/api/imports/b1/commit?includeDuplicates=true");
  });

  it("shows why an upload failed", async () => {
    upload.mockRejectedValue(new ApiError(400, "The file has a header but no rows"));
    setup();

    await chooseFile();

    expect(await screen.findByRole("alert")).toHaveTextContent("The file has a header but no rows");
  });

  it("shows why a commit failed and keeps the preview", async () => {
    upload.mockResolvedValue(staged);
    post.mockRejectedValue(new ApiError(422, "1 row still have errors, so nothing was imported."));
    setup();

    await chooseFile();
    await userEvent.click(await screen.findByRole("button", { name: /commit import/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("nothing was imported");
    expect(screen.getByRole("button", { name: /commit import/i })).toBeInTheDocument();
  });

  it("discards the staged batch and returns to the upload step", async () => {
    upload.mockResolvedValue(staged);
    del.mockResolvedValue(undefined);
    setup();

    await chooseFile();
    await userEvent.click(await screen.findByRole("button", { name: /discard/i }));

    expect(del).toHaveBeenCalledWith("/api/imports/b1");
    expect(await screen.findByLabelText(/choose a csv file/i)).toBeInTheDocument();
  });
});
