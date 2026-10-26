import { test } from "node:test";
import assert from "node:assert/strict";
import http from "node:http";
import { once } from "node:events";
import { createCallbackServer } from "./vnpay-callback-server.mjs";

async function listen(server) {
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  return "http://127.0.0.1:" + server.address().port;
}
async function close(server) {
  server.closeAllConnections();
  await new Promise(resolve => server.close(resolve));
}

test("relay preserves callback query and strips authentication and untrusted headers", async () => {
  let received;
  const backend = http.createServer((req, res) => {
    received = { url: req.url, headers: req.headers };
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ RspCode: "97", Message: "Invalid signature or merchant" }));
  });
  const backendUrl = await listen(backend);
  const relay = createCallbackServer({ backend: backendUrl });
  const relayUrl = await listen(relay);
  try {
    const query = "?vnp_TxnRef=A%2BB&vnp_Amount=1&vnp_Amount=2&vnp_OrderInfo=Thanh+toan";
    const response = await fetch(relayUrl + "/api/v1/payments/vnpay/ipn" + query, { headers: {
      Authorization: "Bearer untrusted", Cookie: "private=secret", Origin: "https://attacker.example", "X-Forwarded-For": "1.2.3.4",
    } });
    assert.equal(response.status, 200);
    assert.equal((await response.json()).RspCode, "97");
    assert.equal(received.url, "/api/v1/payments/vnpay/ipn" + query);
    for (const name of ["authorization", "cookie", "origin", "x-forwarded-for"]) assert.equal(received.headers[name], undefined);
  } finally { await close(relay); await close(backend); }
});

test("return only redirects to the configured frontend with one safe transaction reference", async () => {
  const relay = createCallbackServer();
  const url = await listen(relay);
  try {
    const response = await fetch(url + "/payment/return?vnp_TxnRef=attempt-1&vnp_ResponseCode=00&next=https://attacker.example", { redirect: "manual" });
    assert.equal(response.status, 302);
    assert.equal(response.headers.get("location"), "http://localhost:5173/payment/return?vnp_TxnRef=attempt-1");
    assert.equal(response.headers.get("cache-control"), "no-store");
    const duplicate = await fetch(url + "/payment/return?vnp_TxnRef=one&vnp_TxnRef=two", { redirect: "manual" });
    assert.equal(duplicate.headers.get("location"), "http://localhost:5173/payment/return");
    assert.equal((await fetch(url + "/api/v1/admin/statistics")).status, 404);
    assert.equal((await fetch(url + "/.env")).status, 404);
    assert.equal((await fetch(url + "/api/v1/payments/vnpay/ipn", { method: "POST" })).status, 405);
  } finally { await close(relay); }
});

test("an unavailable or non-JSON backend never receives a successful acknowledgement", async () => {
  const backend = http.createServer((req, res) => res.writeHead(200, { "Content-Type": "text/html" }).end("unavailable"));
  const backendUrl = await listen(backend);
  const relay = createCallbackServer({ backend: backendUrl });
  const url = await listen(relay);
  try {
    const response = await fetch(url + "/api/v1/payments/vnpay/ipn");
    assert.equal(response.status, 502);
    assert.equal((await response.json()).RspCode, "99");
    await close(backend);
    assert.equal((await fetch(url + "/api/v1/payments/vnpay/ipn")).status, 502);
  } finally { await close(relay); }
});

test("upstreams cannot include credentials, paths or non-HTTP protocols", () => {
  for (const backend of ["file:///secret", "http://user:pass@localhost:8080", "http://localhost:8080/admin"]) {
    assert.throws(() => createCallbackServer({ backend }));
  }
});
