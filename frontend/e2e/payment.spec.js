import { test, expect } from "@playwright/test";
import { createHmac, randomUUID } from "node:crypto";
import { mkdir } from "node:fs/promises";
import path from "node:path";

test.skip(
  process.env.TEST_P6 !== "true",
  "Run with scripts/Test-P6.ps1 and an isolated merchant fixture",
);
const api =
  (process.env.API_PROXY_TARGET || "http://localhost:8080") + "/api/v1";

async function account(request) {
  const email = "p6-" + randomUUID() + "@example.invalid";
  const password = randomUUID() + "Aa1!";
  expect(
    (
      await request.post(api + "/auth/register", {
        data: { fullName: "Khách P6", email, password },
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
      await request.post(api + "/auth/verify-email", { data: { token } })
    ).status(),
  ).toBe(204);
  const session = await (
    await request.post(api + "/auth/login", { data: { email, password } })
  ).json();
  return { email, password, session };
}

async function book(request, owner, codes) {
  const trips = await (
    await request.get(api + "/trips?vehicleType=BUS")
  ).json();
  const trip = trips.content.find(
    (trip) => trip.vehicleCode === "DEMO-P3-BUS-01",
  );
  const seats = await (
    await request.get(api + "/trips/" + trip.id + "/seats")
  ).json();
  const response = await request.post(api + "/bookings", {
    headers: { Authorization: "Bearer " + owner.session.accessToken },
    data: {
      tripId: trip.id,
      seatIds: seats
        .filter((seat) => codes.includes(seat.seatCode))
        .map((seat) => seat.id),
    },
  });
  expect(response.status()).toBe(201);
  return response.json();
}

async function login(page, owner) {
  await page.goto("/login");
  await page.getByLabel("Email", { exact: true }).fill(owner.email);
  await page.locator('input[name="password"]').fill(owner.password);
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page.getByText("Chào, Khách P6")).toBeVisible();
}

function callback(payment, success = true, transaction = "701010") {
  const date = new Date()
    .toLocaleString("sv-SE", { timeZone: "Asia/Ho_Chi_Minh" })
    .replace(/[-: ]/g, "");
  return {
    vnp_TmnCode: process.env.VNPAY_TMN_CODE,
    vnp_TxnRef: payment.txnRef,
    vnp_Amount: String(Number(payment.amount) * 100),
    vnp_ResponseCode: success ? "00" : "24",
    vnp_TransactionStatus: success ? "00" : "02",
    vnp_TransactionNo: success ? transaction : "0",
    vnp_PayDate: date,
  };
}

async function notify(request, fields) {
  const query = new URLSearchParams(fields);
  query.sort();
  query.set(
    "vnp_SecureHash",
    createHmac("sha512", process.env.VNPAY_HASH_SECRET)
      .update(query.toString())
      .digest("hex"),
  );
  const response = await request.get(
    api + "/payments/vnpay/ipn?" + query.toString(),
  );
  expect(response.status()).toBe(200);
  return response.json();
}

async function screenshot(page, name) {
  const directory = process.env.TEST_ARTIFACT_DIR || "test-results/screenshots";
  await mkdir(directory, { recursive: true });
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({ path: path.join(directory, name), fullPage: true });
}

test("signed IPN confirms payment, duplicate callbacks preserve two QR tickets and tickets survive reload", async ({
  page,
  request,
}) => {
  const owner = await account(request);
  await login(page, owner);
  const booking = await book(request, owner, ["A7", "A8"]);
  await page.goto("/bookings/" + booking.id);
  let gatewayUrl;
  // Intercept navigation before leaving the machine. Never contact a real gateway during E2E.
  await page.route("https://sandbox.vnpayment.vn/**", async (route) => {
    gatewayUrl = new URL(route.request().url());
    await route.fulfill({
      contentType: "text/html; charset=utf-8",
      body: "<h1>Cổng thanh toán giả lập phục vụ kiểm thử</h1>",
    });
  });
  await page
    .getByRole("button", { name: "Thanh toán VNPAY Sandbox", exact: true })
    .click();
  await expect(
    page.getByRole("heading", {
      name: "Cổng thanh toán giả lập phục vụ kiểm thử",
    }),
  ).toBeVisible();
  expect(gatewayUrl.searchParams.get("vnp_Amount")).toBe("30000000");
  const signed = new URLSearchParams(gatewayUrl.search);
  const received = signed.get("vnp_SecureHash");
  signed.delete("vnp_SecureHash");
  signed.sort();
  expect(
    createHmac("sha512", process.env.VNPAY_HASH_SECRET)
      .update(signed.toString())
      .digest("hex"),
  ).toBe(received);
  const headers = { Authorization: "Bearer " + owner.session.accessToken };
  const payment = await (
    await request.get(
      api + "/payments/" + gatewayUrl.searchParams.get("vnp_TxnRef"),
      { headers },
    )
  ).json();
  const returnUrl =
    gatewayUrl.searchParams.get("vnp_ReturnUrl") +
    "?vnp_TxnRef=" +
    payment.txnRef +
    "&vnp_ResponseCode=00";
  await page.goto(returnUrl);
  await expect(
    page.getByRole("heading", { name: "Đang chờ xác nhận thanh toán" }),
  ).toBeVisible();
  expect(
    (
      await (
        await request.get(api + "/bookings/" + booking.id, { headers })
      ).json()
    ).status,
  ).toBe("PENDING");
  const fields = callback(payment);
  expect((await notify(request, fields)).RspCode).toBe("00");
  expect((await notify(request, fields)).RspCode).toBe("02");
  await expect(
    page.getByRole("heading", { name: "Thanh toán thành công", exact: true }),
  ).toBeVisible();
  await screenshot(page, "payment-success-desktop.png");
  await page.getByRole("link", { name: "Xem vé điện tử", exact: true }).click();
  await expect(page.locator(".electronic-ticket")).toHaveCount(2);
  await expect(page.getByRole("img", { name: "QR vé ghế A7" })).toBeVisible();
  await screenshot(page, "tickets-desktop.png");
  await page.reload();
  await expect(page.locator(".electronic-ticket")).toHaveCount(2);
  await page.setViewportSize({ width: 375, height: 812 });
  await screenshot(page, "tickets-mobile.png");
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.emulateMedia({ media: "print" });
  await expect(page.locator(".site-header")).toBeHidden();
  await expect(page.locator(".ticket-actions").first()).toBeHidden();
  expect(
    await (
      await request.get(api + "/bookings/" + booking.id + "/tickets", {
        headers,
      })
    ).json(),
  ).toHaveLength(2);
});

test("failed payment can retry, but success after cancellation shows reconciliation without a ticket", async ({
  page,
  request,
}) => {
  const owner = await account(request);
  await login(page, owner);
  const booking = await book(request, owner, ["A2"]);
  const headers = { Authorization: "Bearer " + owner.session.accessToken };
  let payment = await (
    await request.post(api + "/bookings/" + booking.id + "/payments", {
      headers,
    })
  ).json();
  expect((await notify(request, callback(payment, false))).RspCode).toBe("00");
  await page.goto("/payment/return?vnp_TxnRef=" + payment.txnRef);
  await expect(
    page.getByRole("heading", { name: "Thanh toán chưa thành công" }),
  ).toBeVisible();
  await page
    .getByRole("link", { name: "Chi tiết đơn hàng", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Thanh toán VNPAY Sandbox", exact: true }),
  ).toBeEnabled();
  payment = await (
    await request.post(api + "/bookings/" + booking.id + "/payments", {
      headers,
    })
  ).json();
  expect(
    (
      await request.post(api + "/bookings/" + booking.id + "/cancel", {
        headers,
      })
    ).ok(),
  ).toBeTruthy();
  expect(
    (await notify(request, callback(payment, true, "702020"))).RspCode,
  ).toBe("00");
  await page.goto("/payment/return?vnp_TxnRef=" + payment.txnRef);
  await expect(
    page.getByRole("heading", { name: "Giao dịch cần được đối soát" }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Xem vé điện tử", exact: true }),
  ).toHaveCount(0);
  expect(
    await (
      await request.get(api + "/bookings/" + booking.id + "/tickets", {
        headers,
      })
    ).json(),
  ).toHaveLength(0);
  await screenshot(page, "payment-reconciliation.png");
});
