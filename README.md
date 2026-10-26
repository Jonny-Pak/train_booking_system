# Hệ thống bán vé tàu xe trực tuyến

Đồ án Quản lý dự án phần mềm. Backend dùng Java 21, Spring Boot 3.5.16, PostgreSQL 16+, Flyway và Spring Security JWT. Frontend React/Vite cho Customer; P6 có tích hợp VNPAY Sandbox và vé điện tử.

## Phạm vi hiện tại

P1 cung cấp hạ tầng, 9 bảng và health check. P2 có auth/JWT và phân quyền. P3 có catalog/tìm chuyến. P4 có booking, giữ ghế, lịch sử, hủy và scheduler hết hạn. P5 có giao diện Customer tiếng Việt để sử dụng các API này. Chưa có thanh toán hoặc UI quản trị.

Public: GET health, POST register/login/verify-email/resend-verification dưới `/api/v1/auth`, GET `/api/v1/trips`, `/api/v1/trips/{id}`, `/api/v1/trips/{id}/seats`. GET `/api/v1/auth/me` yêu cầu JWT; `/api/v1/admin/**` chỉ ADMIN. Endpoint chưa được khai báo bị chặn. Không có tài khoản mặc định hoặc đăng ký ADMIN công khai.

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

Thay email ví dụ bằng tài khoản thật cần nâng quyền. API register không nhận `role` (gửi lên sẽ trả 400). Quyền truy cập được đọc từ database mỗi request, nên thay đổi quyền có hiệu lực cả với token cũ. ADMIN có CRUD catalog và danh sách/chi tiết/hủy booking; dashboard và thống kê thuộc các pha sau.

## P3 Catalog và tìm chuyến

Sơ đồ chuẩn xe giường nằm 34 chỗ và tàu 11 toa/434 chỗ: [cấu hình, API, bảo vệ lịch sử và kiểm thử](docs/seat-layouts.md). V7 bổ sung vị trí ghế, giữ chín bảng.

Hướng dẫn tích hợp VNPAY Sandbox qua HTTPS công khai trên Windows: [Cloudflare Tunnel, Return và IPN](docs/vnpay-sandbox-windows.md).

```powershell
# toàn bộ tests + auth/catalog HTTP smoke + seed lặp + restart, trên cluster riêng
.\scripts\Test-P3.ps1 -JavaHome 'C:\Program Files\Java\jdk-21.0.12' -Offline

# ví dụ tìm chuyến khi backend local đã chạy; thay tên/ngày theo dữ liệu thực tế
Invoke-RestMethod 'http://localhost:8080/api/v1/trips?date=2030-01-02&vehicleType=BUS&page=0&size=20'
```

Danh sách admin: `/api/v1/admin/routes`, `/vehicles`, `/trips`; quản lý ghế dưới `/api/v1/admin/vehicles/{vehicleId}/seats`. GET item/POST/PUT/DELETE có trong [contract catalog](docs/catalog-api.md). PUT thay đầy đủ các field; tạo chuyến sau khi tuyến, phương tiện và ghế đã có.

Ngày tìm chuyến dùng `Asia/Ho_Chi_Minh`; timestamps đầu vào phải có `Z` hoặc offset, response dùng UTC. Phân trang bắt đầu từ 0, size 1–100; tìm chuyến sắp xếp cố định theo giờ đi rồi ID. Chỉ công khai chuyến SCHEDULED chưa khởi hành với tuyến/phương tiện active. Trạng thái ghế là AVAILABLE/HELD/BOOKED; GET không tạo hoặc giải phóng hold.

Không xóa tuyến/phương tiện đang được tham chiếu. Chuyến có booking history không được sửa/xóa; cấu trúc ghế của phương tiện có booking history không được đổi. Chuyến cùng phương tiện không được chồng khoảng `[departureAt, arrivalAt)`; hai chuyến nối đúng giờ được phép.

### Dữ liệu demo

`database/02_demo_seed.sql` chỉ chạy thủ công trên database demo riêng đã được Flyway migrate. Không chạy trong database có dữ liệu người dùng thật:

```powershell
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -h localhost -p 5432 `
  -U '<demo_user>' -d ticket_booking_demo -W -v ON_ERROR_STOP=1 -f database/02_demo_seed.sql
