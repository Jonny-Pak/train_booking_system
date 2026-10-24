# Kiến trúc và quyết định P1

## Phạm vi

Một Spring Boot application và một PostgreSQL theo Modular Monolith. P1 dựng hạ tầng; chưa triển khai nghiệp vụ hoặc frontend.

Stack khóa: Java 21, Spring Boot 3.5.16, Maven Wrapper 3.9.12, Spring Data JPA/Hibernate, Flyway Core và module PostgreSQL 11.20.3. Dependency versions lấy từ Spring Boot BOM, ngoại trừ Flyway được khóa đồng bộ core/module ở 11.20.3 để hỗ trợ PostgreSQL 18 đang có trên máy. Bản mặc định của Boot cảnh báo chỉ được kiểm chứng đến PostgreSQL 17.

Các module dự kiến: `auth`, `user`, `route`, `vehicle` (gồm ghế), `trip`, `booking`, `payment`, `ticket`, `admin`. Chỉ tạo module khi có mã thực tế, tránh các lớp/trang giữ chỗ không có hành vi.

## Trách nhiệm

Controller nhận DTO và kiểm tra input; service thực thi nghiệp vụ/transaction; repository truy cập dữ liệu. Không trả entity JPA trực tiếp qua REST. Backend quyết định user, giá, thanh toán và quyền sở hữu dữ liệu.

`common` hiện có lỗi API, exception nghiệp vụ và exception advice. `config` có security baseline: stateless, không login mặc định, chỉ GET health công khai. JWT, CORS theo frontend origin và chính sách token thuộc P2/P5.

## Database

Giữ nguyên chín bảng và các ràng buộc trong SQL đính kèm. PostgreSQL thật chịu trách nhiệm FK kép, CHECK và partial unique index. JPA không thay thế database constraints.

`ddl-auto=validate`; Flyway `clean` bị vô hiệu hóa; không tự baseline schema có sẵn. Connection và Hibernate dùng UTC. Tiền dùng `NUMERIC(12,2)` và sẽ map sang `BigDecimal` khi triển khai entity.

Tính duy nhất active `(trip_id, seat_id)` chỉ bảo vệ hàng có `is_active=true`; không tự giải phóng hold theo thời gian. SQL chưa bảo đảm booking có ghế, tổng tiền đúng, vé chỉ phát hành sau PAID hoặc liên kết trạng thái giữa payment và booking. Các invariant này cần service và integration test ở P4/P6.

## Quyết định cho các pha sau

Các mục sau là thiết kế, chưa được triển khai:

- P4 khóa theo chuyến; thứ tự khóa thống nhất Trip → Booking → Payment, transaction ngắn.
- Expiry/cancel cập nhật booking và toàn bộ booking seats trong cùng transaction.
- Booking PAID giữ ghế active. Không hủy PAID khi chưa có hoàn tiền.
- P6 IPN xác minh chữ ký, merchant, txn_ref, amount và trạng thái; khóa và xử lý idempotent.
- Payment, booking và tickets được cập nhật atomically; Return không phát hành vé.
- IPN thành công đến sau expiry/cancel không lấy lại ghế; cần đối soát. Xem xét migration bổ sung dấu hiệu đối soát ở P6.
- Không sửa phương tiện hoặc ghế của chuyến đã có booking; không xóa lịch sử đã tham chiếu.

## Nguồn và tương thích

- [Spring Boot 3.5 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [PostgreSQL 16 partial indexes](https://www.postgresql.org/docs/16/indexes-partial.html)
- [Flyway 11.20.3 PostgreSQL support implementation](https://github.com/flyway/flyway/blob/flyway-11.20.3/flyway-database/flyway-database-postgresql/src/main/java/org/flywaydb/database/postgresql/PostgreSQLDatabase.java)
- Maven Wrapper scripts lấy từ tag chính thức `maven-wrapper-3.3.4`, loại `only-script`; Maven archive được đối chiếu SHA-512 chính thức trước khi khóa SHA-256 trong wrapper properties.
