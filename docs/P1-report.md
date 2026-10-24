# Báo cáo hoàn thành P1

Ngày kiểm chứng: 25/10/2026, giờ Việt Nam. Phạm vi: backend skeleton và migration. Không triển khai P2–P7.

## Kết quả

**P1 đạt tiêu chí nghiệm thu trên môi trường kiểm chứng:** Java 21 biên dịch được ứng dụng, Flyway tạo đúng 9 bảng nghiệp vụ trên PostgreSQL thật, 14 tests pass và JAR trả health UP qua HTTP trong cả hai lần khởi động.

| Hạng mục | Kết quả |
|---|---|
| JDK | Oracle JDK 21.0.12 |
| Spring Boot | 3.5.16 |
| Maven | Wrapper 3.9.12; script 3.3.4; distribution có checksum |
| PostgreSQL kiểm chứng | 18.4, đáp ứng yêu cầu 16+ |
| Flyway | Core + PostgreSQL module 11.20.3 |
| `clean verify` | BUILD SUCCESS |
| Tests | 14 chạy; 0 failures; 0 errors; 0 skipped |
| JAR launch 1 | Health UP; database trống được migrate đến V1 |
| JAR launch 2 | Health UP; schema vẫn V1, không áp dụng lại migration |
| Dọn process kiểm chứng | Ứng dụng và PostgreSQL riêng đã dừng |

## File tạo mới

Workspace ban đầu trống; không ghi đè mã nguồn có sẵn. Các file bàn giao:

- Gốc: `.gitignore`, `.gitattributes`, `.env.example`, `.env.test.example`, `README.md`.
- Build: `backend/pom.xml`, `backend/mvnw`, `backend/mvnw.cmd`, `backend/.mvn/wrapper/maven-wrapper.properties`.
- Application: `backend/src/main/java/com/example/ticketbooking/TicketBookingApplication.java`.
- Security: `backend/src/main/java/com/example/ticketbooking/config/SecurityConfig.java`.
- Lỗi API: `backend/src/main/java/com/example/ticketbooking/common/ApiError.java`, `ApiException.java`, `GlobalExceptionHandler.java`.
- Cấu hình: `backend/src/main/resources/application.yml`, `application-dev.yml`, `backend/src/test/resources/application-test.yml`.
- Migration: `backend/src/main/resources/db/migration/V1__initial_schema.sql`.
- Tests: `backend/src/test/java/com/example/ticketbooking/InfrastructureIntegrationTest.java`, `backend/src/test/java/com/example/ticketbooking/common/GlobalExceptionHandlerTest.java`.
- Database: `database/01_schema.sql`, `database/README.md`.
- Tài liệu: `docs/architecture.md`, `docs/02_ERD.md`, `docs/api-contract.md`, `docs/P1-report.md`.
- Kiểm chứng Windows: `scripts/Test-P1.ps1`.
- Frontend: `frontend/README.md` ghi rõ chưa triển khai React.

`backend/target/` và `.local/` là output tạm được bỏ qua trong Git, không phải mã nguồn bàn giao. Workspace chưa khởi tạo Git; không có commit/branch được tạo trong P1.

## Lệnh đã chạy

Các bước chính, từ thư mục gốc trừ khi ghi khác:

```powershell
& 'C:\Program Files\Java\jdk-21.0.12\bin\java.exe' -version
& 'C:\Program Files\Java\jdk-21.0.12\bin\javac.exe' -version
& 'C:\Program Files\PostgreSQL\18\bin\postgres.exe' --version

# Trong backend
.\mvnw.cmd --version
.\mvnw.cmd -B -o -DskipTests package

# Tại thư mục gốc: lần online bổ sung dependency còn thiếu;
# lần cuối chạy trọn bộ offline bằng cấu hình đã hoàn thiện
.\scripts\Test-P1.ps1 -JavaHome 'C:\Program Files\Java\jdk-21.0.12'
.\scripts\Test-P1.ps1 -JavaHome 'C:\Program Files\Java\jdk-21.0.12' -Offline
```

Script cuối thực thi `mvnw.cmd -o -B clean verify`, tạo hai database độc lập, chạy JAR hai lần và gọi HTTP `/actuator/health`. Credentials ngẫu nhiên chỉ được truyền qua môi trường process; không ghi vào tài liệu hoặc mã nguồn.

