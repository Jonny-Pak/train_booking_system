# P7 — Quản trị và thống kê

## Truy cập

Đăng nhập bằng tài khoản ADMIN rồi mở `/admin` hoặc chọn **Quản trị** trên thanh điều hướng. CUSTOMER không thấy liên kết và không được mở trang quản trị. Backend kiểm tra quyền độc lập cho mọi API `/api/v1/admin/**`; ẩn nút trên frontend không thay thế phân quyền.

Không tạo tài khoản Admin hoặc mật khẩu mặc định. Đăng ký tài khoản và xác nhận email như bình thường, sau đó người quản trị database cấp quyền cho đúng email bằng SQL có điều kiện:

```sql
BEGIN;
UPDATE users SET role = 'ADMIN'
WHERE email = 'admin@example.com' AND email_verified = TRUE;
-- Kiểm tra đúng một tài khoản trước khi COMMIT.
SELECT id, email, role FROM users WHERE email = 'admin@example.com';
COMMIT;
```

Thay email mẫu bằng email thực. P7 không tự cấp quyền cho người dùng hiện có và không cung cấp API tự nâng quyền. Tải lại trang sau khi cấp quyền; backend luôn đọc role hiện tại trong database.

## Màn hình

| URL | Chức năng |
|---|---|
| `/admin` | Tổng quan, lọc khoảng ngày, doanh thu, số đơn, số vé, đối soát |
| `/admin/routes` | Tạo/sửa/xóa tuyến, bật/tắt hoạt động |
| `/admin/vehicles` | Tạo/sửa/xóa phương tiện, mở quản lý ghế |
| `/admin/vehicles/:vehicleId/seats` | Tạo/sửa/xóa ghế theo phương tiện |
| `/admin/trips` | Tạo/sửa/xóa chuyến; chọn tuyến/phương tiện bằng tên |
| `/admin/bookings` | Phân trang, lọc trạng thái, mở chi tiết |
| `/admin/bookings/:id` | Tuyến/chuyến/ghế, lịch sử thanh toán, xem vé, xác nhận hủy đơn còn giữ chỗ |
| `/admin/reconciliation` | Tra cứu giao dịch cần kiểm tra với merchant, mở đơn liên quan |

Mọi thao tác xóa/hủy có bước xác nhận. API giữ nguyên các quy tắc lịch sử, khóa chống đặt trùng và giới hạn sửa/xóa P3–P6. Không cho đánh dấu thanh toán hoặc tự xử lý hoàn tiền trên UI. Đối soát chỉ đọc; xử lý tài chính thủ công cần quy trình riêng sau khi kiểm tra merchant.

Catalog và danh sách quản trị phân trang 10 dòng. Selector tuyến/phương tiện tải lần lượt mọi trang 100 dòng để không bỏ sót bản ghi. Cách này phù hợp MVP; dữ liệu lớn cần tìm kiếm selector phía server. Thời gian nhập chuyến luôn được hiểu là giờ Việt Nam và đổi sang ISO UTC trước khi gửi.

## API thống kê mới

`GET /api/v1/admin/statistics?from=2026-10-01&to=2026-10-25`

Chỉ ADMIN, trả `Cache-Control: no-store`. Không cần migration mới; tiếp tục dùng PostgreSQL và chín bảng hiện có.

`from`, `to` là ngày ISO. Khi thiếu, from mặc định đầu tháng hiện tại và to mặc định hôm nay tại `Asia/Ho_Chi_Minh`. Ngày cuối được tính trọn ngày. Khoảng hợp lệ tối đa 366 ngày, từ 2000-01-01 đến 2100-12-31; sai trả 400 `INVALID_DATE_RANGE`; ngày sai định dạng trả 400.

| Field | Định nghĩa |
|---|---|
| `from`, `to` | Khoảng thực tế sau áp dụng mặc định |
| `totalBookings` | Số đơn được tạo trong kỳ |
| `paidBookings` | Trong các đơn tạo trong kỳ, hiện có status PAID |
| `pendingBookings` | Trong các đơn tạo trong kỳ, PENDING và hold còn hạn |
| `expiredBookings` | Trong các đơn tạo trong kỳ, EXPIRED hoặc PENDING đã quá hạn |
| `cancelledBookings` | Trong các đơn tạo trong kỳ, CANCELLED |
| `revenue` | Tổng tiền payment SUCCESS, booking PAID, không cần đối soát; tính theo `paid_at` |
| `issuedTickets` | Số vé được phát hành trong kỳ, tính theo `issued_at` |
| `reconciliationPayments` | Số payment có cờ đối soát, tính theo ngày tạo payment |
| `reconciliationAmount` | Tổng amount các payment có cờ đối soát trong kỳ; không khẳng định tiền thực thu |

Doanh thu và số đơn PAID có thể khác kỳ vì đơn được tạo và thanh toán ở hai ngày khác nhau. Khoản thành công đến muộn của đơn hết hạn không vào doanh thu, không phát hành vé mới; vẫn hiện trong đối soát. Không cộng tổng amount của đơn chưa trả tiền. Dùng BigDecimal và SQL tham số; transaction read-only REPEATABLE_READ bảo đảm các chỉ số cùng một snapshot. Đây là thống kê vận hành tại thời điểm truy vấn, chưa có sổ kế toán hoặc hoàn tiền tự động.

## Chạy và kiểm thử

Backend và frontend chạy theo README hiện tại. Khi thêm endpoint mới cần khởi động lại backend. Frontend: `cd frontend` rồi `npm.cmd run dev`.

Nghiệm thu độc lập bằng PowerShell 7:

```powershell
.\scripts\Test-P7.ps1 -JavaHome 'C:\Program Files\Java\jdk-21.0.12' -Offline
```

Script chạy lint/unit/build frontend, `clean verify` backend với PostgreSQL thực; thử auth/email/catalog/booking/expiry; chạy Edge E2E Customer, Admin, IPN mô phỏng có chữ ký và vé; khởi động lại ứng dụng để kiểm tra migration và dữ liệu tồn tại. Cluster/database/SMTP/merchant test dùng riêng, không sửa dữ liệu hiện có và không gửi email người thật. Kết quả ở `.local/p7-*/verification.json`, ảnh ở `screenshots/admin-desktop.png` và `admin-mobile.png`. Các máy chủ tạm được dừng sau khi xong.

Nghiệm thu VNPAY Sandbox thực vẫn cần cấu hình merchant, secret và IPN HTTPS công khai; test mô phỏng không thay thế giao dịch qua gateway. Xem [payment-api.md](payment-api.md).
