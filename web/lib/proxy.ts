const HOP_BY_HOP = new Set([
  "connection",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailer",
  "transfer-encoding",
  "upgrade",
  "host",
]);

/** Headers that must not cross the proxy in either direction. */
const REQUEST_STRIPPED = new Set([...HOP_BY_HOP, "content-length", "x-proxy-secret"]);

/** fetch() already decoded the body, so the encoding and length headers no longer apply. */
const RESPONSE_STRIPPED = new Set([...HOP_BY_HOP, "content-length", "content-encoding"]);

const DEFAULT_API_BASE_URL = "http://127.0.0.1:8080";

type ForwardOptions = {
  fetchImpl?: typeof fetch;
  env?: Record<string, string | undefined>;
};

function copyHeaders(source: Headers, stripped: Set<string>): Headers {
  const result = new Headers();
  source.forEach((value, name) => {
    if (!stripped.has(name.toLowerCase()) && name.toLowerCase() !== "set-cookie") {
      result.append(name, value);
    }
  });
  return result;
}

function problem(status: number, title: string, detail: string): Response {
  return new Response(JSON.stringify({ type: "about:blank", title, status, detail }), {
    status,
    headers: { "content-type": "application/problem+json" },
  });
}

/** Forwards an /api/* request to the same path on the Spring API and relays the response. */
export async function forward(request: Request, options: ForwardOptions = {}): Promise<Response> {
  const { fetchImpl = fetch, env = process.env } = options;
  const baseUrl = (env.API_BASE_URL ?? DEFAULT_API_BASE_URL).replace(/\/+$/, "");
  const incoming = new URL(request.url);
  const target = `${baseUrl}${incoming.pathname}${incoming.search}`;

  const headers = copyHeaders(request.headers, REQUEST_STRIPPED);
  if (env.PROXY_SECRET) {
    headers.set("x-proxy-secret", env.PROXY_SECRET);
  }

  const hasBody = request.method !== "GET" && request.method !== "HEAD" && request.body !== null;
  const init: RequestInit & { duplex?: "half" } = {
    method: request.method,
    headers,
    redirect: "manual",
    ...(hasBody ? { body: request.body, duplex: "half" as const } : {}),
  };

  let upstream: Response;
  try {
    upstream = await fetchImpl(target, init);
  } catch {
    return problem(502, "API unreachable", "The API did not respond. Is it running?");
  }

  const responseHeaders = copyHeaders(upstream.headers, RESPONSE_STRIPPED);
  for (const cookie of upstream.headers.getSetCookie()) {
    responseHeaders.append("set-cookie", cookie);
  }
  return new Response(upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: responseHeaders,
  });
}
