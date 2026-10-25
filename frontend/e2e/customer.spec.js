import { test, expect } from "@playwright/test";
import { mkdir } from "node:fs/promises";
import path from "node:path";
import { randomUUID } from "node:crypto";

const apiBase =
  (process.env.API_PROXY_TARGET || "http://localhost:8080") + "/api/v1";
const artifactDir = process.env.TEST_ARTIFACT_DIR || "test-results/screenshots";

async function account(request) {
  const email = "p5-" + randomUUID() + "@example.invalid";
  const password = randomUUID() + "Aa1!";
  const registered = await request.post(apiBase + "/auth/register", {
    data: { fullName: "Khách P5", email, password },
  });
  expect(registered.status()).toBe(201);
  const loggedIn = await request.post(apiBase + "/auth/login", {
    data: { email, password },
  });
  expect(loggedIn.ok()).toBeTruthy();
  const session = await loggedIn.json();
  return { email, password, token: session.accessToken };
}

async function login(page, user) {
  await page.goto("/login");
  await page.getByLabel("Email", { exact: true }).fill(user.email);
  await page.locator('input[name="password"]').fill(user.password);
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page.getByText("Chào, Khách P5")).toBeVisible();
}

async function chooseBus(page) {
  await page.goto("/");
  await page
    .getByRole("link", { name: "Chọn ghế chuyến DEMO-P3-BUS-01", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Chọn chỗ ngồi yêu thích" }),
  ).toBeVisible();
}

async function screenshot(page, name) {
  await mkdir(artifactDir, { recursive: true });
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.evaluate(async () => {
    await document.fonts.ready;
  });
  await page.screenshot({ path: path.join(artifactDir, name), fullPage: true });
}

test("public search works on desktop and mobile, including empty and network errors", async ({
  page,
}) => {
  await page.goto("/");
  await expect(
    page.getByRole("link", { name: "Chọn ghế chuyến DEMO-P3-BUS-01" }),
  ).toBeVisible();
  await screenshot(page, "home-desktop.png");
  await page.getByRole("button", { name: "Tàu hỏa", exact: true }).click();
  await page.getByRole("button", { name: "Tìm kiếm", exact: true }).click();
  await expect(
    page.getByRole("link", { name: "Chọn ghế chuyến DEMO-P3-TRAIN-01" }),
  ).toBeVisible();
  await screenshot(page, "train-desktop.png");
  await expect(
    page.getByRole("link", { name: "Chọn ghế chuyến DEMO-P3-BUS-01" }),
  ).toHaveCount(0);
  await page.locator('input[name="origin"]').fill("Không có tuyến này");
  await page.getByRole("button", { name: "Tìm kiếm", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Chưa tìm thấy chuyến phù hợp" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Xem tất cả chuyến" }).click();
  await expect(
    page.getByRole("link", { name: "Chọn ghế chuyến DEMO-P3-BUS-01" }),
  ).toBeVisible();
  await page.setViewportSize({ width: 375, height: 812 });
  await screenshot(page, "home-mobile.png");
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await page.route("**/api/v1/trips?**", (route) => route.abort("failed"));
  await page.reload();
  await expect(page.getByRole("alert")).toContainText(
    "Không thể kết nối máy chủ",
  );
  await page.unroute("**/api/v1/trips?**");
  await page.getByRole("button", { name: "Thử lại" }).click();
  await expect(
    page.getByRole("link", { name: "Chọn ghế chuyến DEMO-P3-BUS-01" }),
  ).toBeVisible();
});

test("auth dialogs match the layout, trap focus and close without losing navigation", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByRole("link", { name: "Đăng nhập", exact: true }).click();
  const loginDialog = page.getByRole("dialog", {
    name: "Đăng nhập",
    exact: true,
  });
  await expect(loginDialog).toBeVisible();
  await expect(page.getByLabel("Email", { exact: true })).toBeFocused();
  for (let index = 0; index < 9; index++) {
    await page.keyboard.press("Tab");
    expect(
      await page.evaluate(() =>
        Boolean(document.activeElement.closest("dialog")),
      ),
    ).toBe(true);
  }
  await screenshot(page, "login-desktop.png");
  await page.getByRole("link", { name: "Đăng ký ngay", exact: true }).click();
  await expect(
    page.getByRole("dialog", { name: "Đăng ký", exact: true }),
  ).toBeVisible();
  await screenshot(page, "register-desktop.png");
  await page.setViewportSize({ width: 375, height: 812 });
  await screenshot(page, "register-mobile.png");
  const bounds = await page.getByRole("dialog").boundingBox();
  expect(bounds.x).toBeGreaterThanOrEqual(0);
  expect(bounds.x + bounds.width).toBeLessThanOrEqual(375);
  await page.getByRole("button", { name: "Đóng đăng ký", exact: true }).click();
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await page.goto("/login");
  await expect(
    page.getByRole("dialog", { name: "Đăng nhập", exact: true }),
  ).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole("dialog")).toHaveCount(0);
});

