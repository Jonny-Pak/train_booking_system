# Booking và giữ ghế — P4

Prefix: `/api/v1`. Tất cả API booking yêu cầu `Authorization: Bearer <JWT>`.
Response thành công có `Cache-Control: no-store`.

| Method | Path | Quyền | Kết quả |
|---|---|---|---|
| POST | `/bookings` | CUSTOMER/ADMIN | 201 tạo PENDING, giữ toàn bộ ghế |
| GET | `/bookings/my?page=0&size=20` | CUSTOMER/ADMIN | 200 chỉ lịch sử của tài khoản hiện tại |
| GET | `/bookings/{id}` | Owner/ADMIN | 200 chi tiết, ghế và snapshot giá |
| POST | `/bookings/{id}/cancel` | Owner/ADMIN | 200 CANCELLED hoặc trạng thái terminal hiện tại |
| GET | `/admin/bookings?status=PENDING&page=0&size=20` | ADMIN | 200 tất cả đơn, filter status tùy chọn |

Customer truy cập ID của người khác nhận 404. Không có API sửa giá, đổi ghế, xóa lịch sử, tự đánh dấu PAID hoặc gia hạn hold.

## Tạo đơn

```http
POST /api/v1/bookings
Authorization: Bearer <JWT>
Content-Type: application/json

{"tripId":12,"seatIds":[41,42]}
```

```json
{
  "id":55,
  "bookingCode":"c5965cd8ffea4129861086e6f14e5318",
  "userId":7,
  "tripId":12,
  "vehicleId":3,
  "status":"PENDING",
  "totalAmount":200000.50,
  "holdExpiresAt":"2030-01-01T00:10:00Z",
  "createdAt":"2030-01-01T00:00:00Z",
  "holdExpired":false,
  "seats":[
    {"id":81,"seatId":41,"seatCode":"A1","seatClass":"STANDARD","unitPrice":100000.25,"active":true},
    {"id":82,"seatId":42,"seatCode":"A2","seatClass":"STANDARD","unitPrice":100000.25,"active":true}
  ]
}
```

- `tripId` dương; `seatIds` có 1–10 ID dương, không null hoặc trùng, thuộc phương tiện của chuyến.
- Chuyến SCHEDULED, chưa khởi hành; tuyến/phương tiện active.
- User lấy từ JWT. Giá lấy từ `trips.base_price`, lưu snapshot `booking_seats.unit_price`; tổng tiền bằng tổng snapshot. Không nhận `userId`, `totalAmount`, `unitPrice`, `status` từ client; field lạ trả 400.
- Tổng tiền phải nằm trong NUMERIC(12,2). P4 dùng cùng base price cho các hạng ghế, chưa có bảng phụ phí.
- Mã đơn là UUID bỏ dấu gạch, dài 32 ký tự, có unique constraint.
- Hold hết hạn tại `min(now + BOOKING_HOLD_SECONDS, departureAt)`.
- Nếu một ghế không còn trống, cả đơn rollback, trả 409 `SEAT_UNAVAILABLE`.
- Gửi lại POST tạo đơn không phải retry idempotent: ghế đang giữ sẽ trả 409. Cancel là idempotent.

## Hủy và hết hạn

| Trạng thái hiện tại | Thao tác | Kết quả |
|---|---|---|
| PENDING còn hạn | cancel | CANCELLED, mọi ghế inactive |
| PENDING hết hạn | cancel / scheduler / tạo đơn mới cùng chuyến | EXPIRED, mọi ghế inactive |
| CANCELLED | cancel lặp | 200 CANCELLED |
| EXPIRED | cancel lặp | 200 EXPIRED |
| PAID | cancel | 409 BOOKING_ALREADY_PAID, ghế vẫn active |
| PAID | expiry | giữ nguyên |

Expiry dùng điều kiện `holdExpiresAt <= now`, bao gồm đúng thời điểm hết hạn. Hủy/hết hạn cập nhật status và tất cả ghế trong cùng transaction. Booking PAID và phát hành vé thuộc P6; P4 không cung cấp endpoint chuyển PAID.

GET chỉ đọc dữ liệu: trong khoảng chờ scheduler, status lưu có thể vẫn PENDING và active vẫn true dù đã qua hạn. `holdExpired=true` cho biết hold không còn hợp lệ. Không dùng riêng status hoặc active để quyết định được thanh toán. Public seat API coi hold hết hạn là AVAILABLE; POST tạo đơn giải phóng hold hết hạn trong transaction trước khi giữ lại. Admin filter dùng status đã lưu trong database.

## Cấu hình

| Biến môi trường | Mặc định | Ý nghĩa |
|---|---|---|
| BOOKING_HOLD_SECONDS | 600 | 30–1800 giây; sai khoảng khiến app không khởi động |
| BOOKING_EXPIRY_ENABLED | true | Bật scheduler; false chỉ dùng release khi tạo/hủy đơn |
| BOOKING_EXPIRY_POLL_MILLISECONDS | 30000 | Khoảng nghỉ giữa hai lượt, phải là số dương |

Scheduler xử lý tối đa 100 chuyến mỗi lượt, mỗi chuyến một transaction; lỗi một chuyến sẽ được thử lại ở lượt sau. Nhiều app instance cùng chạy vẫn được bảo vệ bởi khóa Trip và kiểm tra lại trạng thái sau khi khóa. Không cần Redis.

## Phân trang và lỗi

History sắp xếp `createdAt DESC, id DESC`; page từ 0, size 1–100.
Response: `content`, `page`, `size`, `totalElements`, `totalPages`.

| HTTP | Code thường gặp |
|---|---|
| 400 | VALIDATION_ERROR, BAD_REQUEST, DUPLICATE_SEAT, SEAT_WRONG_VEHICLE, BOOKING_TOTAL_TOO_LARGE, INVALID_PAGE, INVALID_ID |
| 401 | UNAUTHORIZED |
| 403 | FORBIDDEN |
| 404 | NOT_FOUND (chuyến/ghế/đơn không tồn tại hoặc không có quyền sở hữu) |
| 409 | SEAT_UNAVAILABLE, TRIP_NOT_BOOKABLE, CATALOG_INACTIVE, BOOKING_ALREADY_PAID |

Không trả SQL, stacktrace, password hoặc token trong response booking.

## Thử với backend local

Đăng nhập lấy token theo contract auth, thay ID chuyến và ghế bằng dữ liệu thật:

```powershell
$headers = @{Authorization = 'Bearer ' + $login.accessToken}
$body = @{tripId=12;seatIds=@(41,42)} | ConvertTo-Json
$booking = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/bookings' -Headers $headers -ContentType 'application/json' -Body $body
Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/bookings/my' -Headers $headers
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/bookings/$($booking.id)/cancel" -Headers $headers
```
