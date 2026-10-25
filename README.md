# Hệ thống bán vé tàu xe trực tuyến

Đồ án Quản lý dự án phần mềm. Backend dùng Java 21, Spring Boot 3.5.16, PostgreSQL 16+, Flyway và Spring Security JWT. Frontend React/Vite và VNPAY Sandbox sẽ được triển khai trong các pha tiếp theo.

## Phạm vi hiện tại

P1 cung cấp hạ tầng, 9 bảng và health check. P2 bổ sung đăng ký, đăng nhập, BCrypt, JWT, `/api/v1/auth/me` và phân quyền CUSTOMER/ADMIN. Chưa có CRUD catalog, booking, thanh toán hoặc UI nghiệp vụ.

Public: GET health, POST register/login. GET `/api/v1/auth/me` yêu cầu JWT; `/api/v1/admin/**` chỉ ADMIN. Endpoint chưa được khai báo bị chặn. Không có tài khoản mặc định hoặc đăng ký ADMIN công khai.

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

P2 yêu cầu `JWT_SECRET` là Base64 của ít nhất 32 byte ngẫu nhiên. Thay placeholder trong `.env`; có thể tạo giá trị bằng PowerShell 7:

```powershell
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

Không đưa khóa tạo ra vào Git/tài liệu/log. `.env` trên workspace triển khai hiện tại đã được bổ sung khóa ngẫu nhiên, giữ nguyên cấu hình PostgreSQL local. `JWT_ACCESS_TOKEN_SECONDS` mặc định 900, cho phép 60–3600 giây. Khóa được giữ cố định qua các lần restart để token còn hạn tiếp tục dùng được.

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Health mặc định: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health).

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
# status: UP
```

Profile mặc định yêu cầu `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`. Profile `dev` chỉ có URL database mặc định; mật khẩu và khóa JWT vẫn phải cấu hình. Không tự khởi động PostgreSQL bằng ứng dụng. Spring Boot không tự đọc `.env` nói chung: dự án này khai báo rõ `spring.config.import` cho file Java properties; dùng `KEY=value`, không dùng cú pháp `export` hoặc quote kiểu shell.

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

## Kiểm chứng P2 và sử dụng auth

```powershell
.\scripts\Test-P2.ps1 -JavaHome 'C:\Program Files\Java\jdk-21.0.12' -Offline
```

Script dùng chung lifecycle với P1, chạy toàn bộ tests và kiểm tra register/login/me qua HTTP, email trùng 409, thiếu token 401, CUSTOMER gọi admin 403, hồ sơ ADMIN và token còn dùng được sau restart. Nó chỉ tạo tài khoản trong database kiểm chứng riêng. Logs ở `.local/p2-*/`.

Xem request/response và ví dụ PowerShell tại [API contract](docs/api-contract.md). JWT chỉ nhận qua `Authorization: Bearer <token>`, không nhận cookie/query parameter. Token hết hạn cần đăng nhập lại; chưa có refresh token hoặc endpoint logout thu hồi token. Frontend xử lý lưu token ở P5.

Để có ADMIN, đăng ký tài khoản bình thường rồi quản trị database nâng quyền cho đúng tài khoản:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com';
```

Thay email ví dụ bằng tài khoản thật cần nâng quyền. API register không nhận `role` (gửi lên sẽ trả 400). Quyền truy cập được đọc từ database mỗi request, nên thay đổi quyền có hiệu lực cả với token cũ. Chưa có các API CRUD admin của P3/P7; handler kiểm tra quyền ADMIN trong test chỉ có ở classpath test.

## Database và migration

- Flyway V1 tạo `users`, `routes`, `vehicles`, `seats`, `trips`, `bookings`, `booking_seats`, `payments`, `tickets`.
- V2 chuẩn hóa email và thêm uniqueness/CHECK cho email chuẩn hóa. V1 giữ nguyên. Khi khởi động phiên bản P2, Flyway tự nâng database V1 lên V2.
- PostgreSQL còn có bảng hạ tầng `flyway_schema_history`.
- Nguồn schema có hiệu lực: `backend/src/main/resources/db/migration/`.
- `database/01_schema.sql` là bản tham chiếu từ tài liệu; không chạy thủ công trước Flyway.
- Hibernate dùng `ddl-auto=validate`, không tạo/cập nhật schema.
- Không sửa V1 sau khi đã chia sẻ/áp dụng; thay đổi tiếp theo dùng V2/V3.
- Không dùng `baseline-on-migrate=true` để bỏ qua database có schema chưa được kiểm chứng.
- `TIMESTAMPTZ` biểu diễn thời điểm tuyệt đối; connection/JPA dùng UTC. UI tương lai dùng `Asia/Ho_Chi_Minh`.
- P2 đã có entity `User` được Hibernate validate. Các entity nghiệp vụ khác được bổ sung ở P3–P4.

Nếu database V1 đã có email trùng sau chuẩn hóa, V2 sẽ thất bại nguyên tử và giữ dữ liệu cũ. Xác minh tài khoản trước khi xử lý thủ công; không xóa/merge tự động. Truy vấn kiểm tra:

```sql
SELECT lower(btrim(email)) AS normalized_email, count(*)
FROM users GROUP BY lower(btrim(email)) HAVING count(*) > 1;
```

## Tài liệu và bước tiếp theo

- [Kiến trúc và quyết định P1](docs/architecture.md)
- [ERD](docs/02_ERD.md)
- [Contract API hiện tại](docs/api-contract.md)
- [Báo cáo kiểm chứng P1](docs/P1-report.md)
- [Báo cáo kiểm chứng P2](docs/P2-report.md)

WBS P1: `1.2.2.1`, `1.3.1.1`. WBS P2: `1.2.2.2`, `1.3.1.2`. Tiếp theo là P3: CRUD tuyến/phương tiện/ghế/chuyến và tìm chuyến.
