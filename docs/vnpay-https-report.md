# Kiểm tra và cấu hình VNPAY HTTPS — 26/10/2026

## Phần đã có và được giữ nguyên

- Spring Boot `/api/v1/bookings/{id}/payments` tạo URL từ số tiền trong database, chỉ chủ đơn có quyền gọi.
- React `/payment/return` không xác nhận tiền; chỉ tra cứu payment qua API riêng có JWT.
- Public GET `/api/v1/payments/vnpay/ipn` kiểm tra HMAC-SHA512, merchant, ref, amount, provider status/date và mã giao dịch.
- Transaction khóa Trip → Booking → Payment, phát hành vé và PAID/SUCCESS nguyên tử; rollback khi ghi vé thất bại, trùng callback không tạo thêm vé.
- Giữ uniqueness PostgreSQL, xử lý tiền đến muộn bằng đối soát; không sửa migration, các API catalog/booking/admin hoặc CORS/Spring Security đang hoạt động.

## Phần thiếu trước khi sửa

- Root `.env` tắt VNPAY, chưa có TMN Code/Secret và IPN URL; Return còn localhost.
- Chưa có public HTTPS hoặc hướng dẫn chi tiết để VNPAY gọi vào máy local.
- Gateway URL chưa có env riêng; validation Return chưa bắt buộc HTTPS khi dùng domain công khai.

## Thay đổi

- `VNPAY_PAYMENT_URL` được đọc từ environment, mặc định và chỉ chấp nhận gateway Sandbox.
- Return domain công khai bắt buộc HTTPS; localhost HTTP vẫn được dùng trong local tests.
- `scripts/vnpay-callback-server.mjs`: relay loopback 8787 chỉ phục vụ GET IPN/Return. IPN nguyên query tới Spring; Return 302 về frontend giữ sessionStorage, không truyền response code/JWT, không ghi database.
- `Set-VnpayPublicUrl.ps1`: đặt hai public URLs trong `.env` và bật payment khi có đủ keys và có flag yêu cầu; giữ cấu hình khác.
- `Test-VnpayPublicUrls.ps1`: kiểm tra HTTPS IPN không ký bị từ chối, Return chỉ redirect và Admin không công khai.
- Hướng dẫn [Windows/Sandbox từng bước](vnpay-sandbox-windows.md), gồm local tunnel và domain triển khai thực.

Cloudflared portable 2026.10.3 lấy từ GitHub release chính thức, SHA-256 đã đối chiếu với metadata asset. File binary và PID/log chỉ ở `.local/` bị Git bỏ qua. Đã chạy tunnel HTTP/2 do kiểm tra mạng cho thấy QUIC bị hạn chế. Relay và tunnel được giữ chạy nền để tiếp tục thử Sandbox; cách dừng và kiểm tra process ở tài liệu Windows.

## Kết quả

- **4 callback relay tests đạt**: query/duplicate giữ nguyên, không chuyển auth headers/cookies, Return không tin query kết quả, chặn API khác, xử lý upstream lỗi.
- **145 backend tests đạt**, gồm thêm HTTPS config và tham số PAY/Return được ký; toàn bộ payment/IPN concurrency/rollback/idempotency tests cũ tiếp tục đạt.
- **23 frontend tests, lint/build và 14 Edge E2E đạt**, gồm Customer/Admin/payment/ticket.
- 6 migration hợp lệ trên PostgreSQL 18.4 qua hai lần khởi động; payment thành công và hai vé tồn tại sau restart.
- Kiểm tra HTTPS công khai thực đạt HTTP 200 `RspCode=97` cho IPN không chữ ký, Return 302 chỉ giữ txnRef, API Admin trả 404 qua relay.
- Kiểm tra script trên `.env` giả lập: URL sai/thiếu Secret không ghi file; DB config được giữ; cập nhật URL và enable flag đúng.

Lượt hồi quy: `.local/p7-20261026-011226-44d3d75b/verification.json`; log `.local/vnpay-https-acceptance.log`. Public URLs/PID và trạng thái hiện tại ở `.local/vnpay-tunnel/`; URL tạm không được dùng làm domain cố định trong source.

## Việc cần merchant hoàn tất

TMN Code/Hash Secret vẫn chưa được cung cấp trong `.env`; VNPAY được giữ tắt. Chưa thực hiện giao dịch qua gateway VNPAY thực và chưa xác nhận portal đã đăng ký IPN. Chủ merchant tự điền khóa cục bộ, chạy script với `-EnablePayment`, đăng ký IPN và restart backend trước khi tạo đơn mới. Kiểm thử mô phỏng và kiểm tra HTTPS không thay thế bước này.

Không commit/push trong lượt cấu hình. Các thay đổi P7 và dữ liệu mẫu có sẵn trong working tree được giữ nguyên.
