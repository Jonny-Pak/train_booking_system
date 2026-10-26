import { test, expect } from "@playwright/test";
import { mkdir } from "node:fs/promises";
import path from "node:path";

test.skip(process.env.TEST_P7 !== "true", "Use isolated scripts/Test-P7.ps1");
const base =
  (process.env.API_PROXY_TARGET || "http://localhost:8080") + "/api/v1";
const headers = () => ({
  Authorization: "Bearer " + process.env.TEST_ADMIN_TOKEN,
});

async function login(page, request) {
  const user = await (
    await request.get(base + "/auth/me", { headers: headers() })
  ).json();
  await page.goto("/");
  await page.evaluate(
    (session) =>
      sessionStorage.setItem("len-duong-session", JSON.stringify(session)),
    {
      user,
      accessToken: process.env.TEST_ADMIN_TOKEN,
      expiresAt: new Date(Date.now() + 600000).toISOString(),
    },
  );
  await page.goto("/admin/site");
  await expect(
    page.getByRole("textbox", { name: "Nội dung hướng dẫn" }),
  ).toBeVisible();
}
async function screenshot(page, name) {
  const directory = process.env.TEST_ARTIFACT_DIR || "test-results/screenshots";
  await mkdir(directory, { recursive: true });
  await page.screenshot({ path: path.join(directory, name), fullPage: true });
}

test("public guide has detailed steps, mobile contents and retry on loading failure", async ({
  page,
}) => {
  await page.goto("/");
  await page
    .getByRole("link", { name: "Hướng dẫn đặt vé", exact: true })
    .first()
    .click();
  await expect(page).toHaveURL(/\/booking-guide$/);
  await expect(
    page.getByRole("heading", { name: "Hướng dẫn đặt vé tàu xe", exact: true }),
  ).toBeVisible();
  await expect(page.locator(".guide-content")).toContainText("10 phút");
  await expect(page.locator(".guide-content")).toContainText(
    "Trang quay về không tự xác nhận thanh toán",
  );
  await expect(page.locator(".guide-content")).toContainText("34 giường");
  await expect(
    page
      .getByRole("navigation", { name: "Mục lục hướng dẫn" })
      .getByRole("link"),
  ).toHaveCount(8);
  await screenshot(page, "booking-guide-desktop.png");
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator("body")).toHaveJSProperty("scrollWidth", 390);
  await screenshot(page, "booking-guide-mobile.png");
  await page.route("**/api/v1/site/settings", (route) =>
    route.fulfill({ status: 503, contentType: "application/json", body: "{}" }),
  );
  await page.reload();
  await expect(page.getByRole("alert")).toBeVisible();
  await page.unroute("**/api/v1/site/settings");
  await page.getByRole("button", { name: "Thử lại" }).click();
  await expect(page.locator(".guide-content")).toBeVisible();
});

