# Giao diện đăng nhập và đăng ký theo mẫu

Hai form dùng hộp thoại trắng bo góc, nền trang chủ làm tối, nút chính xanh đậm và liên kết chuyển form màu xanh theo ảnh tham khảo của người dùng.

## File chính

- `frontend/src/pages/AuthPage.jsx`: form, gọi API, đóng hộp thoại và chuyển giữa đăng nhập/đăng ký.
- `frontend/src/auth.css`: bố cục và responsive, tách khỏi CSS nghiệp vụ.
- `frontend/src/App.jsx`: trang chủ làm nền cho hai route `/login`, `/register`.
- `frontend/e2e/customer.spec.js`: kiểm tra giao diện và luồng thực tế.

Đăng nhập tiếp tục dùng email/mật khẩu với API JWT hiện có. Đăng ký gồm họ tên, email, mật khẩu và số điện thoại không bắt buộc. Google OAuth, đăng nhập bằng OTP và reCAPTCHA cần tích hợp backend/cấu hình riêng, chưa nằm trong thay đổi này.

## Tương tác

Native `dialog.showModal()` chặn tương tác với nội dung nền. Ô nhập đầu tiên nhận focus; Tab/Shift+Tab di chuyển trong hộp thoại. Nút X hoặc Escape đóng form và quay lại trang trước theo route state. Khi đang gửi API, nút gửi và nút đóng bị khóa.

Chuyển giữa hai form giữ đường dẫn quay lại và ghế đã chọn. Đăng nhập thành công tiếp tục luồng đặt vé. Đăng ký thành công chuyển sang đăng nhập, điền sẵn email và hiện thông báo.

Trên màn hình nhỏ, hộp thoại co theo chiều rộng và cuộn khi vượt chiều cao màn hình. Có nút hiện/ẩn mật khẩu và thông báo lỗi API bằng tiếng Việt.

## Kiểm chứng

Chạy `scripts/Test-P5.ps1` từ repository root. Browser tests kiểm tra focus, đóng bằng X/Escape, chuyển form, kích thước mobile và luồng đăng ký/đăng nhập/đặt vé với backend/PostgreSQL thật. Ảnh kiểm chứng gồm `login-desktop.png`, `register-desktop.png`, `register-mobile.png` trong `.local/p5-*/screenshots/`.

Lượt nghiệm thu `.local/p5-20261025-185931-bbba12db/verification.json` hoàn tất ngày 25/10/2026 lúc 12:02:48 UTC: lint/build đạt, 17 unit tests frontend, 104 tests backend và 5 browser E2E tests đạt. Đã xem cả ba ảnh giao diện; app và PostgreSQL tạm đã dừng.
