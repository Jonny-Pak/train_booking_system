const baseUrl = (import.meta.env.VITE_API_BASE_URL || "/api/v1").replace(
  /\/$/,
  "",
);

const messages = {
  INVALID_CREDENTIALS: "Email hoặc mật khẩu chưa đúng.",
  EMAIL_ALREADY_EXISTS:
    "Email này đã được đăng ký. Hãy đăng nhập hoặc dùng email khác.",
  SEAT_UNAVAILABLE:
    "Một ghế bạn chọn vừa được người khác giữ. Vui lòng chọn lại.",
  TRIP_NOT_BOOKABLE: "Chuyến này đã đóng đặt chỗ hoặc đã khởi hành.",
  CATALOG_INACTIVE: "Chuyến này hiện ngừng nhận đặt chỗ.",
  BOOKING_ALREADY_PAID: "Đơn đã thanh toán. Bạn chưa thể hủy trực tuyến.",
  DUPLICATE_SEAT: "Bạn đã chọn trùng ghế. Vui lòng chọn lại.",
  SEAT_WRONG_VEHICLE: "Ghế không thuộc chuyến này. Vui lòng tải lại sơ đồ.",
  BOOKING_TOTAL_TOO_LARGE:
    "Tổng tiền vượt giới hạn hỗ trợ. Vui lòng chọn ít ghế hơn.",
  INVALID_PAGE: "Trang không hợp lệ. Vui lòng quay về trang đầu.",
};

export class ApiError extends Error {
  constructor(message, status = 0, code = "NETWORK_ERROR", fields = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fields = fields;
  }
}

export async function api(path, { method = "GET", body, token, signal } = {}) {
  const headers = { Accept: "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (token) headers.Authorization = "Bearer " + token;

  let response;
  try {
    response = await fetch(baseUrl + path, {
      method,
      headers,
      signal,
      credentials: "omit",
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (error) {
    if (error.name === "AbortError") throw error;
    throw new ApiError(
      "Không thể kết nối máy chủ. Vui lòng kiểm tra kết nối rồi thử lại.",
    );
  }

  if (response.status === 204) return null;
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    if (response.status === 401 && token) {
      window.dispatchEvent(
        new CustomEvent("session-expired", { detail: token }),
      );
    }
    let message = messages[data?.code];
    if (!message && response.status === 401)
      message = "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
    if (!message && response.status === 403)
      message = "Bạn không có quyền thực hiện thao tác này.";
    if (!message && response.status === 404)
      message = "Không tìm thấy dữ liệu hoặc bạn không có quyền truy cập.";
    if (!message && response.status === 400)
      message = "Thông tin chưa hợp lệ. Vui lòng kiểm tra lại.";
    if (!message) message = "Chưa thể hoàn tất yêu cầu. Vui lòng thử lại sau.";
    throw new ApiError(message, response.status, data?.code, data?.fieldErrors);
  }
  if (data === null)
    throw new ApiError("Máy chủ trả dữ liệu không hợp lệ. Vui lòng thử lại.");
  return data;
}

export function tripQuery(filters, page = 0) {
  const query = new URLSearchParams({ page: String(page), size: "6" });
  for (const key of ["origin", "destination", "date", "vehicleType"]) {
    const value = filters[key]?.trim();
    if (value) query.set(key, value);
  }
  return "/trips?" + query.toString();
}
