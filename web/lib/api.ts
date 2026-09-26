import type { Problem } from "./types";

/** A failed API call. `message` is the server's own explanation when there is one. */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: Problem | null;

  constructor(status: number, message: string, problem: Problem | null = null) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.problem = problem;
  }

  /** Validation messages keyed by field name. */
  get fieldErrors(): Record<string, string> {
    const result: Record<string, string> = {};
    for (const error of this.problem?.errors ?? []) result[error.field] = error.message;
    return result;
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers = new Headers({ accept: "application/json" });
  const init: RequestInit = { method, headers };
  if (body !== undefined) {
    headers.set("content-type", "application/json");
    init.body = JSON.stringify(body);
  }

  let response: Response;
  try {
    response = await fetch(path, init);
  } catch {
    throw new ApiError(0, "Cannot reach the server. Check that the app is running.");
  }

  const isJson = response.headers.get("content-type")?.includes("json") ?? false;
  if (!response.ok) {
    const problem = isJson ? ((await response.json().catch(() => null)) as Problem | null) : null;
    const message = problem?.detail ?? problem?.title ?? `Request failed (${response.status})`;
    throw new ApiError(response.status, message, problem);
  }
  if (response.status === 204 || !isJson) return undefined as T;
  return (await response.json()) as T;
}

export const api = {
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, body),
  put: <T>(path: string, body?: unknown) => request<T>("PUT", path, body),
  del: <T = void>(path: string) => request<T>("DELETE", path),
};
