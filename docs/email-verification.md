# Xác nhận email khi đăng ký

## Luồng sử dụng

1. Người dùng đăng ký CUSTOMER. Backend tạo tài khoản chưa xác nhận và gửi email qua SMTP.
2. Frontend hướng dẫn mở hộp thư, gồm cả thư rác. Đăng nhập đúng mật khẩu nhưng chưa xác nhận trả 403 `EMAIL_NOT_VERIFIED`, không phát JWT.
3. Email chứa link `${APP_FRONTEND_URL}/verify-email#token=...`. Người dùng mở link, nhấn **Xác nhận email**, sau đó đăng nhập.
4. Link mặc định hết hạn sau 30 phút, chỉ dùng một lần. Gửi lại link mới làm link cũ mất hiệu lực. Mỗi tài khoản được gửi lại cách nhau ít nhất 60 giây.

Nếu xác nhận trên cùng tab đã đăng ký, đường dẫn quay lại và ghế đang chọn được giữ trong sessionStorage; tab/trình duyệt khác quay về trang chủ sau đăng nhập. Token không được lưu vào localStorage/sessionStorage.

## API

| Method | Endpoint | Request | Kết quả |
|---|---|---|---|
| POST | `/api/v1/auth/register` | DTO đăng ký hiện có | 201 hồ sơ với `emailVerified: false`; email đã được SMTP chấp nhận |
| POST | `/api/v1/auth/verify-email` | `{"token":"<64 ký tự hex>"}` | 204 khi xác nhận thành công |
| POST | `/api/v1/auth/resend-verification` | `{"email":"customer@example.com"}` | 204 cho email không tồn tại/đã xác nhận/đang cooldown hoặc gửi lại thành công |
| POST | `/api/v1/auth/login` | email và mật khẩu | Chỉ cấp JWT khi đã xác nhận |

Token sai, hết hạn, đã dùng hoặc bị thay thế trả 400 `INVALID_VERIFICATION_TOKEN`. Payload sai định dạng trả 400 validation. Lỗi SMTP trả 503 `EMAIL_SEND_FAILED` với nội dung chung. Không trả link/token/hash trong API. Các response auth thành công dùng `Cache-Control: no-store`.

Backend không xác nhận bằng GET: thao tác xác nhận là POST sau khi người dùng nhấn nút, tránh phần mềm quét link email tự kích hoạt tài khoản. Fragment không được gửi trong HTTP request; trang xóa fragment khỏi URL ngay sau khi đọc. Reload trang yêu cầu mở lại link trong email.

## Database và transaction

Flyway V5 thêm bốn cột vào `users`, không thay đổi các migration cũ hoặc thêm bảng: `email_verified`, `verification_token_hash`, `verification_expires_at`, `verification_sent_at`.

Tài khoản tồn tại trước V5 được đánh dấu đã xác nhận để giữ quyền truy cập. Tài khoản mới mặc định chưa xác nhận. Đây là chính sách tương thích dữ liệu cũ; không chứng minh tài khoản cũ đã thực sự nhận email.

Token ngẫu nhiên 256 bit từ SecureRandom; database chỉ lưu SHA-256. Khóa ghi PostgreSQL trên user bảo vệ xác nhận/gửi lại đồng thời. Xác nhận xóa hash và hạn dùng; JWT converter cũng kiểm tra `email_verified` từ database để chặn token của tài khoản chưa xác nhận.

Gửi SMTP đồng bộ trong transaction để giữ cách triển khai đơn giản. Lỗi gửi mail rollback đăng ký; lỗi gửi lại giữ link cũ còn hiệu lực. Timeout SMTP giới hạn 5 giây cho connect/read/write. SMTP và PostgreSQL không có transaction phân tán: nếu SMTP chấp nhận rồi DB commit thất bại, email có thể chứa link không dùng được. Người dùng đăng ký/gửi lại sau đó; chưa triển khai outbox/queue. SMTP chấp nhận không đảm bảo email đã vào inbox.

## Cấu hình chạy thật

Thêm vào `.env` gốc (file bị Git bỏ qua), thay các giá trị theo nhà cung cấp SMTP:

```dotenv
APP_FRONTEND_URL=http://localhost:5173
EMAIL_VERIFICATION_TTL_SECONDS=1800
MAIL_HOST=<smtp-host>
MAIL_PORT=587
MAIL_FROM=<sender-email>
MAIL_USERNAME=<smtp-username>
MAIL_PASSWORD=<smtp-password-or-app-password>
MAIL_SMTP_AUTH=true
MAIL_STARTTLS=true
```

