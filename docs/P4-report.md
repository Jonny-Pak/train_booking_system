# Báo cáo hoàn thành P4 — Booking và giữ ghế

Ngày kiểm chứng: 25/10/2026, giờ Việt Nam. Nhánh: `feature/booking-seat-hold`, tạo từ `develop` có merge P3 `67dc0ec`.

## Kết quả

P4 đã triển khai và kiểm chứng trên Java 21.0.12, Spring Boot 3.5.16 và PostgreSQL 18.4 thật. Không dùng MySQL, H2, Redis hoặc dịch vụ giữ ghế bên ngoài.

| Kiểm tra | Kết quả |
|---|---|
| Maven `-o -B clean verify` | BUILD SUCCESS |
| BookingIntegrationTest | 31 đạt |
| AuthIntegrationTest | 29 đạt |
| JwtConfigTest | 3 đạt |
| CatalogIntegrationTest | 21 đạt |
| InfrastructureIntegrationTest | 12 đạt |
| GlobalExceptionHandlerTest | 4 đạt |
| Tổng | 100 tests; 0 failures, 0 errors, 0 skipped |
| Tranh ghế còn trống | Hai request đồng thời: một 201, một 409; một booking active |
| Tranh ghế vừa hết hạn | Đơn cũ EXPIRED; chỉ một request giữ lại thành công |
| HTTP thật | JWT, tạo đơn, lịch sử, admin detail, conflict, cancel, đặt lại đạt |
| Scheduler thật | Đơn quá hạn tự chuyển EXPIRED và ghế inactive |
| Restart | Health UP hai lần; JWT, booking PENDING và active seat còn nguyên |
| Flyway | V1/V2/V3 giữ nguyên; V4 áp dụng một lần; validate 7 JPA entities |
| Database người dùng | Không migrate/seed/sửa dữ liệu local; dùng cluster tạm riêng |
| Dọn process | App và PostgreSQL của lượt kiểm chứng đã dừng |

Bằng chứng: `.local/p4-20261025-154515-d7bf20a5/verification.json`, thời điểm `2026-10-25T08:47:29Z`. Logs HTTP/startup ở cùng thư mục; báo cáo chi tiết tests tại `backend/target/surefire-reports/`. Các thư mục này không đưa vào Git.

## Các phần đã tạo và sửa

- Module `booking/`: Booking, BookingSeat, BookingStatus; request/response DTO; BookingRepository, BookingSeatRepository; BookingService; BookingExpiryService; BookingExpiryScheduler; BookingController, AdminBookingController.
- Flyway `V4__booking_history_indexes.sql`: index lịch sử theo user và cho admin; giữ nguyên chín bảng nghiệp vụ và ràng buộc chống giữ trùng V1.
- SecurityConfig: cho CUSTOMER/ADMIN dùng các endpoint booking được khai báo; admin list vẫn ADMIN-only.
- GlobalExceptionHandler: unique constraint `uq_active_trip_seat` trả 409 SEAT_UNAVAILABLE.
- application.yml và .env.example: cấu hình thời hạn/scheduler; profile test tắt scheduler để kiểm soát thời gian.
- BookingIntegrationTest: request/transaction PostgreSQL thật, fixtures riêng và cleanup theo ID; không bọc toàn bộ test trong transaction giả lập.
- InfrastructureIntegrationTest: xác nhận schema V4.
- Test-P4.ps1 và lifecycle Test-P1.ps1: toàn bộ regression tests, HTTP booking, scheduler thật, restart; các biến môi trường được khôi phục sau khi chạy.
- README, architecture, API contract và booking-api: hướng dẫn chạy, cấu hình, endpoint và các quyết định nghiệp vụ.

Code dùng if/else, vòng lặp for, biến có kiểu rõ ràng và các hàm kiểm tra theo từng nhiệm vụ. Không dùng Specification/Criteria, stream hoặc generic workflow cho nghiệp vụ booking. Lambda chỉ cần trong test chạy hai thread đồng thời.

## Quy tắc đã nghiệm thu

