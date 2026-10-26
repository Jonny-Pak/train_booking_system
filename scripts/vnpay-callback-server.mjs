import http from "node:http";
import { pathToFileURL } from "node:url";

const ipnPath = "/api/v1/payments/vnpay/ipn";

export function createCallbackServer({ backend = "http://127.0.0.1:8080", frontend = "http://localhost:5173" } = {}) {
  const backendUrl = new URL(backend);
  const frontendUrl = new URL(frontend);
  for (const url of [backendUrl, frontendUrl]) {
    if (!["http:", "https:"].includes(url.protocol) || url.username || url.password
        || url.pathname !== "/" || url.search || url.hash) {
      throw new Error("Callback upstream must be an HTTP(S) origin without credentials or a path");
    }
  }
  return http.createServer(async (request, response) => {
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("Referrer-Policy", "no-referrer");
    response.setHeader("X-Content-Type-Options", "nosniff");
    if (request.method !== "GET") {
      response.writeHead(405, { Allow: "GET" }).end();
      return;
    }
    if (request.url.length > 24000) {
      response.writeHead(414).end();
      return;
    }
    const url = new URL(request.url, "http://callback.local");
    if (url.pathname === "/payment/return") {
      const references = url.searchParams.getAll("vnp_TxnRef");
      const target = new URL("/payment/return", frontendUrl);
      if (references.length === 1 && /^[a-zA-Z0-9_-]{1,100}$/.test(references[0])) {
        target.searchParams.set("vnp_TxnRef", references[0]);
      }
      // Navigation only: no result code is trusted, no database writes and no JWT in the URL.
      response.writeHead(302, { Location: target.href }).end();
      return;
    }
    if (url.pathname !== ipnPath) {
      response.writeHead(404).end();
      return;
    }
    try {
      // Preserve the original query, including repeated fields for the backend to reject.
      // Never forward Authorization, cookies, Origin or client supplied proxy headers.
      const result = await fetch(new URL(ipnPath + url.search, backendUrl), {
        headers: { Accept: "application/json" }, redirect: "error",
        signal: AbortSignal.timeout(8000),
      });
      if (result.status !== 200 || !result.headers.get("content-type")?.includes("application/json")) {
        throw new Error("IPN upstream unavailable");
      }
      const body = await result.json();
      if (!/^[0-9]{2}$/.test(body.RspCode) || typeof body.Message !== "string") {
        throw new Error("Invalid IPN acknowledgement");
      }
      response.writeHead(200, { "Content-Type": "application/json; charset=utf-8" });
      response.end(JSON.stringify({ RspCode: body.RspCode, Message: body.Message }));
    } catch {
      // No fabricated acknowledgement; VNPAY should retry while the backend is unavailable.
      response.writeHead(502, { "Content-Type": "application/json" });
      response.end(JSON.stringify({ RspCode: "99", Message: "Backend unavailable; retry later" }));
    }
  });
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const port = Number(process.env.VNPAY_CALLBACK_PORT || "8787");
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error("Invalid callback port");
  const server = createCallbackServer({
    backend: process.env.VNPAY_CALLBACK_BACKEND_URL,
    frontend: process.env.VNPAY_CALLBACK_FRONTEND_URL,
  });
  server.requestTimeout = 10000;
  server.listen(port, "127.0.0.1", () => console.log("VNPAY callback relay listening on 127.0.0.1:" + port));
}
