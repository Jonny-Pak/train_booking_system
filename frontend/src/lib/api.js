const baseUrl = (import.meta.env.VITE_API_BASE_URL || "/api/v1").replace(
  /\/$/,
  "",
);

const messages = {
  SITE_CHANGED:
    "Nội dung đã được cập nhật ở phiên khác. Tải lại trang trước khi lưu để tránh ghi đè.",
  GUIDE_EMPTY: "Hướng dẫn phải có nội dung văn bản.",
  INVALID_LOGO: "Ảnh phải là PNG/JPG tối đa 2 MB, mỗi chiều từ 16 đến 4096 px.",
  STANDARD_LAYOUT_LOCKED:
    "Sơ đồ chuẩn đã cố định số chỗ. Không thể thêm, sửa hoặc xóa riêng lẻ.",
  INVALID_DATE_RANGE:
    "Chọn khoảng ngày hợp lệ, tối đa 366 ngày (từ năm 2000 đến 2100).",
  INVALID_ROUTE: "Điểm đi và điểm đến phải khác nhau.",
  VEHICLE_CODE_EXISTS: "Mã phương tiện đã tồn tại.",
  SEAT_CODE_EXISTS: "Mã ghế đã tồn tại trong phương tiện.",
  VEHICLE_SCHEDULE_CONFLICT: "Phương tiện đã có chuyến trùng lịch.",
  ROUTE_IN_USE:
    "Tuyến đã có chuyến; không thể xóa hoặc thay đổi điểm đi/đến. Bạn có thể ngừng hoạt động.",
  VEHICLE_IN_USE:
    "Phương tiện đã có chuyến hoặc ghế; không thể xóa hoặc thay đổi loại.",
  TRIP_HAS_BOOKINGS: "Chuyến đã có lịch sử đặt vé; không thể sửa hoặc xóa.",
  SEATS_IN_USE: "Phương tiện đã có lịch sử đặt vé; không thể thay đổi ghế.",
  VEHICLE_HAS_NO_SEATS: "Hãy tạo ghế cho phương tiện trước khi tạo chuyến.",
  INVALID_TRIP_TIME: "Giờ đến phải sau giờ khởi hành.",
  TRIP_IN_PAST: "Chuyến mới phải khởi hành trong tương lai.",
  TRIP_TERMINAL: "Chuyến đã hủy hoặc hoàn tất; không thể sửa tiếp.",
  TRIP_NOT_FINISHED:
    "Chuyến chưa đến giờ kết thúc; chưa thể đánh dấu hoàn tất.",
  DATA_CONFLICT:
    "Dữ liệu bị trùng hoặc đang được sử dụng. Hãy tải lại và kiểm tra.",
  VNPAY_NOT_CONFIGURED:
    "Thanh toán VNPAY chưa được cấu hình. Vui lòng liên hệ quản trị viên.",
  BOOKING_NOT_PAYABLE:
    "Đơn đã đóng hoặc sắp hết thời gian giữ ghế. Vui lòng kiểm tra lại đơn.",
  PAYMENT_RECONCILIATION_REQUIRED:
    "Đơn đã ghi nhận giao dịch cần đối soát. Vui lòng liên hệ hỗ trợ trước khi thanh toán lại.",
  INVALID_PAYMENT_AMOUNT: "Số tiền của đơn chưa phù hợp để thanh toán VNPAY.",
  INVALID_CREDENTIALS: "Email hoặc mật khẩu chưa đúng.",
  EMAIL_NOT_VERIFIED:
    "Bạn cần xác nhận email trước khi đăng nhập. Hãy kiểm tra hộp thư hoặc gửi lại email xác nhận.",
  EMAIL_SEND_FAILED:
    "Chưa gửi được email xác nhận. Vui lòng thử lại sau hoặc liên hệ quản trị viên.",
  INVALID_VERIFICATION_TOKEN:
    "Link xác nhận không hợp lệ, đã hết hạn hoặc đã được sử dụng. Vui lòng yêu cầu link mới.",
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
    if (!message && (response.status === 502 || response.status === 504))
      message = "Máy chủ tạm thời không phản hồi. Vui lòng thử lại sau.";
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

export function siteLogoUrl(revision) {
  return baseUrl + "/site/logo?v=" + revision;
}
