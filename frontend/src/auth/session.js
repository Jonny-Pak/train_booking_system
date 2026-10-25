const key = "len-duong-session";

export function readSession() {
  try {
    const value = JSON.parse(sessionStorage.getItem(key));
    if (
      typeof value?.accessToken !== "string" ||
      !value.accessToken ||
      !Number.isFinite(Date.parse(value.expiresAt)) ||
      Date.parse(value.expiresAt) <= Date.now()
    ) {
      sessionStorage.removeItem(key);
      return null;
    }
    return value;
  } catch {
    return null;
  }
}

export function writeSession(session) {
  try {
    if (session) sessionStorage.setItem(key, JSON.stringify(session));
    else sessionStorage.removeItem(key);
  } catch {
    // Trình duyệt chặn storage: phiên vẫn dùng được trong bộ nhớ đến lúc tải lại.
  }
}
