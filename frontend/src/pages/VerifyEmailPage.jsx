import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../lib/api";
import { safeReturnPath } from "../lib/format";
import { ErrorNotice } from "../components/Feedback";

export default function VerifyEmailPage() {
  const [token] = useState(
    () => new URLSearchParams(window.location.hash.slice(1)).get("token") || "",
  );
  const [email, setEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [verified, setVerified] = useState(false);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState("");
  const [returnState, setReturnState] = useState({ from: "/" });

  useEffect(() => {
    // Keep the token only in component memory, never local/session storage.
    window.history.replaceState(
      window.history.state,
      "",
      window.location.pathname + window.location.search,
    );
  }, []);

  async function verify() {
    setBusy(true);
    setError(null);
    try {
      await api("/auth/verify-email", { method: "POST", body: { token } });
      setVerified(true);
      try {
        const saved = JSON.parse(
          sessionStorage.getItem("email-verification-return"),
        );
        setReturnState({
          from: safeReturnPath(saved?.from),
          seatIds: saved?.seatIds,
        });
      } catch {
        /* A new browser can continue from the home page. */
      }
      sessionStorage.removeItem("email-verification-return");
    } catch (failure) {
      setError(failure);
    } finally {
      setBusy(false);
    }
  }

  async function resend(event) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setNotice("");
    try {
      await api("/auth/resend-verification", {
        method: "POST",
        body: { email: email.trim() },
      });
      setNotice(
        "Nếu email đang chờ xác nhận, hệ thống sẽ gửi link mới. Kiểm tra cả thư rác; mỗi lần gửi cách nhau ít nhất 60 giây.",
      );
    } catch (failure) {
      setError(failure);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="verification-card">
      <h1>{verified ? "Email đã được xác nhận" : "Xác nhận email"}</h1>
      {verified ? (
        <>
          <p role="status">
            Tài khoản đã sẵn sàng. Bạn có thể đăng nhập để đặt vé.
          </p>
          <Link className="button" to="/login" state={returnState}>
            Đăng nhập
          </Link>
        </>
      ) : (
        <>
          <p>
            Link xác nhận chỉ dùng một lần và có thời hạn. Nhấn nút bên dưới để
            xác nhận tài khoản.
          </p>
          <ErrorNotice error={error} />
          {token ? (
            <button className="button" disabled={busy} onClick={verify}>
              {busy ? "Đang xử lý…" : "Xác nhận email"}
            </button>
          ) : (
            <p role="alert">
              Thiếu link xác nhận. Hãy mở link trong email hoặc yêu cầu gửi lại.
            </p>
          )}
          <hr />
          <h2>Gửi lại email xác nhận</h2>
          <form onSubmit={resend}>
            <label>
              Email
              <input
                type="email"
                autoComplete="email"
                required
                maxLength={190}
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                placeholder="Nhập email đã đăng ký"
              />
            </label>
            <button className="button secondary" disabled={busy}>
              Gửi lại email xác nhận
            </button>
          </form>
          {notice && (
            <p className="info-notice" role="status">
              {notice}
            </p>
          )}
        </>
      )}
    </section>
  );
}
