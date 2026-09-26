import { describe, expect, it, vi } from "vitest";

import { forward } from "./proxy";

function upstream(init: ResponseInit & { body?: string } = {}) {
  const { body = "{}", ...rest } = init;
  return vi.fn().mockResolvedValue(new Response(body, rest));
}

function lastCall(fetchMock: ReturnType<typeof vi.fn>) {
  const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit & { duplex?: string }];
  return { url, init, headers: new Headers(init.headers) };
}

describe("forward", () => {
  it("forwards the path and query to the API base URL", async () => {
    const fetchMock = upstream();
    await forward(new Request("http://localhost:3000/api/holdings?assetType=ETF&sort=value"), {
      fetchImpl: fetchMock,
      env: { API_BASE_URL: "http://api.test:8080" },
    });
    expect(lastCall(fetchMock).url).toBe(
      "http://api.test:8080/api/holdings?assetType=ETF&sort=value",
    );
  });

  it("defaults the API base URL to loopback port 8080", async () => {
    const fetchMock = upstream();
    await forward(new Request("http://localhost:3000/api/accounts"), {
      fetchImpl: fetchMock,
      env: {},
    });
    expect(lastCall(fetchMock).url).toBe("http://127.0.0.1:8080/api/accounts");
  });

  it("forwards method and JSON body, streaming with duplex half", async () => {
    const fetchMock = upstream({ status: 201 });
    const request = new Request("http://localhost:3000/api/accounts", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ name: "Main" }),
    });
    await forward(request, { fetchImpl: fetchMock, env: {} });
    const { init, headers } = lastCall(fetchMock);
    expect(init.method).toBe("POST");
    expect(init.duplex).toBe("half");
    expect(headers.get("content-type")).toBe("application/json");
    expect(await new Response(init.body as ReadableStream).text()).toBe('{"name":"Main"}');
  });

  it("sends no body for GET requests", async () => {
    const fetchMock = upstream();
    await forward(new Request("http://localhost:3000/api/accounts"), {
      fetchImpl: fetchMock,
      env: {},
    });
    expect(lastCall(fetchMock).init.body).toBeUndefined();
  });

  it.each([202, 409, 422])("passes status %i and content type through", async (status) => {
    const fetchMock = upstream({
      status,
      headers: { "content-type": "application/problem+json" },
      body: '{"title":"x"}',
    });
    const response = await forward(new Request("http://localhost:3000/api/refresh"), {
      fetchImpl: fetchMock,
      env: {},
    });
    expect(response.status).toBe(status);
    expect(response.headers.get("content-type")).toBe("application/problem+json");
    expect(await response.text()).toBe('{"title":"x"}');
  });

  it("forwards request cookies and returns every Set-Cookie", async () => {
    const headers = new Headers();
    headers.append("set-cookie", "SESSION=abc; HttpOnly");
    headers.append("set-cookie", "XSRF-TOKEN=def; Path=/");
    const fetchMock = upstream({ headers });
    const response = await forward(
      new Request("http://localhost:3000/api/accounts", { headers: { cookie: "SESSION=old" } }),
      { fetchImpl: fetchMock, env: {} },
    );
    expect(lastCall(fetchMock).headers.get("cookie")).toBe("SESSION=old");
    expect(response.headers.getSetCookie()).toEqual([
      "SESSION=abc; HttpOnly",
      "XSRF-TOKEN=def; Path=/",
    ]);
  });

  it("drops hop-by-hop request headers", async () => {
    const fetchMock = upstream();
    await forward(
      new Request("http://localhost:3000/api/accounts", {
        headers: { connection: "keep-alive", "keep-alive": "timeout=5", te: "trailers", host: "x" },
      }),
      { fetchImpl: fetchMock, env: {} },
    );
    const { headers } = lastCall(fetchMock);
    for (const name of ["connection", "keep-alive", "te", "host"]) {
      expect(headers.has(name)).toBe(false);
    }
  });

  it("drops hop-by-hop response headers", async () => {
    const fetchMock = upstream({ headers: { "keep-alive": "timeout=5", "x-kept": "1" } });
    const response = await forward(new Request("http://localhost:3000/api/accounts"), {
      fetchImpl: fetchMock,
      env: {},
    });
    expect(response.headers.has("keep-alive")).toBe(false);
    expect(response.headers.get("x-kept")).toBe("1");
  });

  it("attaches X-Proxy-Secret when PROXY_SECRET is set", async () => {
    const fetchMock = upstream();
    await forward(new Request("http://localhost:3000/api/accounts"), {
      fetchImpl: fetchMock,
      env: { PROXY_SECRET: "s3cret" },
    });
    expect(lastCall(fetchMock).headers.get("x-proxy-secret")).toBe("s3cret");
  });

  it("never lets the client supply X-Proxy-Secret", async () => {
    const fetchMock = upstream();
    await forward(
      new Request("http://localhost:3000/api/accounts", {
        headers: { "x-proxy-secret": "forged" },
      }),
      { fetchImpl: fetchMock, env: {} },
    );
    expect(lastCall(fetchMock).headers.has("x-proxy-secret")).toBe(false);
  });

  it("returns 502 problem+json when the API is unreachable", async () => {
    const fetchMock = vi.fn().mockRejectedValue(new TypeError("fetch failed"));
    const response = await forward(new Request("http://localhost:3000/api/accounts"), {
      fetchImpl: fetchMock,
      env: {},
    });
    expect(response.status).toBe(502);
    expect(response.headers.get("content-type")).toBe("application/problem+json");
    const body = (await response.json()) as { status: number; title: string };
    expect(body.status).toBe(502);
    expect(body.title).toMatch(/unreachable/i);
  });
});