Ví dụ trên dùng SMTP STARTTLS; chọn cổng theo tài liệu nhà cung cấp. `APP_FRONTEND_URL` phải là địa chỉ frontend người nhận truy cập được, không có query/fragment; khi triển khai dùng HTTPS và SPA fallback cho `/verify-email`. Thời hạn token cho phép 60–86400 giây. Không dùng thông tin SMTP trong biến `VITE_*`.

Khởi động lại backend sau khi cập nhật `.env` bằng lệnh hiện có trong README; Flyway tự chạy V5. Frontend chạy `npm.cmd run dev` trong `frontend/`. Mặc định SMTP là localhost:1025; khi không có SMTP đang chạy, đăng ký trả 503 và không tạo tài khoản. Không có chế độ tự bỏ qua xác nhận hoặc in link vào console.

## Gmail SMTP

Với Gmail, dùng `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`, `MAIL_SMTP_AUTH=true`, `MAIL_STARTTLS=true`. Đặt `MAIL_USERNAME` và `MAIL_FROM` cùng địa chỉ Gmail gửi thư, `MAIL_PASSWORD` là mật khẩu ứng dụng. Xem [cấu hình SMTP chính thức](https://support.google.com/mail/answer/7104828?hl=en).

Bật xác minh hai bước cho tài khoản Google rồi tạo mật khẩu ứng dụng theo [hướng dẫn Google](https://support.google.com/accounts/answer/185833?hl=en). Điền mật khẩu ứng dụng vào `.env` ở máy, không gửi qua chat hoặc commit. Nếu tài khoản không có mục mật khẩu ứng dụng, kiểm tra các điều kiện/hạn chế trong hướng dẫn Google. Khởi động lại backend sau khi điền cấu hình.

Nếu frontend trả 502/504 với body rỗng, kiểm tra backend ở cổng 8080 và `API_PROXY_TARGET`. Đây là lỗi kết nối proxy, khác lỗi SMTP 503 `EMAIL_SEND_FAILED`. Khi SMTP chưa sẵn sàng, health có thể DOWN dù Tomcat và các API khác vẫn đang chạy.

## Kiểm thử

```powershell
.\scripts\Test-P5.ps1 -JavaHome 'C:\Program Files\Java\jdk-21.0.12' -Offline
```

Script tạo PostgreSQL riêng và SMTP inbox cục bộ từ `scripts/test-mailbox.mjs`, cả hai chỉ phục vụ dữ liệu kiểm thử. Inbox HTTP không được đưa vào application hoặc mở trên mạng ngoài. Backend tests dùng mock JavaMailSender để kiểm tra lỗi SMTP; browser tests đọc email thật do JavaMailSender gửi tới SMTP cục bộ, mở link và xác nhận qua API thật. Không gửi email tới địa chỉ người dùng trong quá trình kiểm thử.

Phủ đăng ký chưa xác nhận, đăng nhập bị chặn, sai mật khẩu, xác nhận thành công, token sai/hết hạn/dùng lại, resend/cooldown/thay token, lỗi SMTP rollback, xác nhận đồng thời và JWT bypass. Bài migration kiểm tra tài khoản cũ giữ quyền và tài khoản mới chưa xác nhận.

Nghiệm thu ngày 25/10/2026: `.local/p5-20261025-191656-d709fb3c/verification.json`, 112 tests backend, 17 tests frontend và 6 tests trình duyệt đạt. Backend khởi động hai lần, giữ nguyên 5 migration; app/PostgreSQL/SMTP tạm đã dừng. Sau chỉnh khoảng cách form, lint/build và kiểm tra hình ảnh desktop/mobile được chạy lại; ảnh cuối là `verify-email-final-desktop.png`, `verify-email-final-mobile.png` trong thư mục screenshots cùng lượt kiểm chứng. Chưa kiểm chứng gửi tới hộp thư thật vì chưa cấu hình thông tin SMTP của nhà cung cấp.

Chưa có giới hạn theo IP/toàn hệ thống; cooldown áp dụng theo tài khoản. Chưa có quên mật khẩu, Google OAuth, OTP hoặc reCAPTCHA.

Tham khảo cấu hình JavaMailSender và timeout theo [Spring Boot Email](https://docs.spring.io/spring-boot/reference/io/email.html).
