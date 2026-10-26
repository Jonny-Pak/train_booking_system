import { test, expect } from "@playwright/test";
import { mkdir } from "node:fs/promises";
import path from "node:path";

async function screenshot(page, name) {
  const directory = process.env.TEST_ARTIFACT_DIR || "test-results/screenshots";
  await mkdir(directory, { recursive: true });
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({ path: path.join(directory, name), fullPage: true });
}

test("choose departure and a connected destination with mouse, keyboard and unaccented search", async ({
  page,
}) => {
  await page.goto("/");
  const origin = page.getByRole("combobox", { name: "Điểm đi", exact: true });
  const destination = page.getByRole("combobox", {
    name: "Điểm đến",
    exact: true,
  });
  await origin.click();
  await expect(
    page.getByRole("option", { name: "Hà Nội", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("option", { name: "TP Hồ Chí Minh", exact: true }),
  ).toBeVisible();
  await screenshot(page, "locations-desktop.png");
  await origin.fill("ha noi");
  await expect(page.getByRole("option")).toHaveCount(1);
  await origin.press("ArrowDown");
  await origin.press("Enter");
  await expect(origin).toHaveValue("Hà Nội");
  await expect(destination).toBeFocused();
  await expect(page.getByRole("option")).toHaveCount(1);
  await page.getByRole("option", { name: "Hải Phòng", exact: true }).click();
  await expect(destination).toHaveValue("Hải Phòng");
  await expect(page.getByRole("listbox")).toHaveCount(0);
  await destination.click();
  await expect(page.getByRole("listbox")).toBeVisible();
  await destination.press("Escape");
  await expect(page.getByRole("listbox")).toHaveCount(0);
  await page.getByRole("button", { name: "Tìm kiếm", exact: true }).click();
  expect(new URL(page.url()).searchParams.get("origin")).toBe("Hà Nội");
  expect(new URL(page.url()).searchParams.get("destination")).toBe("Hải Phòng");
  await expect(
    page.getByRole("link", {
      name: "Chọn ghế chuyến DEMO-P3-BUS-01",
      exact: true,
    }),
  ).toBeVisible();
  await origin.fill("");
  await page
    .getByRole("option", { name: "TP Hồ Chí Minh", exact: true })
    .click();
  await expect(destination).toHaveValue("");
  await page.getByRole("option", { name: "Phan Thiết", exact: true }).click();
  await origin.fill("ha noi");
  await page.getByRole("option", { name: "Hà Nội", exact: true }).click();
  await expect(destination).toHaveValue("");
  await page.getByRole("option", { name: "Hải Phòng", exact: true }).click();
  await page.getByRole("button", { name: "Đổi điểm đi và điểm đến" }).click();
  await expect(origin).toHaveValue("Hải Phòng");
  await expect(destination).toHaveValue("Hà Nội");
  await page.getByRole("button", { name: "Tàu hỏa", exact: true }).click();
  await origin.fill("");
  await expect(
    page.getByRole("option", { name: "TP Hồ Chí Minh", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("option", { name: "Hà Nội", exact: true }),
  ).toHaveCount(0);
  await page
    .getByRole("option", { name: "TP Hồ Chí Minh", exact: true })
    .click();
  await expect(destination).toHaveValue("");
  await page.getByRole("option", { name: "Phan Thiết", exact: true }).click();
  await page.getByRole("button", { name: "Tìm kiếm", exact: true }).click();
  await expect(
    page.getByRole("link", {
      name: "Chọn ghế chuyến DEMO-P3-TRAIN-01",
      exact: true,
    }),
  ).toBeVisible();
  await page.reload();
  await expect(origin).toHaveValue("TP Hồ Chí Minh");
  await expect(destination).toHaveValue("Phan Thiết");
  await page.setViewportSize({ width: 375, height: 812 });
  await origin.click();
  await expect(
    page.getByRole("option", { name: "TP Hồ Chí Minh", exact: true }),
  ).toBeVisible();
  await screenshot(page, "locations-mobile.png");
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  const popup = await page.locator(".location-popup").boundingBox();
  expect(popup.x).toBeGreaterThanOrEqual(0);
  expect(popup.x + popup.width).toBeLessThanOrEqual(375);
  await page.locator(".reference-heading").click();
  await expect(page.getByRole("listbox")).toHaveCount(0);
});

test("location loading errors can retry without breaking trip search", async ({
  page,
}) => {
  await page.route("**/api/v1/trips/locations**", (route) =>
    route.abort("failed"),
  );
  await page.goto("/");
  await page.getByRole("combobox", { name: "Điểm đi", exact: true }).click();
  await expect(
    page.getByText("Chưa tải được địa điểm.", { exact: false }),
  ).toBeVisible();
  await page.unroute("**/api/v1/trips/locations**");
  await page
    .getByRole("button", { name: "Thử lại địa điểm", exact: true })
    .click();
  await expect(
    page.getByRole("option", { name: "Hà Nội", exact: true }),
  ).toBeVisible();
  await page
    .getByRole("combobox", { name: "Điểm đi", exact: true })
    .fill("Không có địa điểm này");
  await expect(
    page.getByText("Không có địa điểm phù hợp", { exact: false }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Tìm kiếm", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Chưa tìm thấy chuyến phù hợp" }),
  ).toBeVisible();
});
