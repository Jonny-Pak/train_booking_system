import { defineConfig } from "@playwright/test";

const port = Number(process.env.E2E_PORT || 4175);

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60000,
  expect: { timeout: 10000 },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: "http://127.0.0.1:" + port,
    browserName: "chromium",
    channel: process.env.E2E_BROWSER_CHANNEL || "msedge",
    viewport: { width: 1440, height: 1050 },
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  webServer: {
    command: "npm run dev -- --host 127.0.0.1 --port " + port,
    url: "http://127.0.0.1:" + port,
    reuseExistingServer: false,
    timeout: 30000,
  },
});
