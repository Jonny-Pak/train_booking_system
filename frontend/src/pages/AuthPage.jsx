import { useEffect, useRef, useState } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { Eye, EyeOff, X } from "lucide-react";
import { api } from "../lib/api";
import { safeReturnPath, validPassword } from "../lib/format";
import { useAuth } from "../auth/AuthContext";
import { ErrorNotice } from "../components/Feedback";

export default function AuthPage({ register = false }) {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, login, notice } = useAuth();
  const [form, setForm] = useState({
    fullName: "",
    email: location.state?.email || "",
    password: "",
    phone: "",
  });
  const [showPassword, setShowPassword] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const target = safeReturnPath(location.state?.from);
  const dialogRef = useRef(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (!dialog.open) dialog.showModal();
    dialog.querySelector("input")?.focus();
    return () => dialog.close();
  }, []);

  function closeDialog() {
    if (busy) return;
    const destination = target.startsWith("/bookings") ? "/" : target;
    navigate(destination, {
      replace: true,
      state: { seatIds: location.state?.seatIds },
    });
  }

  function handleCancel(event) {
    event.preventDefault();
    closeDialog();
  }

  function handleKeyDown(event) {
    if (event.key !== "Tab") return;
    const controls = event.currentTarget.querySelectorAll(
      "button:not(:disabled), input:not(:disabled), a[href]",
    );
    const first = controls[0];
    const last = controls[controls.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  if (user)
    return (
      <Navigate
        to={target}
        replace
        state={{ seatIds: location.state?.seatIds }}
      />
    );

  function update(event) {
    setForm({ ...form, [event.target.name]: event.target.value });
  }

  async function submit(event) {
    event.preventDefault();
    setError(null);
    if (register && !validPassword(form.password)) {
      setError("Mật khẩu cần ít nhất 8 ký tự và không quá 72 byte UTF-8.");
      return;
    }
    if (register && !form.fullName.trim()) {
      setError("Vui lòng nhập họ và tên của bạn.");
      return;
    }
    setBusy(true);
    try {
      if (register) {
        await api("/auth/register", {
          method: "POST",
          body: {
            fullName: form.fullName.trim(),
            email: form.email.trim(),
            password: form.password,
            phone: form.phone.trim() || null,
          },
        });
        navigate("/login", {
          replace: true,
          state: { ...location.state, registered: true, email: form.email },
        });
      } else {
        const result = await api("/auth/login", {
          method: "POST",
          body: { email: form.email.trim(), password: form.password },
        });
        login(result);
        navigate(target, {
          replace: true,
          state: { seatIds: location.state?.seatIds },
        });
      }
    } catch (failure) {
      setError(failure);
    } finally {
      setBusy(false);
    }
  }

  return (
    <dialog
      ref={dialogRef}
      className="auth-dialog"
      aria-labelledby="auth-title"
      onCancel={handleCancel}
      onKeyDown={handleKeyDown}
    >
      <div className="auth-dialog-content">
        <header className="auth-dialog-heading">
          <h1 id="auth-title">{register ? "Đăng ký" : "Đăng nhập"}</h1>
          <button
            type="button"
            className="auth-close"
            aria-label={register ? "Đóng đăng ký" : "Đóng đăng nhập"}
            disabled={busy}
            onClick={closeDialog}
          >
            <X size={27} />
          </button>
        </header>
        <p className="auth-dialog-description">
          {register
            ? "Tạo tài khoản để bắt đầu hành trình của bạn."
            : "Đăng nhập bằng email để đặt vé và quản lý đơn hàng."}
        </p>
        {location.state?.registered && (
          <div className="success-notice" role="status">
            Đăng ký thành công. Bạn có thể đăng nhập ngay.
          </div>
        )}
        {notice && (
          <p className="info-notice" role="status">
            {notice}
          </p>
        )}
        <ErrorNotice error={error} />
        <form className="auth-dialog-form" onSubmit={submit}>
          {register && (
            <label>
              Họ và tên
              <input
                name="fullName"
                autoComplete="name"
                autoFocus
                required
                maxLength={120}
                value={form.fullName}
                onChange={update}
                placeholder="Nhập họ và tên"
              />
            </label>
          )}
          <label>
            Email
            <input
              name="email"
              type="email"
              autoComplete="email"
              autoFocus={!register}
              required
              maxLength={190}
              value={form.email}
              onChange={update}
              placeholder="Nhập địa chỉ email"
            />
          </label>
          <label>
            Mật khẩu
            <span className="auth-password">
              <input
                name="password"
                type={showPassword ? "text" : "password"}
                autoComplete={register ? "new-password" : "current-password"}
                required
                value={form.password}
                onChange={update}
                placeholder={
                  register ? "Tạo mật khẩu (ít nhất 8 ký tự)" : "Nhập mật khẩu"
                }
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
              >
                {showPassword ? <EyeOff size={21} /> : <Eye size={21} />}
              </button>
            </span>
          </label>
          {register && (
            <>
              <p className="auth-password-hint">
                Mật khẩu có ít nhất 8 ký tự, tối đa 72 byte UTF-8.
              </p>
              <label>
                Số điện thoại{" "}
                <span className="auth-optional">(không bắt buộc)</span>
                <input
                  name="phone"
                  type="tel"
                  autoComplete="tel"
                  maxLength={20}
                  value={form.phone}
                  onChange={update}
                  placeholder="Nhập số điện thoại"
                />
              </label>
            </>
          )}
          <button className="auth-submit" disabled={busy}>
            {busy ? "Đang xử lý…" : register ? "Tạo tài khoản" : "Đăng nhập"}
          </button>
        </form>
        <div className="auth-divider">
          <span>hoặc</span>
        </div>
        <p className="auth-dialog-switch">
          {register ? "Bạn đã có tài khoản?" : "Bạn chưa có tài khoản?"}{" "}
          <Link
            to={register ? "/login" : "/register"}
            state={{ from: target, seatIds: location.state?.seatIds }}
          >
            {register ? "Đăng nhập" : "Đăng ký ngay"}
          </Link>
        </p>
        <Link
          className="auth-back-home"
          to={target.startsWith("/bookings") ? "/" : target}
          state={{ seatIds: location.state?.seatIds }}
        >
          Tiếp tục xem chuyến đi
        </Link>
      </div>
    </dialog>
  );
}
