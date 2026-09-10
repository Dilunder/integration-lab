import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests",
  timeout: 30000,
  workers: 1,
  outputDir: "../artifacts/browser",
  use: {
    baseURL: process.env.LAB_URL || "http://127.0.0.1:8088",
    headless: true,
    screenshot: "only-on-failure",
  },
  reporter: [["list"]],
});
