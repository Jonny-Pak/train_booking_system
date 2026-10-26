# PostgreSQL schema reference

`01_schema.sql` reproduces the nine-table SQL embedded in the supplied specification. The executable source is `backend/src/main/resources/db/migration/V1__initial_schema.sql`.

Create the database outside Flyway, then let the application apply migrations. Do not run this reference SQL first and do not edit both copies independently. Keep applied migrations immutable; add schema changes after P3 as V4 and later migrations. No seed data or application users are inserted in P1.

`02_demo_seed.sql` is an explicit opt-in P3 sample, never a startup migration. Run only against a dedicated demo database after Flyway migrations. Vehicles use `DEMO-P3-*` codes and `[DEMO P3]` names. No accounts or passwords are inserted. Initial trips depart seven days after the first execution; reruns reuse the existing demo trips without changing their dates. Demo vehicle booking history makes the seed abort instead of modifying its seat layout. Seed does not update/delete existing data; do not use reserved demo codes for real vehicles.

The P3 verification script applies the seed twice on its isolated database and checks that public search still returns exactly one matching bus trip.

## Dữ liệu mẫu mở rộng

`03_sample_trips.sql` chạy thủ công sau Flyway V9, chỉ thêm dữ liệu, không sửa/xóa bản ghi hiện có. Script dùng mã `SAMPLE-*`, tên phương tiện `[MẪU]`, sáu tuyến (bốn xe giường nằm 34 chỗ, hai tàu 11 toa/434 chỗ), tổng 1004 chỗ và 70 chuyến trong bảy ngày từ ngày mai theo giờ Việt Nam. Xe chạy 08:00/16:00, tàu 09:00. Chạy lại cùng ngày không tạo trùng chuyến; chạy ngày khác bổ sung các ngày còn thiếu. Nếu mã mẫu đã dùng cho phương tiện khác/ngừng hoạt động hoặc lịch bị chồng, toàn bộ transaction rollback. Phương tiện mẫu tồn tại từ trước không bị đổi ghế tự động; nâng cấp bằng API quản trị theo [seat-layouts.md](../docs/seat-layouts.md). Dữ liệu chỉ để trình diễn, không đại diện lịch/giá/ghế của nhà cung cấp thực.

Đã nạp theo yêu cầu ngày 26/10/2026 vào database dự án: ngày khởi hành 27/10–02/11/2026. Kiểm tra qua API public đạt 56 chuyến BUS, 14 chuyến TRAIN và 128 ghế AVAILABLE. Các tuyến được tái sử dụng khi đã tồn tại và đang hoạt động.