1. Lấy owner từ JWT; client không quyết định user, giá, tổng tiền hoặc trạng thái. Field lạ trả 400.
2. Chỉ đặt chuyến SCHEDULED chưa khởi hành, tuyến/phương tiện active.
3. Chọn 1–10 ghế dương, không trùng, đúng phương tiện. Thiếu ghế/chuyến hoặc sai phương tiện không tạo đơn.
4. Giá dùng BigDecimal từ base_price, lưu snapshot unit_price cho từng ghế; tổng đúng và không vượt NUMERIC(12,2).
5. Hold mặc định 600 giây, cấu hình 30–1800; không kéo dài qua departureAt.
6. Khóa Trip trước đọc dữ liệu; cùng khóa với catalog, cancel và expiry. Khóa Booking sau Trip khi đổi trạng thái; partial unique index là bảo vệ cuối.
7. Release hold hết hạn trước giữ lại; status và toàn bộ ghế đổi trong cùng transaction. Nếu đơn mới thất bại, cả cleanup cũng rollback; lần sau có thể cleanup lại.
8. Hai khách tranh cùng ghế (cả ghế trống và ghế vừa hết hạn) không cùng thành công.
9. Cancel PENDING còn hạn thành CANCELLED; PENDING hết hạn thành EXPIRED. Cancel lặp an toàn; PAID trả 409, không giải phóng ghế.
10. Customer chỉ thấy lịch sử của mình; detail/cancel của người khác trả 404; ADMIN được xem/hủy theo cùng quy tắc. Booking response dùng Cache-Control no-store.
11. Scheduler dọn tối đa 100 chuyến mỗi lượt, transaction riêng theo chuyến; có thể chạy lặp/nhiều instance. Sau restart tiếp tục làm việc từ dữ liệu DB.
12. Không tạo payment hoặc ticket, không có API tự chuyển PAID.

## Chạy và kiểm thử

Từ thư mục gốc, PowerShell 7:

```powershell
.\scripts\Test-P4.ps1 -JavaHome 'C:\Program Files\Java\jdk-21.0.12' -Offline
```

Script mặc định dùng PostgreSQL tại `C:\Program Files\PostgreSQL\18\bin`; có thể truyền `-PostgresBin` theo máy. Bỏ `-Offline` nếu cần tải dependency. Cluster ngẫu nhiên chỉ nghe loopback, có mật khẩu ngẫu nhiên, không dùng database cá nhân. Scheduler trong HTTP smoke chạy mỗi giây để kiểm chứng nhanh.

Backend local chạy như P3:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Giữ cấu hình .env cá nhân; backend áp dụng V4 qua Flyway khi khởi động. Request mẫu và danh sách lỗi ở [booking-api.md](booking-api.md).

## Quyết định và giới hạn

- Giới hạn 10 ghế/đơn để input và transaction nhỏ; chưa có phụ phí theo seatClass.
- GET không ghi database. holdExpired cho biết deadline đã qua, kể cả status đang chờ scheduler cập nhật; admin filter dùng status đã lưu. Không dùng GET làm bằng chứng thanh toán hợp lệ.
- Trạng thái và active seats được bảo đảm khi ghi qua service; SQL ghi trực tiếp vẫn cần tuân thủ quy tắc liên bảng.
- Lịch sử giữ snapshot giá; tuyến/lịch/seat code được bảo vệ khỏi chỉnh sửa bởi quy tắc history của P3. Mã/tên phương tiện vẫn có thể đổi như P3.
- Không có retry idempotency key cho POST tạo đơn, gia hạn hold, hoàn tiền hoặc rate limit trong P4.
- Đã kiểm chứng PostgreSQL 18.4 trên Windows; chưa chạy ma trận PostgreSQL 16/17 hoặc Linux.
- Mã P4 được quản lý trên nhánh `feature/booking-seat-hold`, tạo từ `develop`.

## WBS và bước tiếp theo

Hoàn thành phần backend WBS `1.3.3.1` (tạo đơn, giữ ghế có hạn) và `1.3.3.2` (chống đặt trùng, hủy đơn), phục vụ Sprint 3–4 và mốc M04 booking đến PENDING. Kiểm chứng T05 chọn/đặt ghế không trùng và T08 hold hết hạn; chưa đánh dấu hoàn thành E2E thanh toán.

P5 tiếp theo: React Customer dùng API thật, lưới ghế, tạo booking và lịch sử. P6: VNPAY Sandbox, IPN và vé; phải tiếp tục khóa Trip → Booking → Payment, kiểm tra status/deadline sau khi khóa, không phục hồi ghế cho IPN đến sau EXPIRED/CANCELLED.
