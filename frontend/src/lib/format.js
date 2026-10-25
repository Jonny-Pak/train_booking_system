export function money(amount) {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 2,
  }).format(Number(amount));
}

export function time(value) {
  return new Intl.DateTimeFormat("vi-VN", {
    hour: "2-digit",
    minute: "2-digit",
    timeZone: "Asia/Ho_Chi_Minh",
  }).format(new Date(value));
}

export function date(value) {
  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    timeZone: "Asia/Ho_Chi_Minh",
  }).format(new Date(value));
}

export function duration(departure, arrival) {
  const minutes = Math.round((new Date(arrival) - new Date(departure)) / 60000);
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours === 0) return rest + " phút";
  if (rest === 0) return hours + " giờ";
  return hours + " giờ " + rest + " phút";
}

export function remaining(expiresAt, now) {
  const seconds = Math.max(
    0,
    Math.ceil((new Date(expiresAt).getTime() - now) / 1000),
  );
  return (
    Math.floor(seconds / 60)
      .toString()
      .padStart(2, "0") +
    ":" +
    (seconds % 60).toString().padStart(2, "0")
  );
}

export function displayStatus(booking, now = Date.now()) {
  if (
    booking.status === "PENDING" &&
    (booking.holdExpired || new Date(booking.holdExpiresAt).getTime() <= now)
  ) {
    return "EXPIRED";
  }
  return booking.status;
}

export const statusLabels = {
  PENDING: "Đang giữ chỗ",
  PAID: "Đã thanh toán",
  CANCELLED: "Đã hủy",
  EXPIRED: "Đã hết hạn",
};

export function safeReturnPath(path) {
  if (
    typeof path === "string" &&
    path.startsWith("/") &&
    !path.startsWith("//") &&
    !path.includes("\\")
  )
    return path;
  return "/";
}

export function validPassword(password) {
  return (
    password.trim().length > 0 &&
    [...password].length >= 8 &&
    new TextEncoder().encode(password).length <= 72
  );
}