test("register, login, keep session on reload, book and cancel with real APIs", async ({
  page,
}) => {
  const email = "p5-ui-" + randomUUID() + "@example.invalid";
  const password = randomUUID() + "Aa1!";
  await page.goto("/bookings");
  await expect(page).toHaveURL(/\/login$/);
  await page.getByRole("link", { name: "Đăng ký ngay" }).click();
  await page.getByLabel("Họ và tên", { exact: true }).fill("Khách P5");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.locator('input[name="password"]').fill(password);
  await page
    .getByRole("button", { name: "Tạo tài khoản", exact: true })
    .click();
  await expect(page.getByRole("status")).toContainText("Đăng ký thành công");
  await page.locator('input[name="password"]').fill("wrong-password");
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText(
    "Email hoặc mật khẩu chưa đúng",
  );
  await page.locator('input[name="password"]').fill(password);
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page).toHaveURL(/\/bookings$/);
  await expect(
    page.getByRole("heading", { name: "Hành trình đầu tiên đang chờ" }),
  ).toBeVisible();
  await page.reload();
  await expect(page.getByText("Chào, Khách P5")).toBeVisible();
  await chooseBus(page);
  await page
    .getByRole("button", { name: "Ghế A5, còn trống", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Ghế A6, còn trống", exact: true })
    .click();
  await screenshot(page, "seats-desktop.png");
  await page
    .getByRole("button", { name: "Giữ ghế đã chọn", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Đã giữ chỗ cho bạn." }),
  ).toBeVisible();
  await expect(page.getByLabel("Thời gian giữ ghế còn lại")).toBeVisible();
  await screenshot(page, "booking-desktop.png");
  await page.setViewportSize({ width: 375, height: 812 });
  await screenshot(page, "booking-mobile.png");
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await page.getByRole("link", { name: "Xem tất cả đơn của tôi" }).click();
  await expect(page.getByRole("heading", { name: "Ghế A5, A6" })).toBeVisible();
  await page.getByRole("link", { name: "Chi tiết", exact: true }).click();
  await page
    .getByRole("button", { name: "Hủy đơn đặt chỗ", exact: true })
    .click();
  await page.getByRole("button", { name: "Xác nhận hủy", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Đơn đã hủy");
  await chooseBus(page);
  await expect(
    page.getByRole("button", { name: "Ghế A5, còn trống", exact: true }),
  ).toBeEnabled();
  await page.getByRole("button", { name: "Đăng xuất", exact: true }).click();
  await page.goto("/bookings");
  await expect(page).toHaveURL(/\/login$/);
});

test("login return preserves selected seats and a real seat conflict refreshes the grid", async ({
  page,
  request,
}) => {
  const owner = await account(request);
  const competitor = await account(request);
  await chooseBus(page);
  await page
    .getByRole("button", { name: "Ghế A3, còn trống", exact: true })
    .click();
  await page.getByRole("button", { name: "Đăng nhập để giữ ghế" }).click();
  await page.getByLabel("Email", { exact: true }).fill(owner.email);
  await page.locator('input[name="password"]').fill(owner.password);
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Ghế A3, còn trống", exact: true }),
  ).toHaveAttribute("aria-pressed", "true");
  const results = await (
    await request.get(apiBase + "/trips?origin=" + encodeURIComponent("Hà Nội"))
  ).json();
  const tripId = results.content[0].id;
  const seats = await (
    await request.get(apiBase + "/trips/" + tripId + "/seats")
  ).json();
  const seatId = seats.find((seat) => seat.seatCode === "A3").id;
  const reserved = await request.post(apiBase + "/bookings", {
    headers: { Authorization: "Bearer " + competitor.token },
    data: { tripId, seatIds: [seatId] },
  });
  expect(reserved.status()).toBe(201);
  const booking = await reserved.json();
  try {
    await page
      .getByRole("button", { name: "Giữ ghế đã chọn", exact: true })
      .click();
    await expect(page.getByRole("alert")).toContainText(
      "vừa được người khác giữ",
    );
    await expect(
      page.getByRole("button", { name: "Ghế A3, đang giữ", exact: true }),
    ).toBeDisabled();
    await expect(
      page.getByRole("button", { name: "Giữ ghế đã chọn", exact: true }),
    ).toBeDisabled();
  } finally {
    const cancelled = await request.post(
      apiBase + "/bookings/" + booking.id + "/cancel",
      {
        headers: { Authorization: "Bearer " + competitor.token },
      },
    );
    expect(cancelled.ok()).toBeTruthy();
  }
});

test("hold countdown expires and the server reopens the seat", async ({
  page,
  request,
}) => {
  test.setTimeout(70000);
  const owner = await account(request);
  await login(page, owner);
  await chooseBus(page);
  await page
    .getByRole("button", { name: "Ghế A4, còn trống", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Giữ ghế đã chọn", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Đã giữ chỗ cho bạn." }),
  ).toBeVisible();
  const bookingId = page.url().split("/").pop();
  await expect(page.getByRole("status")).toContainText(
    "Thời gian giữ ghế đã kết thúc",
    { timeout: 45000 },
  );
  await expect(
    page.getByRole("button", { name: "Hủy đơn đặt chỗ" }),
  ).toHaveCount(0);
  await expect
    .poll(async () => {
      const response = await request.get(apiBase + "/bookings/" + bookingId, {
        headers: { Authorization: "Bearer " + owner.token },
      });
      return (await response.json()).status;
    })
    .toBe("EXPIRED");
  await chooseBus(page);
  await expect(
    page.getByRole("button", { name: "Ghế A4, còn trống", exact: true }),
  ).toBeEnabled();
});