```

User/database demo cần được tạo trước; chạy backend với cấu hình trỏ đúng database đó để migrate trước khi seed. Script không tạo user/mật khẩu; đánh dấu phương tiện bằng `DEMO-P3-*` và tên `[DEMO P3]`, tạo 8 ghế/phương tiện và chuyến sau 7 ngày tính từ lần seed đầu. Chạy lại không đổi giờ hoặc nhân đôi chuyến. Seed từ chối nếu phương tiện demo đã có booking history. Không có seed tự chạy lúc startup. Logs kiểm chứng P3 ở `.local/p3-*/`.

## P4 Booking và giữ ghế

```powershell
.\scripts\Test-P4.ps1 -JavaHome 'C:\Program Files\Java\jdk-21.0.12' -Offline
```

Script tạo cluster PostgreSQL riêng, chạy toàn bộ tests, HTTP auth/catalog/booking, kiểm tra scheduler thật và dữ liệu giữ nguyên sau restart. Logs và bằng chứng nằm trong `.local/p4-*/`. Không tác động database local của người dùng.

API: POST `/api/v1/bookings`, GET `/api/v1/bookings/my`, GET `/api/v1/bookings/{id}`, POST `/api/v1/bookings/{id}/cancel`; ADMIN xem GET `/api/v1/admin/bookings`. Xem [contract Booking](docs/booking-api.md).

Mặc định giữ ghế 600 giây, không quá giờ khởi hành; cấu hình `BOOKING_HOLD_SECONDS` trong khoảng 30–1800. Scheduler bật mặc định, chạy mỗi 30000 ms (`BOOKING_EXPIRY_ENABLED`, `BOOKING_EXPIRY_POLL_MILLISECONDS`). POST cũng giải phóng hold hết hạn ngay trong transaction. Khi GET trả `holdExpired=true`, hold đã mất hiệu lực dù status còn chờ scheduler cập nhật. Không hủy đơn PAID trong P4.

## P5 React Customer

Chạy backend ở terminal riêng theo hướng dẫn trên, rồi:

```powershell
cd frontend
npm ci
npm run dev
```

Mở [http://127.0.0.1:5173](http://127.0.0.1:5173). Cần Node 24+. Vite chuyển /api đến backend localhost:8080; URL khác cấu hình trong frontend/.env.local theo [frontend README](frontend/README.md).

Luồng: tìm chuyến → chọn ghế → đăng nhập → tạo đơn giữ chỗ → xem lịch sử/chi tiết → hủy hoặc hết hạn. Token lưu trong sessionStorage theo tab; reload xác minh /me, 401/hết hạn yêu cầu đăng nhập lại. Không tự gửi lại POST khi gặp lỗi.

Backend CORS chỉ mở cho origin cụ thể trong APP_CORS_ALLOWED_ORIGINS, mặc định localhost và 127.0.0.1 cổng 5173; không dùng wildcard/cookie credentials. Không đưa secret vào biến VITE_*. Preview/production cần reverse proxy hoặc API URL và CORS phù hợp.

```powershell
# lint + unit tests + production build + Java tests + HTTP + browser E2E trên cluster riêng
.\scripts\Test-P5.ps1 -JavaHome 'C:\Program Files\Java\jdk-21.0.12' -Offline
```

Edge đã cài được dùng cho browser tests. Ảnh desktop/mobile và bằng chứng ở .local/p5-*/. Không chạy E2E trên database có dữ liệu người dùng thật.

## Database và migration

- Flyway V1 tạo `users`, `routes`, `vehicles`, `seats`, `trips`, `bookings`, `booking_seats`, `payments`, `tickets`.
- V2 chuẩn hóa email và thêm uniqueness/CHECK cho email chuẩn hóa. V1 giữ nguyên. Khi khởi động phiên bản P2, Flyway tự nâng database V1 lên V2.
- V3 thêm index tìm tuyến, lịch/chuyến và booking theo chuyến. Khởi động phiên bản P3 sẽ tự nâng schema đến V3; V1/V2 giữ nguyên.
- V4 thêm indexes lịch sử booking theo user và cho admin; V1/V2/V3 giữ nguyên. Backend hiện tại tự migrate đến V4.
- PostgreSQL còn có bảng hạ tầng `flyway_schema_history`.
- Nguồn schema có hiệu lực: `backend/src/main/resources/db/migration/`.
- `database/01_schema.sql` là bản tham chiếu từ tài liệu; không chạy thủ công trước Flyway.
- Hibernate dùng `ddl-auto=validate`, không tạo/cập nhật schema.
- Không sửa migration đã chia sẻ/áp dụng; thay đổi schema sau P4 dùng V5 trở lên.
- Không dùng `baseline-on-migrate=true` để bỏ qua database có schema chưa được kiểm chứng.
- `TIMESTAMPTZ` biểu diễn thời điểm tuyệt đối; connection/JPA dùng UTC. UI tương lai dùng `Asia/Ho_Chi_Minh`.
- Hibernate validate 9 entities: User, Route, Vehicle, Seat, Trip, Booking, BookingSeat, Payment và Ticket.

Nếu database V1 đã có email trùng sau chuẩn hóa, V2 sẽ thất bại nguyên tử và giữ dữ liệu cũ. Xác minh tài khoản trước khi xử lý thủ công; không xóa/merge tự động. Truy vấn kiểm tra:

```sql
SELECT lower(btrim(email)) AS normalized_email, count(*)
FROM users GROUP BY lower(btrim(email)) HAVING count(*) > 1;
```

## Tài liệu và bước tiếp theo

### VS Code báo thiếu thư viện mail khi Run Java

Nếu log có `NoClassDefFoundError: org/springframework/mail/MailException` nhưng Maven build thành công, VS Code đang dùng classpath cũ sau khi thêm `spring-boot-starter-mail`. Mở Command Palette bằng `Ctrl+Shift+P`, chạy **Java: Reload Projects** và chờ **Java: Ready**. Không chạy lại file `cp_*.argfile` cũ. Đặt `"java.configuration.updateBuildConfiguration": "automatic"` trong `.vscode/settings.json` để tự cập nhật khi `pom.xml` đổi; đây là cấu hình [Java extension chính thức](https://github.com/redhat-developer/vscode-java#supported-vs-code-settings).

Nếu backend đang chạy ở cổng 8080, chỉ giữ một phiên backend; không mở thêm một phiên Run cùng cổng. Có thể chạy qua Maven Wrapper tại `backend/` như hướng dẫn phía trên khi cần bỏ qua classpath của IDE.

Đăng ký mới yêu cầu mở link xác nhận email trước khi đăng nhập. Cần cấu hình SMTP trong `.env`; xem [luồng xác nhận email và cách chạy](docs/email-verification.md). Flyway V5 tự nâng schema, giữ quyền truy cập tài khoản cũ. Script kiểm chứng dùng Node cho SMTP inbox tạm và không gửi email ra ngoài.

- [Kiến trúc và quyết định backend](docs/architecture.md)
- [ERD](docs/02_ERD.md)
- [Contract API hiện tại](docs/api-contract.md)
- [Báo cáo kiểm chứng P1](docs/P1-report.md)
- [Báo cáo kiểm chứng P2](docs/P2-report.md)
- [Contract Catalog/Trip API](docs/catalog-api.md)
- [Luồng chọn điểm đi và điểm đến](docs/location-picker.md)
- [Báo cáo kiểm chứng P3](docs/P3-report.md)
- [Contract Booking API](docs/booking-api.md)
- [Báo cáo kiểm chứng P4](docs/P4-report.md)
- [Frontend README](frontend/README.md)
- [Deploy backend lên Render với Neon PostgreSQL](docs/render-neon.md) — profile prod, JDBC SSL, biến môi trường và quy trình duyệt migration trước khởi tạo.
- [Audit an toàn V1–V10 trước khởi tạo Neon](docs/neon-migration-safety.md) — kiểm thử cluster tạm độc lập, giữ ID/lịch sử và rollback DDL.
- [Hướng dẫn đặt vé, trình soạn thảo Admin và thay logo](docs/site-content.md) — khách truy cập `/booking-guide`, Admin quản lý tại `/admin/site`; Flyway V10 lưu cấu hình trong PostgreSQL.
- [Báo cáo kiểm chứng P5](docs/P5-report.md)
- [API VNPAY Sandbox và vé](docs/payment-api.md)
- [Báo cáo kiểm chứng P6](docs/P6-report.md)

P6 có VNPAY Sandbox, IPN và vé điện tử. Xem [cấu hình và API P6](docs/payment-api.md). P7 thêm [giao diện Admin và thống kê](docs/admin-api.md), truy cập `/admin` bằng tài khoản ADMIN. Nghiệm thu toàn hệ thống: `.\scripts\Test-P7.ps1 -JavaHome 'C:\Program Files\Java\jdk-21.0.12' -Offline`. Demo qua VNPAY thật cần merchant Sandbox và IPN HTTPS công khai; kiểm thử cục bộ không thay thế bước đó.
