# PostgreSQL schema reference

`01_schema.sql` reproduces the nine-table SQL embedded in the supplied specification. The executable source is `backend/src/main/resources/db/migration/V1__initial_schema.sql`.

Create the database outside Flyway, then let the application apply migrations. Do not run this reference SQL first and do not edit both copies independently. Keep applied migrations immutable; add schema changes after P3 as V4 and later migrations. No seed data or application users are inserted in P1.

`02_demo_seed.sql` is an explicit opt-in P3 sample, never a startup migration. Run only against a dedicated demo database after Flyway migrations. Vehicles use `DEMO-P3-*` codes and `[DEMO P3]` names. No accounts or passwords are inserted. Initial trips depart seven days after the first execution; reruns reuse the existing demo trips without changing their dates. Demo vehicle booking history makes the seed abort instead of modifying its seat layout. Seed does not update/delete existing data; do not use reserved demo codes for real vehicles.

The P3 verification script applies the seed twice on its isolated database and checks that public search still returns exactly one matching bus trip.

## Dữ liệu mẫu mở rộng

### Lịch cố định 27/10/2026–10/11/2026

Chạy `04_sample_trips_20261027_20261110.sql` sau Flyway V10, bằng Query Tool của pgAdmin trên database dự án hoặc `psql -v ON_ERROR_STOP=1 -f database/04_sample_trips_20261027_20261110.sql` với cấu hình kết nối PostgreSQL của bạn.

Script bổ sung **150 chuyến** trong **15 ngày** (bao gồm hai đầu): 75 chuyến xe giường nằm và 75 chuyến tàu. Mỗi ngày có lịch mới:

| Loại | Giờ khởi hành (Việt Nam) | Tuyến tương ứng |
| --- | --- | --- |
| Xe khách | 06:00 · 08:30 · 11:00 · 15:30 · 20:00 | Hà Nội–Hải Phòng · Hà Nội–Ninh Bình · Hà Nội–Sa Pa · TP Hồ Chí Minh–Vũng Tàu · TP Hồ Chí Minh–Đà Lạt |
| Tàu hỏa | 07:00 · 09:30 · 13:00 · 17:30 · 21:30 | Hà Nội–Hải Phòng · Hà Nội–Ninh Bình · Hà Nội–Vinh · TP Hồ Chí Minh–Phan Thiết · TP Hồ Chí Minh–Nha Trang |

Dùng 5 xe 34 giường (hai tầng ×17) và 5 tàu 11 toa ×434 chỗ, mã riêng `SAMPLE-2026-*`. Giá/lịch chỉ là mẫu trình diễn. Các chuyến có thể đến vào ngày hôm sau; khoảng ngày được tính theo **ngày khởi hành** ở `Asia/Ho_Chi_Minh`.

Giữ toàn bộ dữ liệu cũ nên tổng số chuyến mỗi ngày có thể lớn hơn 10. Script chạy trong một transaction, dùng cùng advisory lock với Admin catalog, kiểm tra trùng lịch và chạy lại không tạo bản sao. Nếu phương tiện/chuyến mẫu đã bị thay đổi hoặc hủy, script dừng để kiểm tra thay vì ghi đè. Không tự đổi ngày của chuyến cũ hoặc sửa đơn đã đặt. SQL dữ liệu mẫu này không phải migration và không tự chạy khi khởi động ứng dụng.

Đã nạp vào database local ngày 26/10/2026 và xác minh qua API public đủ 15 ngày: **27/10–02/11 có 13 xe +7 tàu/ngày**, **03/11–10/11 có 5 xe +5 tàu/ngày**. Tổng trong khoảng là **220 chuyến**, bao gồm 70 chuyến cũ. Bản sao lưu trước khi nạp nằm trong `.local/calendar-seed-20261026-161810/before-seed.dump`; so sánh toàn bộ bản ghi cũ của 10 bảng không có thay đổi. Kiểm thử PostgreSQL tạm đã xác minh lịch cố định, số giờ khác nhau, không trùng phương tiện và chạy SQL hai lần không nhân đôi dữ liệu.

`03_sample_trips.sql` chạy thủ công sau Flyway V9, chỉ thêm dữ liệu, không sửa/xóa bản ghi hiện có. Script dùng mã `SAMPLE-*`, tên phương tiện `[MẪU]`, sáu tuyến (bốn xe giường nằm 34 chỗ, hai tàu 11 toa/434 chỗ), tổng 1004 chỗ và 70 chuyến trong bảy ngày từ ngày mai theo giờ Việt Nam. Xe chạy 08:00/16:00, tàu 09:00. Chạy lại cùng ngày không tạo trùng chuyến; chạy ngày khác bổ sung các ngày còn thiếu. Nếu mã mẫu đã dùng cho phương tiện khác/ngừng hoạt động hoặc lịch bị chồng, toàn bộ transaction rollback. Phương tiện mẫu tồn tại từ trước không bị đổi ghế tự động; nâng cấp bằng API quản trị theo [seat-layouts.md](../docs/seat-layouts.md). Dữ liệu chỉ để trình diễn, không đại diện lịch/giá/ghế của nhà cung cấp thực.

Đã nạp theo yêu cầu ngày 26/10/2026 vào database dự án: ngày khởi hành 27/10–02/11/2026. Kiểm tra qua API public đạt 56 chuyến BUS, 14 chuyến TRAIN và 128 ghế AVAILABLE. Các tuyến được tái sử dụng khi đã tồn tại và đang hoạt động.
