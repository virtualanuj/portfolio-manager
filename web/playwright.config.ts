import { defineConfig } from "@playwright/test";

/**
 * End-to-end tests run against their own stack so they never touch development data: a separate
 * database (portfolio_e2e), the API with the local and e2e profiles on port 8081, and the web dev
 * server on port 3100. The e2e profile swaps the price feeds for scriptable stubs.
 */
const WEB_PORT = 3100;
const API_PORT = 8081;

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  reporter: "list",
  timeout: 90_000,
  use: {
    baseURL: `http://127.0.0.1:${WEB_PORT}`,
    trace: "retain-on-failure",
  },
  webServer: [
    {
      command: "../scripts/e2e-api.sh",
      url: `http://127.0.0.1:${API_PORT}/api/actuator/health/liveness`,
      reuseExistingServer: false,
      timeout: 240_000,
      env: { E2E_API_PORT: String(API_PORT) },
    },
    {
      command: `npm run dev -- --hostname 127.0.0.1 --port ${WEB_PORT}`,
      url: `http://127.0.0.1:${WEB_PORT}`,
      reuseExistingServer: false,
      timeout: 120_000,
      env: { API_BASE_URL: `http://127.0.0.1:${API_PORT}` },
    },
  ],
});
