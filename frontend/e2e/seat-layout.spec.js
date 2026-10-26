import { test, expect } from "@playwright/test";
import { randomUUID, createHmac } from "node:crypto";
import { mkdir } from "node:fs/promises";
import path from "node:path";

test.skip(
  process.env.TEST_P7 !== "true",
  "Run with isolated scripts/Test-P7.ps1",
);
const base =
  (process.env.API_PROXY_TARGET || "http://localhost:8080") + "/api/v1";
const headers = () => ({
  Authorization: "Bearer " + process.env.TEST_ADMIN_TOKEN,
});
const screenshotDir =
  process.env.TEST_ARTIFACT_DIR || "test-results/screenshots";

async function login(page, request) {
  const user = await (
    await request.get(base + "/auth/me", { headers: headers() })
  ).json();
  await page.goto("/");
  await page.evaluate(
    (session) =>
      sessionStorage.setItem("len-duong-session", JSON.stringify(session)),
    {
      accessToken: process.env.TEST_ADMIN_TOKEN,
      expiresAt: new Date(Date.now() + 600000).toISOString(),
      user,
    },
  );
}
async function vehicle(request, type) {
  const response = await request.post(base + "/admin/vehicles", {
    headers: headers(),
    data: {
      vehicleCode: "LAYOUT-" + randomUUID().slice(0, 8),
      vehicleType: type,
      displayName: "Phương tiện sơ đồ chuẩn",
      active: true,
    },
  });
  expect(response.status()).toBe(201);
  return response.json();
}
async function trip(request, owner) {
  const routes = await (
    await request.get(base + "/admin/routes", { headers: headers() })
  ).json();
  const depart = new Date(Date.now() + 10 * 86400000);
  const response = await request.post(base + "/admin/trips", {
    headers: headers(),
    data: {
      routeId: routes.content[0].id,
      vehicleId: owner.id,
      departureAt: depart.toISOString(),
      arrivalAt: new Date(depart.getTime() + 2 * 3600000).toISOString(),
      basePrice: 150000,
      status: "SCHEDULED",
    },
  });
  expect(response.status()).toBe(201);
  return response.json();
}
async function screenshot(page, name) {
  await mkdir(screenshotDir, { recursive: true });
  await page.screenshot({
    path: path.join(screenshotDir, name),
    fullPage: true,
  });
}

