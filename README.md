# Hệ thống bán vé tàu xe trực tuyến

Đồ án Quản lý dự án phần mềm. Backend dùng Java 21, Spring Boot 3.5.16, PostgreSQL 16+ và Flyway. Frontend React/Vite, JWT và VNPAY Sandbox sẽ được triển khai trong các pha tiếp theo.

## Phạm vi hiện tại

P1 cung cấp backend skeleton, cấu hình môi trường, migration cho 9 bảng, lỗi JSON, health check và kiểm thử PostgreSQL thật. Chưa có API đăng nhập, CRUD, booking, thanh toán hoặc UI nghiệp vụ.

Spring Security hiện chỉ mở `GET /actuator/health`; các request khác bị từ chối. Chưa có tài khoản mặc định, Basic Auth hoặc JWT. Quy tắc endpoint và xác thực JWT được bổ sung ở P2.

## Yêu cầu

- JDK **21**; `JAVA_HOME` trỏ tới thư mục JDK, không phải `bin` hoặc JRE.
- PostgreSQL **16 trở lên**, database trống và tài khoản sở hữu schema.
- Không cần cài Maven riêng: Wrapper khóa Maven **3.9.12**, có checksum SHA-256.
- Internet cho lần tải Maven/dependencies đầu tiên.
- PowerShell 7 nếu dùng script kiểm chứng tự động trên Windows.

Kiểm tra Java qua `JAVA_HOME` nếu `java` trong PATH trỏ tới Oracle shim:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12' # thay bằng đường dẫn thực tế
& "$env:JAVA_HOME\bin\java.exe" -version
cd backend
.\mvnw.cmd --version
```

## Chuẩn bị PostgreSQL

Tạo database bên ngoài Flyway. Ví dụ chạy `psql -U postgres -d postgres` rồi thực hiện:

```sql
CREATE ROLE ticket_booking_app LOGIN;
\password ticket_booking_app
CREATE DATABASE ticket_booking OWNER ticket_booking_app;
CREATE ROLE ticket_booking_test LOGIN;
\password ticket_booking_test
CREATE DATABASE ticket_booking_test OWNER ticket_booking_test;
```

`\password` hỏi mật khẩu tương tác, tránh ghi mật khẩu vào lịch sử SQL. Đây là môi trường phát triển; user app sở hữu database để Flyway tạo schema. Môi trường triển khai sau này có thể tách migration user và runtime user.

Nếu `psql` chưa có trong PATH, gọi trực tiếp, ví dụ:

```powershell
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -U postgres -d postgres
```

## Cấu hình và chạy backend

Tại thư mục gốc:

```powershell
Copy-Item .env.example .env
Copy-Item .env.test.example .env.test
```

Sửa mật khẩu/URL trong các file vừa sao chép. Không ghi đè file cấu hình cá nhân đã có. Các file này bị Git bỏ qua; Spring đọc chúng khi chạy từ thư mục gốc hoặc `backend/`. Biến môi trường hệ điều hành được ưu tiên hơn file.

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Health mặc định: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health).

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
# status: UP
```

Profile mặc định yêu cầu `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Profile `dev` chỉ có URL mặc định; mật khẩu vẫn phải cấu hình. Không tự khởi động PostgreSQL bằng ứng dụng. Spring Boot không tự đọc `.env` nói chung: dự án này khai báo rõ `spring.config.import` cho file Java properties; dùng `KEY=value`, không dùng cú pháp `export` hoặc quote kiểu shell.

## Build và kiểm thử

Tests integration chỉ dùng database riêng trong `.env.test` hoặc các biến `TEST_DB_*`. Tuyệt đối không trỏ database test vào database nghiệp vụ. Tests chạy Flyway rồi rollback dữ liệu fixture theo từng test.

```powershell
cd backend
.\mvnw.cmd clean verify
java -jar target/ticket-booking-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

Nếu PATH của Java bị lỗi, thay `java` bằng `& "$env:JAVA_HOME\bin\java.exe"`.

Trên Linux/macOS:

```bash
cd backend
sh mvnw clean verify
sh mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Không cần database cho các test lỗi HTTP độc lập:

```powershell
.\mvnw.cmd "-Dtest=GlobalExceptionHandlerTest" test
```

## Kiểm chứng P1 bằng cụm PostgreSQL riêng trên Windows

Script dưới đây dùng PostgreSQL binaries đã cài, tạo cluster mới trong `.local/`, mật khẩu ngẫu nhiên, chỉ bind `127.0.0.1`, chọn cổng trống và tạo hai database riêng. Nó chạy build/tests, khởi động JAR hai lần, kiểm tra health/Flyway rồi dừng các process do nó tạo. Không thay đổi Windows service hoặc database đang dùng.

```powershell
# chạy tại thư mục gốc, bằng PowerShell 7
.\scripts\Test-P1.ps1 `
  -JavaHome 'C:\Program Files\Java\jdk-21.0.12' `
  -PostgresBin 'C:\Program Files\PostgreSQL\18\bin'
# thêm -Offline nếu Maven/dependencies đã được cache
```

Trên máy kiểm chứng, thư mục TEMP mặc định gây lỗi Java `Unable to establish loopback connection` / `UnixDomainSockets ... Invalid argument: connect`. Script đặt `jdk.net.unixdomain.tmpdir` vào `.local/sockets` để tránh lỗi môi trường này. Khi chạy JAR trực tiếp trên máy gặp cùng lỗi, tạo thư mục socket riêng rồi truyền JVM property **trước** `-jar`:

```powershell
# chạy tại thư mục gốc; chỉ cần khi máy có lỗi loopback nói trên
New-Item -ItemType Directory -Path .local\sockets -Force | Out-Null
$socketDir = (Resolve-Path .local\sockets).Path
& "$env:JAVA_HOME\bin\java.exe" "-Djdk.net.unixdomain.tmpdir=$socketDir" `
  -jar backend/target/ticket-booking-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

Nếu chạy bằng Maven trên máy có lỗi này, truyền `"-Dspring-boot.run.jvmArguments=-Djdk.net.unixdomain.tmpdir=<absolute_socket_directory>"` cho `mvnw.cmd spring-boot:run`.

Logs và `verification.json` nằm trong `.local/p1-*/`; dữ liệu được giữ để chẩn đoán, không đưa vào Git. User superuser `p1_runner` chỉ tồn tại trong cluster kiểm chứng riêng. Không tái sử dụng cluster này cho triển khai thật. Script cần quyền chạy PostgreSQL process trên máy.

## Database và migration

- Flyway V1 tạo `users`, `routes`, `vehicles`, `seats`, `trips`, `bookings`, `booking_seats`, `payments`, `tickets`.
- PostgreSQL còn có bảng hạ tầng `flyway_schema_history`.
- Nguồn schema có hiệu lực: `backend/src/main/resources/db/migration/`.
- `database/01_schema.sql` là bản tham chiếu từ tài liệu; không chạy thủ công trước Flyway.
- Hibernate dùng `ddl-auto=validate`, không tạo/cập nhật schema.
- Không sửa V1 sau khi đã chia sẻ/áp dụng; thay đổi tiếp theo dùng V2/V3.
- Không dùng `baseline-on-migrate=true` để bỏ qua database có schema chưa được kiểm chứng.
- `TIMESTAMPTZ` biểu diễn thời điểm tuyệt đối; connection/JPA dùng UTC. UI tương lai dùng `Asia/Ho_Chi_Minh`.
- Chưa có entities nghiệp vụ trong P1; kiểm tra mapping Hibernate được mở rộng theo P2–P4.

## Tài liệu và bước tiếp theo

- [Kiến trúc và quyết định P1](docs/architecture.md)
- [ERD](docs/02_ERD.md)
- [Contract API hiện tại](docs/api-contract.md)
- [Báo cáo kiểm chứng P1](docs/P1-report.md)

WBS P1: `1.2.2.1` — ERD/lược đồ **PostgreSQL**, `1.3.1.1` — Spring Boot và **PostgreSQL**. P2 tiếp theo triển khai user entity, BCrypt, register/login/me, JWT và phân quyền.
