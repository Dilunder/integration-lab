import { test, expect } from "@playwright/test";
test.beforeEach(async ({ page }) => {
  if (!process.env.LAB_API_KEY) throw Error("LAB_API_KEY is required");
  await page.goto("/");
  await page.getByLabel("Workspace API key").fill(process.env.LAB_API_KEY);
  await page.getByRole("button", { name: "Open workspace" }).click();
  await expect(
    page.getByRole("heading", { name: "Your integration test bench" }),
  ).toBeVisible();
});
test("fixed handler passes business assertion and exports report", async ({
  page,
}) => {
  await page.screenshot({path:"../artifacts/dashboard.png",fullPage:true}); await page.getByRole("button", { name: "Try fixed handler" }).click();
  await page.getByRole("button", { name: "Save & run" }).click();
  await expect(page.locator(".badge")).toHaveText("PASSED", { timeout: 15000 });
  await expect(
    page.getByText("Business assertion passed", { exact: true }),
  ).toBeVisible();
  await expect(page.locator(".delivery")).toHaveCount(5); await page.screenshot({path:"../artifacts/report.png",fullPage:true});
  const download = page.waitForEvent("download");
  await page.getByRole("button", { name: "Download JUnit XML" }).click();
  expect((await download).suggestedFilename()).toMatch(/\.xml$/);
});
test("broken handler fails although all five deliveries return HTTP 200", async ({
  page,
}) => {
  await page.getByRole("button", { name: "Try broken handler" }).click();
  await page.getByRole("button", { name: "Save & run" }).click();
  await expect(page.locator(".badge")).toHaveText("FAILED", { timeout: 15000 });
  await expect(page.getByText(/Business assertion timed out/)).toBeVisible();
  await expect(page.getByText("HTTP 200", { exact: true })).toHaveCount(5);
});
test("event fixture is reusable in editor and mobile layout fits", async ({
  page,
}) => {
  await page.getByRole("button", { name: /Event library/ }).click();
  const name = "Event " + Date.now();
  await page.getByLabel("Event name", { exact: true }).fill(name);
  await page
    .getByLabel("Body", { exact: true })
    .fill('{"paymentId":"{{runId}}"}');
  await page.getByRole("button", { name: "Save event" }).click();
  await expect(page.getByText("Event saved.", { exact: false })).toBeVisible();
  await page.getByRole("button", { name: /Scenarios/ }).click();
  await page.screenshot({path:"../artifacts/dashboard.png",fullPage:true}); await page.getByRole("button", { name: "Try fixed handler" }).click();
  await page.getByLabel("Use saved event").selectOption({ label: name });
  await expect(page.getByLabel("Request body")).toHaveValue(
    '{"paymentId":"{{runId}}"}',
  );
  await page.setViewportSize({ width: 390, height: 844 });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBeTruthy();
});