test("admin generates a two-deck sleeper bus and customer books a berth on each deck", async ({
  page,
  request,
}) => {
  await login(page, request);
  const bus = await vehicle(request, "BUS");
  await page.goto("/admin/vehicles/" + bus.id + "/seats");
  await page
    .getByRole("button", { name: "Tạo sơ đồ chuẩn", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Xác nhận tạo sơ đồ", exact: true })
    .click();
  await expect(
    page.getByText("Đã có sơ đồ chuẩn.", { exact: false }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Thêm mới", exact: true }),
  ).toBeDisabled();
  const journey = await trip(request, bus);
  await page.goto("/trips/" + journey.id);
  await expect(
    page.getByRole("button", { name: /^Giường \d+ · Tầng [12], còn trống$/ }),
  ).toHaveCount(34);
  await expect(
    page
      .getByRole("group", { name: "Tầng 1, 17 giường", exact: true })
      .getByRole("button"),
  ).toHaveCount(17);
  await expect(
    page
      .getByRole("group", { name: "Tầng 2, 17 giường", exact: true })
      .getByRole("button"),
  ).toHaveCount(17);
  await page
    .getByRole("button", { name: "Giường 1 · Tầng 1, còn trống", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Giường 34 · Tầng 2, còn trống", exact: true })
    .click();
  await screenshot(page, "bus-sleeper-34-desktop.png");
  await page.setViewportSize({ width: 390, height: 844 });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await screenshot(page, "bus-sleeper-34-mobile.png");
  await page.getByRole("button", { name: "Giữ giường đã chọn" }).click();
  await expect(page).toHaveURL(/\/bookings\/\d+$/);
  const id = page.url().split("/").at(-1);
  const booking = await (
    await request.get(base + "/bookings/" + id, { headers: headers() })
  ).json();
  expect(
    booking.seats.map((seat) => seat.positionNumber).sort((a, b) => a - b),
  ).toEqual([1, 34]);
  expect(booking.seats.map((seat) => seat.deckNumber).sort()).toEqual([1, 2]);
  expect(booking.seats.every((seat) => seat.seatClass === "BUS_SLEEPER")).toBe(
    true,
  );
  const state = await (
    await request.get(base + "/trips/" + journey.id + "/seats")
  ).json();
  expect(state.filter((seat) => seat.status === "HELD")).toHaveLength(2);
  expect(
    (
      await request.post(base + "/bookings/" + id + "/cancel", {
        headers: headers(),
      })
    ).status(),
  ).toBe(200);
});

test("eleven coaches show correct rows and preserve multi-coach selection through payment and tickets", async ({
  page,
  request,
}) => {
  await login(page, request);
  const train = await vehicle(request, "TRAIN");
  expect(
    (
      await request.post(
        base + "/admin/vehicles/" + train.id + "/seat-layout",
        { headers: headers() },
      )
    ).status(),
  ).toBe(200);
  const journey = await trip(request, train);
  await page.goto("/trips/" + journey.id);
  await expect(page.getByRole("tab")).toHaveCount(11);
  await expect(page.getByRole("tabpanel")).toContainText("56 chỗ");
  await expect(page.getByRole("tabpanel").getByRole("button")).toHaveCount(56);
  await page
    .getByRole("button", { name: "Ghế 56 · Toa 1, còn trống", exact: true })
    .click();
  await screenshot(page, "train-seated-56.png");
  await page.getByRole("tab", { name: /^Toa 3 / }).click();
  await expect(page.getByRole("tabpanel")).toContainText("28 chỗ");
  await expect(page.getByRole("tabpanel").getByRole("button")).toHaveCount(28);
  await page
    .getByRole("button", {
      name: "Giường 28 · Toa 3 · Khoang 7 · Tầng 2, còn trống",
      exact: true,
    })
    .click();
  await screenshot(page, "train-sleeper-4.png");
  await page.getByRole("tab", { name: /^Toa 7 / }).click();
  await expect(page.getByRole("tabpanel")).toContainText("42 chỗ");
  await expect(page.getByRole("tabpanel").getByRole("button")).toHaveCount(42);
  await page
    .getByRole("button", {
      name: "Giường 42 · Toa 7 · Khoang 7 · Tầng 3, còn trống",
      exact: true,
    })
    .click();
  await screenshot(page, "train-sleeper-6.png");
  await page.getByRole("tab", { name: /^Toa 11 / }).click();
  await page
    .getByRole("button", {
      name: "Giường 42 · Toa 11 · Khoang 7 · Tầng 3, còn trống",
      exact: true,
    })
    .click();
  await page.getByRole("tab", { name: /^Toa 1 / }).click();
  await expect(
    page.getByRole("button", {
      name: "Ghế 56 · Toa 1, còn trống",
      exact: true,
    }),
  ).toHaveAttribute("aria-pressed", "true");
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("tab", { name: /^Toa 7 / }).click();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await screenshot(page, "train-mobile.png");
  await page.getByRole("button", { name: "Giữ ghế đã chọn" }).click();
  await expect(page).toHaveURL(/\/bookings\/\d+$/);
  const id = page.url().split("/").at(-1);
  const paymentResponse = await request.post(
    base + "/bookings/" + id + "/payments",
    { headers: headers() },
  );
  expect(paymentResponse.status()).toBe(200);
  const payment = await paymentResponse.json();
  const query = new URLSearchParams({
    vnp_TmnCode: process.env.VNPAY_TMN_CODE,
    vnp_TxnRef: payment.txnRef,
    vnp_Amount: "60000000",
    vnp_ResponseCode: "00",
    vnp_TransactionStatus: "00",
    vnp_TransactionNo: "98" + Date.now(),
    vnp_PayDate: new Date()
      .toLocaleString("sv-SE", { timeZone: "Asia/Ho_Chi_Minh" })
      .replace(/[-: ]/g, ""),
  });
  query.sort();
  query.set(
    "vnp_SecureHash",
    createHmac("sha512", process.env.VNPAY_HASH_SECRET)
      .update(query.toString())
      .digest("hex"),
  );
  expect(
    (await (await request.get(base + "/payments/vnpay/ipn?" + query)).json())
      .RspCode,
  ).toBe("00");
  await page.goto("/bookings/" + id + "/tickets");
  await expect(page.locator(".electronic-ticket")).toHaveCount(4);
  await expect(
    page
      .getByText("Giường 28 · Toa 3 · Khoang 7 · Tầng 2", { exact: false })
      .first(),
  ).toBeVisible();
  await expect(
    page
      .getByText("Giường 42 · Toa 11 · Khoang 7 · Tầng 3", { exact: false })
      .first(),
  ).toBeVisible();
});
