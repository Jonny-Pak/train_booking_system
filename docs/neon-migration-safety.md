# Audit an toàn Flyway V1–V10 trước khi khởi tạo Neon

Ngày kiểm chứng: **26/10/2026**. Đã đọc toàn bộ 10 file SQL, cấu hình chung/prod và các seed. Không sửa file migration, không kết nối Neon, không thay đổi PostgreSQL local hiện hữu, không bật cờ migration trên production.

## Kết luận cho Neon mới, chưa có dữ liệu

**Có thể chủ động đặt `SPRING_FLYWAY_ENABLED=true` khi deploy lần đầu**, sau khi xác nhận đúng database trống và cấu hình Render/Neon đã sẵn sàng. Không có migration nào cần sửa để chạy từ đầu trên PostgreSQL Neon mới. Profile prod vẫn mặc định false; audit này không tự đổi cấu hình cloud hay `.env`.

Các câu SQL dùng tính năng PostgreSQL thông thường có trên PostgreSQL 16+: identity, TIMESTAMPTZ, BYTEA, partial/expression indexes, CHECK/FK, DO/PLpgSQL, generate_series. Không yêu cầu extension bổ sung hay superuser. Kiểm thử thực tế dùng PostgreSQL **18.4**, role là database owner **không superuser/CREATEDB/CREATEROLE**. Điều này hỗ trợ kết luận tương thích SQL; chưa phải kiểm chứng kết nối TLS hay quyền thực tế của role trên Neon.

## Đánh giá từng migration

| Migration | Database mới/trống | Điều kiện cần chú ý với database cũ |
| --- | --- | --- |
| V1 | An toàn: tạo 9 bảng nghiệp vụ, identity, FK, CHECK, index. | Phải là schema chưa được tạo thủ công. Không chạy `01_schema.sql` trước Flyway. |
| V2 | An toàn: UPDATE 0 dòng, thêm chuẩn hóa/unique email. | Email trùng sau lower/trim làm migration dừng và rollback; không gộp/xóa tài khoản. |
| V3 | An toàn: thêm index tìm chuyến, lịch phương tiện, booking. | Tạo index có thể giữ khóa/tốn thời gian khi dữ liệu lớn. |
| V4 | An toàn: thêm index lịch sử đơn. | Tương tự V3; không sửa dữ liệu. |
| V5 | An toàn: thêm email verification; tài khoản mới mặc định chưa xác nhận. | Tài khoản đã tồn tại được coi là verified theo thiết kế trước đây; token có CHECK và unique index. |
| V6 | An toàn: backfill 0 dòng, thêm metadata thanh toán, NOT NULL và unique pending payment. | Nhiều payment PENDING/booking làm unique index thất bại; rollback cả DDL/backfill của V6, không tự chọn/xóa giao dịch. |
| V7 | An toàn: thêm layout/tọa độ, CHECK và unique vị trí. | Dữ liệu cũ mặc định CUSTOM, tọa độ null được hỗ trợ. |
| V8 | An toàn: không có BUS_16 nên không chèn/cập nhật chỗ; thay 2 CHECK. | BUS_16 phải có đúng 16 chỗ/tọa độ hợp lệ. Mã A17–A34 không được trùng mã cũ. Vi phạm thì rollback. |
| V9 | An toàn: không có BUS_34 nên UPDATE 0 dòng; thay 2 CHECK. | BUS_34 phải có đúng 34 chỗ STANDARD ở tầng 1. Chuyển class/tầng/layout, không đổi ID hay mã chỗ. |
| V10 | An toàn: tạo bảng site_settings và một dòng hướng dẫn mặc định. | Chưa được tạo bảng này thủ công hoặc chèn id=1 ngoài lịch sử migration. |

Không có `DROP TABLE`, `DROP DATABASE`, `DROP SCHEMA`, `TRUNCATE` hay `DELETE FROM` trong V1–V10. Có UPDATE có chủ đích ở V2, V6, V8, V9 và INSERT ở V8/V10. Không nên diễn giải là "migration hoàn toàn không thay đổi dữ liệu" khi chạy trên database cũ.

## Phân tích chính xác V8 và V9

Hai CHECK được thay ở **mỗi** migration:

```text
vehicles.chk_vehicle_seat_layout
seats.chk_seat_position
```

Tổng là **4 câu DROP CONSTRAINT nhưng chỉ có 2 tên ràng buộc**, được thay liên tiếp qua hai phiên bản. Đây không phải PK, FK hoặc unique index chống giữ trùng chỗ. Không có DROP CASCADE, đổi ID, reset sequence hay xóa bản ghi.

### V8: BUS_16 → BUS_34

