import { useEffect, useState } from "react";
import { api } from "../lib/api";
import { readSession, writeSession } from "./session";
import { AuthContext } from "./AuthContext";

export function AuthProvider({ children }) {
  const [session, setSession] = useState(readSession);
  const [checking, setChecking] = useState(() => Boolean(readSession()));
  const [notice, setNotice] = useState("");
  const token = session?.accessToken;

  function login(result) {
    writeSession(result);
    setSession(result);
    setChecking(false);
    setNotice("");
  }

  function logout() {
    writeSession(null);
    setSession(null);
    setNotice("");
  }

  useEffect(() => {
    if (!session) return;
    const token = session.accessToken;
    function expire(event) {
      if (event?.detail && event.detail !== token) return;
      writeSession(null);
      setSession(null);
      setChecking(false);
      setNotice(
        "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại để tiếp tục.",
      );
    }
    window.addEventListener("session-expired", expire);
    const timer = window.setTimeout(
      expire,
      Math.max(0, Date.parse(session.expiresAt) - Date.now()),
    );
    return () => {
      window.clearTimeout(timer);
      window.removeEventListener("session-expired", expire);
    };
  }, [session]);

  useEffect(() => {
    if (!token) {
      setChecking(false);
      return;
    }
    const controller = new AbortController();
    api("/auth/me", { token, signal: controller.signal })
      .then((user) => {
        setSession((current) => {
          if (!current || current.accessToken !== token) return current;
          return { ...current, user };
        });
      })
      .catch((error) => {
        if (error.name !== "AbortError" && error.status !== 401) {
          setNotice(
            "Chưa xác minh được phiên với máy chủ. Bạn có thể thử lại thao tác khi có kết nối.",
          );
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setChecking(false);
      });
    return () => controller.abort();
    // Chỉ xác minh khi token đổi, không chạy lại khi cập nhật profile.
  }, [token]);

  return (
    <AuthContext.Provider
      value={{
        session,
        token: session?.accessToken,
        user: session?.user,
        checking,
        notice,
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
