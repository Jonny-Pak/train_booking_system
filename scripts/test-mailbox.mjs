// Test-only SMTP inbox. No forwarding or production application endpoint.
// Bind to loopback; the verification harness stops this process afterward.
import net from "node:net";
import http from "node:http";

const messages = [];
const smtp = net.createServer((socket) => {
  socket.setEncoding("utf8");
  socket.write("220 localhost test SMTP\r\n");
  let buffer = "";
  let data = false;
  let lines = [];
  let to = [];
  socket.on("error", () => {});
  socket.on("data", (chunk) => {
    buffer += chunk;
    while (buffer.includes("\r\n")) {
      const end = buffer.indexOf("\r\n");
      const line = buffer.slice(0, end);
      buffer = buffer.slice(end + 2);
      if (data) {
        if (line !== ".") {
          lines.push(line.replace(/^\.\./, "."));
          continue;
        }
        const raw = lines.join("\r\n");
        const split = raw.indexOf("\r\n\r\n");
        const headers = raw.slice(0, split);
        let body = raw.slice(split + 4);
        if (/Content-Transfer-Encoding: base64/i.test(headers)) {
          body = Buffer.from(body.replace(/\s/g, ""), "base64").toString("utf8");
        } else if (/Content-Transfer-Encoding: quoted-printable/i.test(headers)) {
          body = body.replace(/=\r\n/g, "").replace(/=([0-9A-F]{2})/gi,
            (_, hex) => String.fromCharCode(parseInt(hex, 16)));
        }
        messages.push({ to, body });
        data = false;
        lines = [];
        to = [];
        socket.write("250 queued\r\n");
      } else if (/^(EHLO|HELO)/i.test(line)) {
        socket.write("250 localhost\r\n");
      } else if (/^RCPT TO:/i.test(line)) {
        to.push(line.match(/<([^>]+)>/)?.[1] || "");
        socket.write("250 ok\r\n");
      } else if (/^DATA$/i.test(line)) {
        data = true;
        socket.write("354 end with dot\r\n");
      } else if (/^QUIT$/i.test(line)) {
        socket.end("221 bye\r\n");
      } else {
        socket.write("250 ok\r\n");
      }
    }
  });
});

const inbox = http.createServer((request, response) => {
  const url = new URL(request.url, "http://localhost");
  response.setHeader("Content-Type", "application/json");
  response.setHeader("Cache-Control", "no-store");
  if (url.pathname !== "/messages") {
    response.writeHead(404).end("{}");
    return;
  }
  const to = url.searchParams.get("to");
  response.end(JSON.stringify(messages.filter((mail) => !to || mail.to.includes(to))));
});

smtp.listen(Number(process.env.MAIL_PORT), "127.0.0.1");
inbox.listen(Number(process.env.TEST_MAIL_HTTP_PORT), "127.0.0.1");