## Phạm vi test đã pass

**4 test xử lý lỗi:** validation có field errors; JSON sai không lộ parser details; exception nghiệp vụ trả 409; exception bất ngờ trả 500 không lộ message/stacktrace.

**10 test integration trên PostgreSQL:** V1/Flyway validate và đúng 9 bảng; connection UTC; health công khai không lộ details; anonymous nhận JSON 401; authenticated bị chặn 403 trước khi có contract P2; unique ghế active; ghế đã giải phóng có thể đặt lại và cùng ghế dùng ở chuyến khác; FK kép ghế/phương tiện; FK kép booking/chuyến/phương tiện; CHECK thời gian chuyến; vé duy nhất theo booking seat. Mục kiểm tra V1/Flyway/UTC nằm trong một test method; các tình huống khác theo từng test method.

Các test constraint không thay thế test nghiệp vụ/concurrency P4 hoặc payment P6. Chưa có JPA entity nghiệp vụ để kiểm chứng mapping; Hibernate được cấu hình `validate` và sẽ kiểm tra thêm khi entities được bổ sung.

## Bằng chứng

Lần chạy hoàn tất được lưu ở `.local/p1-20261025-022455-5eef1c39/`:

- `verification.json`: kết quả tổng hợp, thời điểm UTC `2026-10-24T19:25:44Z` (25/10/2026 theo giờ Việt Nam).
- `application-1.out.log`, `application-2.out.log`: Tomcat khởi động, Flyway và app startup.
- `pgctl-stop.out.log`: PostgreSQL đã dừng.
- Maven XML/text reports: `backend/target/surefire-reports/`.

Script quan sát đúng một migration thành công ở database chạy JAR sau mỗi lần launch. Bản SQL tham chiếu và V1 có SHA-256 trùng nhau; SQL có đúng 9 `CREATE TABLE`, không có câu lệnh `CREATE DATABASE` thực thi.

## Sự cố đã xử lý và giới hạn

- Java trong PATH trỏ qua Oracle shim bị treo: sử dụng JDK 21 qua `JAVA_HOME` đã xác minh.
- Maven cache thiếu POM/plugin: lần offline đầu không đủ điều kiện; đã tải dependency và lần `clean verify` offline cuối pass.
- Flyway mặc định của Boot cảnh báo PostgreSQL 18: khóa cả hai module Flyway ở 11.20.3, chạy lại tests/migration thành công và không còn cảnh báo đó.
- Java NIO trên máy này lỗi AF_UNIX ở thư mục socket mặc định: đã kiểm tra bằng `Selector.open()` và chạy JAR thành công khi đặt `jdk.net.unixdomain.tmpdir` vào `.local/sockets`. Script áp dụng cấu hình riêng cho process; README có hướng dẫn chạy thủ công.
- Trên Windows, `Start-Process -Wait` chờ cả PostgreSQL process con; script đã đổi sang chờ riêng `pg_ctl` bằng `WaitForExit()` và kiểm chứng đầy đủ.
- Docker Engine không được sử dụng; không dùng H2 hoặc database giả.
- Kiểm chứng thực tế trên PostgreSQL 18.4; chưa chạy ma trận riêng PostgreSQL 16/17 hoặc Linux/macOS.
- Test framework có cảnh báo Mockito dynamic agent trên JDK 21; tests vẫn pass. Không ảnh hưởng JAR runtime.
- Chưa có JWT, tài khoản ứng dụng, entities nghiệp vụ, dữ liệu demo, frontend hoặc thanh toán. Health chỉ công khai tình trạng tổng hợp.
- Database cá nhân và secrets triển khai chưa được cấu hình; không tác động database/service hiện có. Dùng README để tạo môi trường phát triển thường trực.

## WBS và bước tiếp theo

P1 liên kết `1.2.2.1` (ERD/lược đồ PostgreSQL) và `1.3.1.1` (Spring Boot/PostgreSQL). Đã sửa cách gọi công nghệ trong hồ sơ mới thành PostgreSQL; không sửa file Word gốc hoặc cập nhật Trello.

Bước tiếp theo là P2: user entity/DTO/repository/service, BCrypt, register/login/me, JWT và SecurityFilterChain theo quyền CUSTOMER/ADMIN. P2 chưa được triển khai trong lượt này.
