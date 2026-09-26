import { afterEach, describe, expect, it, vi } from "vitest";

import { ApiError, api } from "./api";

function respond(status: number, body?: unknown, contentType = "application/json") {
  const fetchMock = vi.fn().mockImplementation(
    async () =>
      new Response(body === undefined ? null : JSON.stringify(body), {
        status,
        headers: body === undefined ? {} : { "content-type": contentType },
      }),
  );
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

afterEach(() => vi.unstubAllGlobals());

describe("api", () => {
  it("gets JSON from the given path", async () => {
    const fetchMock = respond(200, [{ id: "1" }]);

    await expect(api.get("/api/accounts")).resolves.toEqual([{ id: "1" }]);
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/accounts",
      expect.objectContaining({ method: "GET" }),
    );
  });

  it("sends a JSON body with the right header for post and put", async () => {
    const fetchMock = respond(201, { id: "1" });

    await api.post("/api/accounts", { name: "Main" });
    await api.put("/api/accounts/1", { name: "New" });

    const [, postInit] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(postInit.method).toBe("POST");
    expect(postInit.body).toBe('{"name":"Main"}');
    expect(new Headers(postInit.headers).get("content-type")).toBe("application/json");
    expect((fetchMock.mock.calls[1] as [string, RequestInit])[1].method).toBe("PUT");
  });

  it("uploads a file as multipart form data without forcing a content type", async () => {
    const fetchMock = respond(200, { id: "b1" });
    const file = new File(["a,b\n1,2\n"], "broker.csv", { type: "text/csv" });

    await api.upload("/api/imports", file);

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(init.method).toBe("POST");
    expect(init.body).toBeInstanceOf(FormData);
    expect((init.body as FormData).get("file")).toBeInstanceOf(File);
    expect(new Headers(init.headers).has("content-type")).toBe(false);
  });

  it("returns undefined for 204 responses", async () => {
    respond(204);

    await expect(api.del("/api/accounts/1")).resolves.toBeUndefined();
  });

  it("throws ApiError carrying the status and problem body", async () => {
    respond(
      409,
      { status: 409, detail: "An account named Main already exists" },
      "application/problem+json",
    );

    const error = await api.post("/api/accounts", {}).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(409);
    expect(apiError.problem?.detail).toBe("An account named Main already exists");
    expect(apiError.message).toBe("An account named Main already exists");
  });

  it("surfaces 422 messages unchanged", async () => {
    respond(
      422,
      { status: 422, detail: "Sell of 15 exceeds the 10 held on 2026-01-11", transactionId: "t1" },
      "application/problem+json",
    );

    const error = (await api.post("/api/transactions", {}).catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(422);
    expect(error.message).toBe("Sell of 15 exceeds the 10 held on 2026-01-11");
  });

  it("exposes field errors from validation problems", async () => {
    respond(
      400,
      {
        status: 400,
        detail: "The request has invalid fields",
        errors: [{ field: "name", message: "must not be blank" }],
      },
      "application/problem+json",
    );

    const error = (await api.post("/api/accounts", {}).catch((e: unknown) => e)) as ApiError;

    expect(error.fieldErrors).toEqual({ name: "must not be blank" });
  });

  it("falls back to a readable message when the body is not a problem", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response("<html>bad gateway</html>", { status: 502 })),
    );

    const error = (await api.get("/api/accounts").catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(502);
    expect(error.message).toMatch(/502/);
  });

  it("wraps network failures in an ApiError with status 0", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));

    const error = (await api.get("/api/accounts").catch((e: unknown) => e)) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(0);
  });
});
