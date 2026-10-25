# Contract API Auth và Health

API đặt vé, giữ ghế, lịch sử và hủy đơn xem tại [booking-api.md](booking-api.md).

## Endpoint hiện tại

| Method | Path | Quyền | Kết quả |
|---|---|---|---|
| GET | `/actuator/health` | Public | 200 `{"status":"UP"}` khi hạ tầng hoạt động |
| POST | `/api/v1/auth/register` | Public | 201 hồ sơ CUSTOMER chưa xác nhận; gửi email |
| POST | `/api/v1/auth/verify-email` | Public | 204 xác nhận token chưa hết hạn, chỉ dùng một lần |
| POST | `/api/v1/auth/resend-verification` | Public | 204; gửi lại cho email chờ xác nhận ngoài cooldown |
| POST | `/api/v1/auth/login` | Public | 200 JWT và hồ sơ |
| GET | `/api/v1/auth/me` | CUSTOMER/ADMIN | 200 hồ sơ theo JWT subject |

Prefix `/api/v1/admin/**` yêu cầu ADMIN. CRUD Catalog/Trip và các GET trip public của P3 được mô tả tại [catalog-api.md](catalog-api.md); booking theo [booking-api.md](booking-api.md). Endpoint chưa khai báo vẫn bị chặn. Health không lộ components/details. Authentication stateless, không có tài khoản mặc định hoặc cookie session.

P5 hỗ trợ CORS cho origin frontend cụ thể trong APP_CORS_ALLOWED_ORIGINS, mặc định http://localhost:5173 và http://127.0.0.1:5173. Preflight không cần JWT nhưng request nghiệp vụ vẫn cần Bearer như contract. Không hỗ trợ cookie credentials hoặc wildcard origin.

## Register

```json
{
  "fullName": "Nguyen Van A",
  "email": "customer@example.com",
  "password": "Example-password-21!",
  "phone": "0900000000"
}
```

`fullName` bắt buộc, tối đa 120 ký tự. Email bắt buộc, đúng định dạng, tối đa 190 ký tự, strip/lowercase trước khi validate/lưu. `phone` tùy chọn, tối đa 20 ký tự. Password tối thiểu 8 code points và tối đa 72 byte UTF-8, không trim. Không nhận field `role`, `userId`, `passwordHash` hoặc field lạ; trả 400. Email trùng sau chuẩn hóa trả 409 `EMAIL_ALREADY_EXISTS`.

Response 201:

```json
{
  "id": 1,
  "fullName": "Nguyen Van A",
  "email": "customer@example.com",
  "phone": "0900000000",
  "role": "CUSTOMER",
  "createdAt": "2026-10-25T00:00:00Z",
  "emailVerified": false
}
```

## Login và me

Login nhận `email`, `password`. Response:

```json
{
  "accessToken": "<signed-jwt>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "expiresAt": "2026-10-25T00:15:00Z",
  "user": {
    "id": 1,
    "fullName": "Nguyen Van A",
    "email": "customer@example.com",
    "phone": "0900000000",
    "role": "CUSTOMER",
    "createdAt": "2026-10-25T00:00:00Z",
    "emailVerified": true
  }
}
```

Các thời điểm/ID trên chỉ là ví dụ. Sai mật khẩu và email không tồn tại cùng trả 401 `INVALID_CREDENTIALS`, cùng message. Đúng mật khẩu nhưng email chưa xác nhận trả 403 `EMAIL_NOT_VERIFIED`. Dữ liệu đầu vào sai định dạng trả 400. Luồng xác nhận, resend, lỗi SMTP và cấu hình tại [email-verification.md](email-verification.md).

Gửi `Authorization: Bearer <accessToken>` cho `/me`. Response giống hồ sơ register; không nhận userId từ query/body. Thiếu, sai, hết hạn token hoặc tài khoản đã xóa trả 401 `UNAUTHORIZED`. Token chỉ có hiệu lực với đúng thuật toán, chữ ký, issuer, audience, subject và thời gian hợp lệ. Quyền được đọc từ database, không tin role do client gửi.

CUSTOMER gọi prefix admin trả 403 `FORBIDDEN`. Auth responses thành công có `Cache-Control: no-store`. JWT không nhận qua cookie/query. Chưa có refresh token/logout thu hồi; hết hạn thì đăng nhập lại. Không public token trong URL/log.

## Ví dụ PowerShell 7

Chạy backend theo README. Ví dụ dùng dữ liệu minh họa, không chứa tài khoản mặc định:

```powershell
$base = 'http://localhost:8080/api/v1'
$credential = Read-Host 'Password' -MaskInput
$registration = @{fullName='Demo Customer'; email='customer@example.com'; password=$credential} | ConvertTo-Json
$user = Invoke-RestMethod -Method Post -Uri "$base/auth/register" -ContentType 'application/json' -Body $registration
# Mở link trong email, nhấn Xác nhận email trên frontend trước bước đăng nhập.
$body = @{email='customer@example.com'; password=$credential} | ConvertTo-Json
$session = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body $body
$me = Invoke-RestMethod -Uri "$base/auth/me" -Headers @{Authorization="Bearer $($session.accessToken)"}
$me # chỉ hiển thị profile; không in token
```

## Lỗi JSON

```json
{
  "timestamp": "2026-10-25T00:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "fieldErrors": {"email": "must be a well-formed email address"}
}
```

Lỗi không có field validation trả `fieldErrors: {}`. Không trả stacktrace, SQL, hash hoặc nguyên nhân kiểm tra chữ ký. Các lỗi HTTP/JSON sai giữ cấu trúc này. Trường dữ liệu lạ trả 400 `BAD_REQUEST`.