test("admin formats and publishes guide, changes logo, resets logo and detects stale saves", async ({
  page,
  request,
}) => {
  const original = await (await request.get(base + "/site/settings")).json();
  try {
    await login(page, request);
    const editor = page.getByRole("textbox", { name: "Nội dung hướng dẫn" });
    await page.getByLabel("Tiêu đề trang").fill("Hướng dẫn đặt vé kiểm thử");
    await editor.fill("Hướng dẫn thanh toán an toàn");
    await editor.press("ControlOrMeta+A");
    await page.getByRole("button", { name: "In đậm", exact: true }).click();
    await page.getByLabel("Cỡ chữ", { exact: true }).selectOption("24px");
    await page.getByLabel("Màu chữ", { exact: true }).fill("#ff0000");
    await page
      .getByRole("button", { name: "Toàn màn hình", exact: true })
      .click();
    await expect(page.locator(".guide-editor-expanded")).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(page.locator(".guide-editor-expanded")).toHaveCount(0);
    await page.getByRole("button", { name: "Xem trước", exact: true }).click();
    await expect(
      page
        .getByRole("region", { name: "Xem trước hướng dẫn" })
        .locator("strong"),
    ).toHaveText("Hướng dẫn thanh toán an toàn");
    await screenshot(page, "admin-guide-editor.png");
    await page
      .getByRole("button", { name: "Lưu hướng dẫn", exact: true })
      .click();
    await expect(page.getByRole("status")).toContainText("Đã lưu hướng dẫn");
    await expect(
      page.getByRole("button", { name: "Lưu hướng dẫn", exact: true }),
    ).toBeDisabled();
    await page.reload();
    await expect(editor).toContainText("Hướng dẫn thanh toán an toàn");
    const dataUrl = await page.evaluate(() => {
      const canvas = document.createElement("canvas");
      canvas.width = 240;
      canvas.height = 64;
      const context = canvas.getContext("2d");
      context.fillStyle = "#ffe13b";
      context.fillRect(0, 0, 240, 64);
      context.fillStyle = "#182b47";
      context.font = "bold 28px sans-serif";
      context.fillText("TEST LOGO", 20, 43);
      return canvas.toDataURL("image/png");
    });
    await page.locator('input[type="file"]').setInputFiles({
      name: "test-logo.png",
      mimeType: "image/png",
      buffer: Buffer.from(dataUrl.split(",")[1], "base64"),
    });
    await expect(
      page.getByRole("button", { name: "Lưu logo", exact: true }),
    ).toBeEnabled();
    await page.getByRole("button", { name: "Lưu logo", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("Đã thay logo");
    await expect(page.locator(".brand-logo")).toHaveAttribute(
      "src",
      /\/site\/logo\?v=/,
    );
    await page.goto("/booking-guide");
    await expect(
      page.getByRole("heading", { name: "Hướng dẫn đặt vé kiểm thử" }),
    ).toBeVisible();
    await expect(page.locator(".guide-content strong")).toHaveText(
      "Hướng dẫn thanh toán an toàn",
    );
    await expect(page.locator(".guide-content span")).toHaveCSS(
      "color",
      "rgb(255, 0, 0)",
    );
    await expect(page.locator(".guide-content span")).toHaveCSS(
      "font-size",
      "24px",
    );
    await expect(page.locator(".brand-logo")).toHaveAttribute(
      "src",
      /\/site\/logo\?v=/,
    );
    await page.goto("/admin/site");
    await page.getByRole("button", { name: "Khôi phục logo mặc định" }).click();
    await expect(page.getByRole("status")).toContainText("Đã khôi phục");
    await expect(page.locator(".brand-logo")).toHaveAttribute(
      "src",
      "/images/vexesieure-logo-white.svg",
    );
    const current = await (await request.get(base + "/site/settings")).json();
    const external = await request.put(base + "/admin/site/guide", {
      headers: headers(),
      data: {
        title: "Cập nhật từ phiên khác",
        html: "<p>Giữ nguyên bản mới</p>",
        revision: current.revision,
      },
    });
    expect(external.status()).toBe(200);
    await page.getByLabel("Tiêu đề trang").fill("Bản cũ không được ghi đè");
    await page
      .getByRole("button", { name: "Lưu hướng dẫn", exact: true })
      .click();
    await expect(page.getByRole("alert")).toContainText("phiên khác");
    await expect(editor).toContainText("Hướng dẫn thanh toán an toàn");
    expect(
      (await (await request.get(base + "/site/settings")).json()).guideTitle,
    ).toBe("Cập nhật từ phiên khác");
  } finally {
    let current = await (await request.get(base + "/site/settings")).json();
    if (current.customLogo) {
      await request.delete(
        base + "/admin/site/logo?revision=" + current.revision,
        { headers: headers() },
      );
      current = await (await request.get(base + "/site/settings")).json();
    }
    await request.put(base + "/admin/site/guide", {
      headers: headers(),
      data: {
        title: original.guideTitle,
        html: original.guideHtml,
        revision: current.revision,
      },
    });
  }
});
