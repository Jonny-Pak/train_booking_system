import { test, expect } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { mkdir } from "node:fs/promises";
import path from "node:path";

test.skip(
  process.env.TEST_P7 !== "true",
  "Run with scripts/Test-P7.ps1 against isolated data",
);
const base =
  (process.env.API_PROXY_TARGET || "http://localhost:8080") + "/api/v1";
const headers = () => ({
  Authorization: "Bearer " + process.env.TEST_ADMIN_TOKEN,
});

async function adminLogin(page, request) {
  const response = await request.get(base + "/auth/me", { headers: headers() });
  expect(response.status()).toBe(200);
  const user = await response.json();
  expect(user.role).toBe("ADMIN");
  const session = {
    accessToken: process.env.TEST_ADMIN_TOKEN,
    expiresAt: new Date(Date.now() + 600000).toISOString(),
    user,
  };
  await page.goto("/");
  await page.evaluate(
    (value) =>
      sessionStorage.setItem("len-duong-session", JSON.stringify(value)),
    session,
  );
  await page.goto("/admin");
  await expect(
    page.getByRole("heading", { name: "Tổng quan hệ thống" }),
  ).toBeVisible();
  await expect(page.getByText("Kỳ thống kê:")).toBeVisible();
}

test("admin creates catalog, handles conflicts, filters bookings and sees dashboard on mobile", async ({
  page,
  request,
}) => {
  await adminLogin(page, request);
  const suffix = randomUUID().slice(0, 8);
  await page.getByRole("link", { name: "Tuyến đường", exact: true }).click();
  await page.getByRole("button", { name: "Thêm mới" }).click();
  await page.getByLabel("Điểm đi", { exact: true }).fill("Điểm A " + suffix);
  await page.getByLabel("Điểm đến", { exact: true }).fill("Điểm B " + suffix);
  await page.getByRole("button", { name: "Lưu", exact: true }).click();
  let row = page.getByRole("row").filter({ hasText: "Điểm A " + suffix });
  await expect(row).toBeVisible();
  await row.getByRole("button", { name: "Sửa", exact: true }).click();
  await page.getByLabel("Điểm đến", { exact: true }).fill("Điểm C " + suffix);
  await page.getByRole("button", { name: "Lưu", exact: true }).click();
  await expect(row).toContainText("Điểm C " + suffix);

  await page.getByRole("link", { name: "Phương tiện", exact: true }).click();
  await page.getByRole("button", { name: "Thêm mới" }).click();
  await page.getByLabel("Mã phương tiện", { exact: true }).fill("P7-" + suffix);
  await page
    .getByLabel("Tên phương tiện", { exact: true })
    .fill("Xe kiểm thử P7");
  await page.getByRole("button", { name: "Lưu", exact: true }).click();
  row = page.getByRole("row").filter({ hasText: "P7-" + suffix.toUpperCase() });
  await expect(row).toBeVisible();
  await row.getByRole("link", { name: "Quản lý ghế" }).click();
  await page.getByRole("button", { name: "Thêm mới" }).click();
  await page.getByLabel("Mã ghế", { exact: true }).fill("A1");
  await page.getByRole("button", { name: "Lưu", exact: true }).click();
  await expect(page.getByRole("row").filter({ hasText: "A1" })).toBeVisible();
  await page.getByRole("button", { name: "Thêm mới" }).click();
  await page.getByLabel("Mã ghế", { exact: true }).fill("A1");
  await page.getByRole("button", { name: "Lưu", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Mã ghế đã tồn tại");
  await page.getByRole("button", { name: "Đóng biểu mẫu" }).click();

  await page.getByRole("link", { name: "Chuyến đi", exact: true }).click();
  await page.getByRole("button", { name: "Thêm mới" }).click();
  await page
    .getByLabel("Tuyến đường", { exact: true })
    .selectOption({ label: "Điểm A " + suffix + " → Điểm C " + suffix });
  await page.getByLabel("Phương tiện", { exact: true }).selectOption({
    label: "P7-" + suffix.toUpperCase() + " · Xe kiểm thử P7",
  });
  const departure = new Date(Date.now() + 7 * 86400000)
    .toISOString()
    .slice(0, 10);
  await page
    .getByLabel("Giờ khởi hành (Việt Nam)", { exact: true })
    .fill(departure + "T08:00");
  await page
    .getByLabel("Giờ đến (Việt Nam)", { exact: true })
    .fill(departure + "T10:00");
  await page.getByLabel("Giá vé (VND)", { exact: true }).fill("180000");
  await page.getByRole("button", { name: "Lưu", exact: true }).click();
  row = page.getByRole("row").filter({ hasText: "Điểm A " + suffix });
  await expect(row).toBeVisible();
  await row.getByRole("button", { name: "Sửa", exact: true }).click();
  await page.getByLabel("Giá vé (VND)", { exact: true }).fill("190000");
  await page.getByRole("button", { name: "Lưu", exact: true }).click();
  await expect(row).toContainText("190.000");
  await row.getByRole("button", { name: "Xóa", exact: true }).click();
  await page.getByRole("button", { name: "Xác nhận xóa" }).click();
  await expect(row).toHaveCount(0);

  await page.getByRole("link", { name: "Phương tiện", exact: true }).click();
  row = page.getByRole("row").filter({ hasText: "P7-" + suffix.toUpperCase() });
  await row.getByRole("button", { name: "Sửa", exact: true }).click();
  await page
    .getByLabel("Tên phương tiện", { exact: true })
    .fill("Xe P7 đã sửa");
  await page.getByRole("button", { name: "Lưu", exact: true }).click();
  await expect(row).toContainText("Xe P7 đã sửa");
  await row.getByRole("link", { name: "Quản lý ghế" }).click();
  row = page.getByRole("row").filter({ hasText: "A1" });
  await row.getByRole("button", { name: "Sửa", exact: true }).click();
  await page.getByLabel("Mã ghế", { exact: true }).fill("B1");
  await page.getByRole("button", { name: "Lưu", exact: true }).click();
  row = page.getByRole("row").filter({ hasText: "B1" });
  await row.getByRole("button", { name: "Xóa", exact: true }).click();
  await page.getByRole("button", { name: "Xác nhận xóa" }).click();
  await expect(row).toHaveCount(0);
  await page.getByRole("link", { name: "Phương tiện", exact: true }).click();
  row = page.getByRole("row").filter({ hasText: "P7-" + suffix.toUpperCase() });
  await row.getByRole("button", { name: "Xóa", exact: true }).click();
  await page.getByRole("button", { name: "Xác nhận xóa" }).click();
  await expect(row).toHaveCount(0);
  await page.getByRole("link", { name: "Tuyến đường", exact: true }).click();
  row = page.getByRole("row").filter({ hasText: "Điểm A " + suffix });
  await row.getByRole("button", { name: "Xóa", exact: true }).click();
  await page.getByRole("button", { name: "Xác nhận xóa" }).click();
  await expect(row).toHaveCount(0);

  await page.getByRole("link", { name: "Đơn hàng", exact: true }).click();
  await page.getByLabel("Trạng thái đơn").selectOption("PENDING");
  await expect(page.locator("caption")).toContainText("bản ghi");
  await page.getByLabel("Trạng thái đơn").selectOption("");
  await page
    .getByRole("link", { name: "Xem đơn", exact: true })
    .first()
    .click();
  await expect(
    page.getByRole("heading", { name: "Chi tiết đơn hàng" }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Lịch sử thanh toán" }),
  ).toBeVisible();
  await page.getByRole("link", { name: "Cần đối soát", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Giao dịch cần đối soát" }),
  ).toBeVisible();
  await page.getByRole("link", { name: "Tổng quan", exact: true }).click();
  await page.getByLabel("Từ ngày").fill("2030-02-02");
  await page.getByLabel("Đến ngày").fill("2030-02-01");
  await page.getByRole("button", { name: "Xem thống kê" }).click();
  await expect(page.getByRole("alert")).toContainText(
    "Chọn khoảng ngày hợp lệ",
  );
  await page.getByLabel("Từ ngày").fill("");
  await page.getByLabel("Đến ngày").fill("");
  await page.getByRole("button", { name: "Xem thống kê" }).click();
  await expect(page.locator(".admin-metrics")).toBeVisible();
  const screenshots =
    process.env.TEST_ARTIFACT_DIR || "test-results/screenshots";
  await mkdir(screenshots, { recursive: true });
  await page.screenshot({
    path: path.join(screenshots, "admin-desktop.png"),
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(
    page.getByRole("heading", { name: "Tổng quan hệ thống" }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: path.join(screenshots, "admin-mobile.png"),
    fullPage: true,
  });
});

test("admin confirms cancellation and can retry a failed statistics request", async ({
  page,
  request,
}) => {
  await adminLogin(page, request);
  const trips = await (
    await request.get(base + "/trips?vehicleType=TRAIN")
  ).json();
  const trip = trips.content[0];
  const seats = await (
    await request.get(base + "/trips/" + trip.id + "/seats")
  ).json();
  const seat = seats.find((item) => item.status === "AVAILABLE");
  const created = await request.post(base + "/bookings", {
    headers: headers(),
    data: { tripId: trip.id, seatIds: [seat.id] },
  });
  expect(created.status()).toBe(201);
  const booking = await created.json();
  await page.goto("/admin/bookings/" + booking.id);
  await page.getByRole("button", { name: "Hủy đơn", exact: true }).click();
  await expect(
    page.getByRole("alertdialog", { name: "Xác nhận hủy đơn" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Giữ đơn", exact: true }).click();
  await expect(page.getByRole("alertdialog")).toHaveCount(0);
  await page.getByRole("button", { name: "Hủy đơn", exact: true }).click();
  await page.getByRole("button", { name: "Xác nhận hủy", exact: true }).click();
  await expect(page.getByText(/Khách #.*Đã hủy/)).toBeVisible();
  const freed = await (
    await request.get(base + "/trips/" + trip.id + "/seats")
  ).json();
  expect(freed.find((item) => item.id === seat.id).status).toBe("AVAILABLE");
  await page.route("**/api/v1/admin/statistics**", (route) =>
    route.fulfill({ status: 503, contentType: "application/json", body: "{}" }),
  );
  await page.getByRole("link", { name: "Tổng quan", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await page.unroute("**/api/v1/admin/statistics**");
  await page.getByRole("button", { name: "Thử lại" }).click();
  await expect(page.locator(".admin-metrics")).toBeVisible();
});

test("anonymous redirects to login and customer cannot enter admin or statistics API", async ({
  page,
  request,
}) => {
  await page.goto("/admin");
  await expect(page).toHaveURL(/\/login$/);
  const email = "p7-" + randomUUID() + "@example.invalid";
  const password = randomUUID() + "Aa1!";
  expect(
    (
      await request.post(base + "/auth/register", {
        data: { fullName: "Khách P7", email, password },
      })
    ).status(),
  ).toBe(201);
  const inbox = await (
    await request.get(
      process.env.TEST_MAIL_URL + "/messages?to=" + encodeURIComponent(email),
    )
  ).json();
  const token = inbox.at(-1).body.match(/#token=([a-f0-9]{64})/)[1];
  expect(
    (
      await request.post(base + "/auth/verify-email", { data: { token } })
    ).status(),
  ).toBe(204);
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.locator('input[name="password"]').fill(password);
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Không có quyền quản trị" }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Quản trị", exact: true }),
  ).toHaveCount(0);
  const session = await page.evaluate(() =>
    JSON.parse(sessionStorage.getItem("len-duong-session")),
  );
  expect(
    (
      await request.get(base + "/admin/statistics", {
        headers: { Authorization: "Bearer " + session.accessToken },
      })
    ).status(),
  ).toBe(403);
});