1. Trước DDL, kiểm tra mỗi xe BUS_16 có đúng 16 chỗ, coach=0, tầng=1, vị trí 1–16. Sai thì raise exception trước khi DROP.
2. DROP hai CHECK cũ.
3. ADD lại CHECK vị trí, nới nhánh chỗ xe STANDARD từ 1–16 thành 1–34. Nhánh null cho chỗ legacy và các nhánh toa tàu giữ nguyên.
4. INSERT A17–A34 cho xe BUS_16. Chỗ 1–16 giữ ID/mã cũ. Nếu một mã mới đã được dùng cho một chỗ cũ, unique(vehicle_id,seat_code) chặn và rollback; không ghi đè chỗ đó.
5. UPDATE layout thành BUS_34; ADD lại CHECK layout với BUS_34/BUS, giữ CUSTOM và TRAIN_11/TRAIN.

### V9: BUS_34 → BUS_SLEEPER_34

1. Kiểm tra đủ 34 chỗ STANDARD, coach=0, tầng=1, vị trí 1–34.
2. DROP cùng hai CHECK.
3. UPDATE chỗ của xe BUS_34: class BUS_SLEEPER, tầng `(position_number-1)/17+1`; không đổi vị trí, mã hoặc ID.
4. UPDATE layout BUS_SLEEPER_34.
5. ADD lại CHECK layout và vị trí: 34 giường, vị trí 1–17 tầng 1; 18–34 tầng 2. Các nhánh tàu/CUSTOM giữ nguyên.

Điều kiện `compartment_number IS NULL` của xe coach=0 và ghế toa 1–2 **đã có từ V7**, không phải ràng buộc mới được thêm vào V8/V9.

Các CHECK được áp dụng trên bảng, không chỉ các dòng đang chuyển layout. Flyway chạy từng migration bằng transaction trên PostgreSQL; lỗi sau khi DROP cũng khôi phục CHECK và dữ liệu trong migration đó. **Không phải toàn bộ V1–V10 là một transaction duy nhất**: nếu V8 thất bại, V1–V7 đã thành công vẫn còn, V8 rollback. Không sửa migration đã áp dụng hoặc dùng repair/baseline để bỏ qua lỗi dữ liệu.

## Dữ liệu mẫu và startup

- `database/02_demo_seed.sql`: seed P3 tự chọn, phương tiện CUSTOM cũ, không tự chạy lúc ứng dụng khởi động.
- `database/03_sample_trips.sql`: seed riêng 7 ngày tương đối với ngày chạy, layout chuẩn 34 giường/11 toa khi tạo mới; không tự chạy qua Flyway.
- `database/04_sample_trips_20261027_20261110.sql`: seed riêng 150 chuyến/15 ngày cố định, chạy thủ công sau migration; không tự chạy qua Flyway.
- V10 có **một dòng site_settings** để trang hướng dẫn hoạt động; đó là dữ liệu cấu hình mặc định, không phải tài khoản/chuyến/đơn mẫu.
- Không có CommandLineRunner/ApplicationRunner tạo seed trong backend. Prod đặt `spring.sql.init.mode=never`. Script test catalog có gọi seed riêng nhưng chỉ trên database test của chính nó.

Kết quả fresh audit: 10 bảng ứng dụng + flyway_schema_history; **0 users/routes/vehicles/seats/trips/bookings/booking_seats/payments/tickets**, **1 site_settings**. Không có Admin mẫu hoặc mật khẩu mẫu tự sinh.

## Kiểm thử độc lập đã qua

Chạy từ root:

```powershell
pwsh -NoProfile -File scripts/Test-MigrationSafety.ps1 `
  -JavaHome 'C:\Program Files\Java\jdk-21.0.12' -Offline
