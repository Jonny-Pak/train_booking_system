import { test, expect } from "@playwright/test";
import { mkdir } from "node:fs/promises";
import path from "node:path";
import { randomUUID } from "node:crypto";

const apiBase =
  (process.env.API_PROXY_TARGET || "http://localhost:8080") + "/api/v1";
const artifactDir = process.env.TEST_ARTIFACT_DIR || "test-results/screenshots";

async function verificationLink(request, email) {
  const inbox = process.env.TEST_MAIL_URL;
  if (!inbox)
    throw new Error("TEST_MAIL_URL must point to the isolated SMTP inbox");
  const response = await request.get(
    inbox + "/messages?to=" + encodeURIComponent(email),
  );
  expect(response.ok()).toBeTruthy();
  const messages = await response.json();
  const link = messages
    .at(-1)
    ?.body.match(/https?:\/\/[^\s]+\/verify-email#token=[a-f0-9]{64}/)?.[0];
  expect(link).toBeTruthy();
  return link;
}

async function account(request) {
  const email = "p5-" + randomUUID() + "@example.invalid";
  const password = randomUUID() + "Aa1!";
  const registered = await request.post(apiBase + "/auth/register", {
    data: { fullName: "Khách P5", email, password },
  });
  expect(registered.status()).toBe(201);
  const link = await verificationLink(request, email);
  const token = new URLSearchParams(new URL(link).hash.slice(1)).get("token");
  expect(
    (
      await request.post(apiBase + "/auth/verify-email", { data: { token } })
    ).status(),
  ).toBe(204);
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

test("registration requires matching passwords and terms acceptance in the requested order", async ({
  page,
}) => {
  let registrationRequests = 0;
  page.on("request", (request) => {
    if (request.method() === "POST" && request.url().endsWith("/auth/register"))
      registrationRequests++;
  });
  await page.goto("/register");
  await expect(
    page.getByRole("dialog", { name: "Đăng ký", exact: true }),
  ).toBeVisible();
  expect(
    await page
      .locator(".auth-dialog-form input")
      .evaluateAll((inputs) => inputs.map((input) => input.name)),
  ).toEqual([
    "fullName",
    "phone",
    "password",
    "confirmPassword",
    "email",
    "acceptedTerms",
  ]);
  const submit = page.getByRole("button", {
    name: "Tạo tài khoản",
    exact: true,
  });
  const terms = page.getByRole("checkbox", {
    name: "Tôi đồng ý với chính sách và điều khoản sử dụng.",
  });
  await expect(terms).not.toBeChecked();
  await expect(submit).toBeDisabled();
  await page.getByLabel("Họ và tên", { exact: true }).fill("Khách kiểm tra");
  await page.getByLabel("Số điện thoại", { exact: true }).fill("0900000000");
  await page
    .getByLabel("Email", { exact: true })
    .fill("validation@example.invalid");
  await page.locator('input[name="password"]').fill("Test-password-21!");
  const confirmation = page.getByLabel("Xác nhận mật khẩu", { exact: true });
  await confirmation.fill("Different-password!");
  await page
    .getByRole("button", { name: "Hiện xác nhận mật khẩu", exact: true })
    .click();
  await expect(confirmation).toHaveAttribute("type", "text");
  await page
    .getByRole("button", { name: "Ẩn xác nhận mật khẩu", exact: true })
    .click();
  await expect(confirmation).toHaveAttribute("type", "password");
  await terms.check();
  await submit.click();
  await expect(page.getByRole("alert")).toContainText(
    "Xác nhận mật khẩu chưa khớp",
  );
  expect(registrationRequests).toBe(0);
  await terms.uncheck();
  await expect(submit).toBeDisabled();
});

test("register, login, keep session on reload, book and cancel with real APIs", async ({
  page,
  request,
}) => {
  const email = "p5-ui-" + randomUUID() + "@example.invalid";
  const password = randomUUID() + "Aa1!";
  await page.goto("/bookings");
  await expect(page).toHaveURL(/\/login$/);
  await page.getByRole("link", { name: "Đăng ký ngay" }).click();
  await page.getByLabel("Họ và tên", { exact: true }).fill("Khách P5");
  await page.getByLabel("Số điện thoại", { exact: true }).fill("0900000000");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.locator('input[name="password"]').fill(password);
  await page.getByLabel("Xác nhận mật khẩu", { exact: true }).fill(password);
  await page
    .getByRole("checkbox", {
      name: "Tôi đồng ý với chính sách và điều khoản sử dụng.",
    })
    .check();
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
  await expect(page.getByRole("alert")).toContainText("Bạn cần xác nhận email");
  await page
    .getByRole("button", { name: "Gửi lại email xác nhận", exact: true })
    .click();
  await expect(
    page.getByText("Nếu email đang chờ xác nhận", { exact: false }),
  ).toBeVisible();
  await page.goto(await verificationLink(request, email));
  await expect(
    page.getByRole("heading", { name: "Xác nhận email", exact: true }),
  ).toBeVisible();
  await expect(page).toHaveURL(/\/verify-email$/);
  await screenshot(page, "verify-email-desktop.png");
  await page
    .getByRole("button", { name: "Xác nhận email", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Email đã được xác nhận" }),
  ).toBeVisible();
  await page
    .getByRole("link", { name: "Đăng nhập", exact: true })
    .last()
    .click();
  await page.getByLabel("Email", { exact: true }).fill(email);
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

test("verification page handles a missing, invalid and already used link", async ({
  page,
  request,
}) => {
  await page.goto("/verify-email");
  await expect(page.getByRole("alert")).toContainText("Thiếu link xác nhận");
  await page.goto("/");
  await page.goto("/verify-email#token=" + "0".repeat(64));
  await expect(page).toHaveURL(/\/verify-email$/);
  await page.reload();
  // A token intentionally removed from the URL cannot survive a reload.
  await expect(page.getByRole("alert")).toContainText("Thiếu link xác nhận");
  await page.goto("/");
  await page.goto("/verify-email#token=" + "0".repeat(64));
  await page
    .getByRole("button", { name: "Xác nhận email", exact: true })
    .click();
  await expect(page.getByRole("alert")).toContainText(
    "Link xác nhận không hợp lệ",
  );
  const owner = await account(request);
  const usedLink = await verificationLink(request, owner.email);
  await page.goto("/");
  await page.goto(usedLink);
  await page
    .getByRole("button", { name: "Xác nhận email", exact: true })
    .click();
  await expect(page.getByRole("alert")).toContainText("đã được sử dụng");
  await page.setViewportSize({ width: 375, height: 812 });
  await screenshot(page, "verify-email-mobile.png");
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
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
