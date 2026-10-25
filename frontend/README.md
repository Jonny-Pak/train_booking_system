# Lên Đường — React Customer

React 19 + Vite 8 + React Router, JavaScript và CSS thuần. Node 24+; phiên bản dependency được khóa trong package-lock.json. UI tiếng Việt, responsive; icon Lucide và font Be Vietnam Pro đóng gói cục bộ.

## Chạy development

Chạy backend ở terminal riêng theo README gốc, rồi:

```powershell
cd frontend
npm ci
npm run dev
```

Mở http://127.0.0.1:5173. Vite proxy `/api` đến backend `http://localhost:8080`.
Backend dùng PostgreSQL và .env gốc hiện có; frontend không đọc mật khẩu database.

Nếu cần backend URL khác, sao chép .env.example thành .env.local rồi sửa:

```properties
VITE_API_BASE_URL=/api/v1
API_PROXY_TARGET=http://localhost:8080
```

Chỉ biến `VITE_*` được đưa vào bundle trình duyệt: không đặt DB_PASSWORD, JWT_SECRET hoặc VNPAY secret trong frontend.

## Route

| Path | Trang |
|---|---|
| / | Tìm chuyến và phân trang |
| /login | Đăng nhập |
| /register | Đăng ký CUSTOMER |
| /trips/:id | Chi tiết chuyến, trạng thái ghế, tạo booking |
| /bookings | Lịch sử đơn của tài khoản hiện tại |
| /bookings/:id | Chi tiết, đếm ngược giữ ghế, hủy đơn |

API thật từ backend P2–P4; không có dữ liệu thành công giả khi mất kết nối. Admin có thể dùng các thao tác Customer cho tài khoản của mình; giao diện quản trị chưa thuộc P5.

## Cấu trúc

- `src/pages/`: từng trang nghiệp vụ.
- `src/components/`: card chuyến, badge trạng thái, phân trang, loading/error/empty.
- `src/auth/`: session trong tab, xác minh /me, route guard.
- `src/lib/`: API client, định dạng tiền/thời gian, trạng thái hold.
- `src/hooks/useNow.js`: đồng hồ hiển thị; không quyết định trạng thái backend.
- `src/styles.css`: giao diện chung, responsive và reduced motion.
- `src/home.css`: homepage theo mẫu tham khảo, header xanh, banner xe/tàu và form tìm kiếm.
- `e2e/`: luồng trình duyệt với backend thật.

Không dùng Redux, UI framework lớn, interceptor hoặc tự refresh JWT. Token nằm trong sessionStorage của tab; tải lại gọi /auth/me để xác minh. Logout chỉ xóa phiên phía client; token đã cấp vẫn có thể hợp lệ đến exp theo thiết kế P2.

## Kiểm thử

```powershell
npm run lint
npm test
npm run build
```

Kiểm chứng đầy đủ từ repository root:

```powershell
.\scripts\Test-P5.ps1 -JavaHome 'C:\Program Files\Java\jdk-21.0.12' -Offline
```

Cần Edge đã cài (Playwright dùng channel msedge mặc định). Có thể cấu hình E2E_BROWSER_CHANNEL=chrome nếu có Chrome. Script tạo cluster PostgreSQL riêng, chạy backend tests, seed demo có kiểm soát và mở Vite/Edge tạm để thử UI. Không trỏ E2E vào backend chứa dữ liệu thật: tests đăng ký khách và tạo/hủy đơn. Ở lượt E2E, hold rút còn 30 giây để kiểm tra hết hạn; cấu hình local bình thường vẫn 600 giây.

Ảnh giao diện nằm trong .local/p5-*/screenshots/. Playwright report/trace ở frontend/playwright-report và frontend/test-results; đều bị Git bỏ qua.

## Production build

```powershell
npm run build
npm run preview -- --host 127.0.0.1 --port 4173
```

Vite proxy chỉ dành cho dev, không chạy trong preview hoặc production. Để thử preview, build với VITE_API_BASE_URL là URL backend đầy đủ, ví dụ http://localhost:8080/api/v1, và thêm origin frontend http://127.0.0.1:4173 vào APP_CORS_ALLOWED_ORIGINS của backend. Không dùng wildcard hoặc bật cookie credentials.

Production phục vụ dist/ qua web server, cấu hình SPA fallback về index.html cho /trips/* và /bookings/*; reverse proxy /api đến backend hoặc build với API URL đầy đủ. Dùng HTTPS cho frontend và backend. Đây là hướng dẫn triển khai; P5 chưa publish website.

## Quy tắc hiển thị

Ngày/giờ theo Asia/Ho_Chi_Minh, tiền VND. Giá tạm tính chỉ hỗ trợ chọn ghế; tổng chính thức lấy từ booking response. Ghế được chọn chưa được giữ cho đến khi POST thành công. Conflict 409 tải lại sơ đồ và bỏ lựa chọn cũ, không tự gửi lại POST.

Deadline được tính để hiển thị countdown; trường holdExpired và dữ liệu backend luôn được ưu tiên. Chi tiết đơn cập nhật mỗi 15 giây, tạm ngừng polling trong lúc hủy để tránh response cũ ghi đè. Lịch sử phản ánh hết hạn ngay khi quá deadline dù scheduler chưa cập nhật status lưu.

Sơ đồ là danh sách ghế minh họa vì schema chưa có tọa độ/toa/tầng. Khi chuyến đã khởi hành và không còn public, chi tiết đơn vẫn hiện ID chuyến, ghế và snapshot giá; không làm hỏng lịch sử vì public trip trả 404.

Thanh toán, IPN và vé điện tử ở P6. Giao diện hiện chỉ tạo và quản lý đơn giữ chỗ, không tự chuyển PAID.