```

Script không đọc `.env`, DB_URL hoặc URL Neon. Tạo cluster dưới `.local/migration-audit-*`, cổng loopback ngẫu nhiên khác 5432, 8 database `migration_audit_*`, role owner không superuser. Test chỉ chấp nhận prefix JDBC loopback và tên database audit. Kết thúc dừng đúng cluster tạm, không chạy DROP DATABASE/SCHEMA và không tác động tiến trình PostgreSQL ứng dụng.

Kết quả cuối: **14 tests passed, 0 failure/error/skipped** — 8 bài migration + 6 bài cấu hình production:

1. Database trống: V1–V10 thành công, validate thành công, CHECK được validate, không cần superuser.
2. Chạy lại migrate: 0 migration mới, không nhân đôi dòng cấu hình/seed nghiệp vụ.
3. Nâng cấp V7 → V8 → V10 với booking/payment/ticket có sẵn: 16 ID chỗ cũ giữ nguyên, thành 34 giường ×2 tầng, chỗ tàu không đổi; toàn bộ dòng booking/booking_seats/payment/ticket giữ nguyên; các FK và index giữ chỗ còn tồn tại.
4. BUS_16 chỉ có 15 chỗ: V8 bị từ chối trước DROP; giữ dữ liệu và CHECK cũ.
5. BUS_34 chỉ có 33 chỗ: V9 bị từ chối trước DROP; giữ dữ liệu và CHECK cũ.
6. Email trùng sau chuẩn hóa: V2 rollback, giữ cả hai tài khoản.
7. Hai payment PENDING cùng booking: V6 rollback DDL/backfill, giữ nguyên giao dịch.
8. Mã A17 đã dùng cho một chỗ cũ: V8 lỗi sau khi thay CHECK; rollback khôi phục CHECK và 16 dòng ban đầu.
9. Trigger lỗi **chỉ trong database test** tại bước UPDATE layout của V9, sau khi chỗ đã chuyển đổi: rollback khôi phục CHECK, layout BUS_34 và toàn bộ 34 chỗ STANDARD. Đây là fault injection, không phải trigger hoặc lỗi hiện có trong nghiệp vụ production.

Hai mục đầu và mục nâng cấp nằm trong các test tổng hợp, nên số tình huống nhiều hơn số method test migration. Log có các dòng ERROR do chủ động tạo dữ liệu/lỗi sai để kiểm chứng rollback; bộ test cuối cùng đã qua.

Bằng chứng: `.local/migration-audit-20261026-210953-36f2af8c/verification.json`, `fresh-schema.json`, JUnit XML và `.local/migration-safety-verified.log`. Cluster test PostgreSQL 18.4 cổng **63521** đã được dừng. File SQL V1–V10 và code Java nghiệp vụ không thay đổi. Chưa kiểm thử trên dịch vụ Neon thật, chưa xác minh version PostgreSQL/quyền/TLS của project cloud.

## Cấu hình prod và quy trình khởi tạo Neon

Cấu hình hiện tại giữ:

```yaml
spring.jpa.hibernate.ddl-auto: validate
spring.sql.init.mode: never
spring.flyway.enabled: ${SPRING_FLYWAY_ENABLED:false}
spring.flyway.clean-disabled: true
spring.flyway.baseline-on-migrate: false
spring.flyway.validate-on-migrate: true
```

Quy trình đề xuất:

1. Chưa chạy từ máy local với Neon credentials, không sửa DB_URL local. Reset mật khẩu Neon từng xuất hiện trong ảnh; chỉ nhập mật khẩu mới vào Render.
2. Trên Neon, xác nhận production/neondb/neondb_owner và PostgreSQL 16+. Nếu có dữ liệu hoặc object cũ, dừng để đối chiếu, backup/nhánh thử nghiệm trước khi nâng cấp.
3. Kiểm tra chỉ đọc:

```sql
SELECT current_database(), current_user;
SHOW server_version;
SELECT table_name FROM information_schema.tables
WHERE table_schema='public' AND table_type='BASE TABLE'
ORDER BY table_name;
SELECT to_regclass('public.flyway_schema_history');
```

4. Với schema mới: không chạy `01_schema.sql`, không seed/import dump trước. Nếu có bảng ứng dụng nhưng không có lịch sử Flyway, không tự baseline hoặc xóa bảng.
5. Render dùng profile prod, JDBC **direct** không có `-pooler`, SSL/channelBinding và ba biến `SPRING_DATASOURCE_*`. Không nhập user/password vào URL hay GitHub. Đặt JWT/origin/mail theo `render-neon.md`.
6. **Lúc đã sẵn sàng khởi tạo**, chủ động đặt `SPRING_FLYWAY_ENABLED=true` trong Render rồi deploy một instance. Kết luận audit cho phép dùng nguyên V1–V10 với database trống; không có bước bỏ qua migration hoặc sửa checksum.
7. Flyway tạo lịch sử, áp dụng đủ V1–V10, sau đó Hibernate validate. Nếu có lỗi, xem log và trạng thái migration; không xóa schema hoặc bật Hibernate create/update để xử lý.
8. Sau deploy, chạy truy vấn đọc sau (chỉ khi bảng lịch sử đã tồn tại):

```sql
SELECT version, description, success
FROM flyway_schema_history ORDER BY installed_rank;
SELECT count(*) FROM site_settings;
```

   Cần 10 migration thành công, 10 bảng ứng dụng, 1 dòng cấu hình. Kiểm tra `/actuator/health`, restart một lần để xác nhận migrate không lặp.
9. Chưa có chuyến hoặc Admin là trạng thái đúng của database mới. Tạo dữ liệu bằng luồng ứng dụng/quy trình quản trị; seed mẫu chỉ khi chủ động chọn môi trường demo. Không đưa database production vào Test-P1/P7 hoặc script audit.

Không cần sửa migration nào trong lượt này. Với database cũ bị sai dữ liệu, cần xử lý dữ liệu có kiểm soát trước phiên bản bị chặn, không sửa SQL lịch sử. Cờ prod mặc định vẫn false để tránh tự chạy DDL; chỉ đổi trong môi trường Render khi bạn triển khai theo các bước trên.

Nguồn chính thức: [Neon yêu cầu direct connection cho Flyway](https://neon.com/docs/guides/flyway), [PostgreSQL CHECK/FK/UNIQUE](https://www.postgresql.org/docs/16/ddl-constraints.html). Kết luận rollback/data retention được kiểm chứng bằng test thực tế nêu trên.
