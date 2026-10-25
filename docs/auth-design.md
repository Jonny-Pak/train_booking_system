# Giao diện đăng nhập và đăng ký theo mẫu

Hai form dùng hộp thoại trắng bo góc, nền trang chủ làm tối, nút chính xanh đậm và liên kết chuyển form màu xanh theo ảnh tham khảo của người dùng.

## File chính

- `frontend/src/pages/AuthPage.jsx`: form, gọi API, đóng hộp thoại và chuyển giữa đăng nhập/đăng ký.
- `frontend/src/auth.css`: bố cục và responsive, tách khỏi CSS nghiệp vụ.
- `frontend/src/App.jsx`: trang chủ làm nền cho hai route `/login`, `/register`.
- `frontend/e2e/customer.spec.js`: kiểm tra giao diện và luồng thực tế.

Đăng nhập dùng email/mật khẩu với API JWT. Tài khoản mới cần [xác nhận email](email-verification.md) trước khi đăng nhập; có nút gửi lại email. Google OAuth, đăng nhập bằng OTP và reCAPTCHA chưa tích hợp.

Form đăng ký được cập nhật theo thứ tự: họ tên, số điện thoại, mật khẩu, xác nhận mật khẩu, email và ô đồng ý chính sách/điều khoản sử dụng. Số điện thoại bắt buộc trên form; API vẫn giữ contract phone tùy chọn. Email được giữ để gửi link xác nhận. Mật khẩu nhập lại phải khớp; nút tạo tài khoản chỉ bật khi tích đồng ý. Hai trường xác nhận mật khẩu/đồng ý chỉ kiểm tra ở giao diện, không gửi vào DTO đăng ký hoặc lưu lịch sử chấp thuận chính sách.

Lượt kiểm chứng form mới: `.local/p5-20261025-194856-a96e9674/verification.json`, ngày 25/10/2026. Lint/build đạt; 112 tests backend, 17 tests frontend và 7 browser tests đạt. Đã xem ảnh đăng ký desktop/mobile; có kiểm thử riêng thứ tự trường, hiện/ẩn xác nhận mật khẩu, chặn mật khẩu không khớp và bắt buộc tích đồng ý.

## Tương tác

Native `dialog.showModal()` chặn tương tác với nội dung nền. Ô nhập đầu tiên nhận focus; Tab/Shift+Tab di chuyển trong hộp thoại. Nút X hoặc Escape đóng form và quay lại trang trước theo route state. Khi đang gửi API, nút gửi và nút đóng bị khóa.

Chuyển giữa hai form giữ đường dẫn quay lại và ghế đã chọn. Đăng nhập thành công tiếp tục luồng đặt vé. Đăng ký thành công chuyển sang đăng nhập, điền sẵn email và hiện thông báo.

Trên màn hình nhỏ, hộp thoại co theo chiều rộng và cuộn khi vượt chiều cao màn hình. Có nút hiện/ẩn mật khẩu và thông báo lỗi API bằng tiếng Việt.

## Kiểm chứng

Chạy `scripts/Test-P5.ps1` từ repository root. Browser tests kiểm tra focus, đóng bằng X/Escape, chuyển form, kích thước mobile và luồng đăng ký/đăng nhập/đặt vé với backend/PostgreSQL thật. Ảnh kiểm chứng gồm `login-desktop.png`, `register-desktop.png`, `register-mobile.png` trong `.local/p5-*/screenshots/`.

Lượt nghiệm thu `.local/p5-20261025-185931-bbba12db/verification.json` hoàn tất ngày 25/10/2026 lúc 12:02:48 UTC: lint/build đạt, 17 unit tests frontend, 104 tests backend và 5 browser E2E tests đạt. Đã xem cả ba ảnh giao diện; app và PostgreSQL tạm đã dừng.
