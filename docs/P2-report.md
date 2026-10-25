# Báo cáo hoàn thành P2

Ngày kiểm chứng: 25/10/2026, giờ Việt Nam. Nhánh làm việc: `develop`. Phạm vi chỉ gồm đăng ký/đăng nhập, BCrypt, JWT, hồ sơ và phân quyền CUSTOMER/ADMIN.

## Kết quả

**P2 đạt nghiệm thu trên PostgreSQL 18.4 thật:** build sạch thành công; 48 tests pass; luồng auth qua HTTP thật pass; JWT còn hạn dùng được sau khi ứng dụng restart với cùng khóa.

| Hạng mục | Kết quả |
|---|---|
| Build | `mvnw.cmd -o -B clean verify`: BUILD SUCCESS |
| AuthIntegrationTest | 29 pass |
| JwtConfigTest | 3 pass |
| InfrastructureIntegrationTest | 12 pass |
| GlobalExceptionHandlerTest | 4 pass |
| Tổng | 48 chạy, 0 failures, 0 errors, 0 skipped |
| Migration | V1 giữ nguyên; V2 chuẩn hóa/bảo vệ email |
| Hibernate | Validate mapping User thành công |
| HTTP smoke | register 201, trùng email 409, login/me 200, thiếu token 401, CUSTOMER gọi admin 403 |
| ADMIN | `/me` phản ánh role ADMIN; test-only admin handler chỉ chấp nhận ADMIN |
| Restart | Hai lần health UP; hai migration không chạy lại; token tiếp tục dùng được |
| Dọn môi trường | PostgreSQL và application do script tạo đã dừng |

## Chức năng triển khai

- POST `/api/v1/auth/register`: validate, chuẩn hóa email, chỉ CUSTOMER, BCrypt cost 12; trùng email trả 409, kể cả đăng ký đồng thời.
- POST `/api/v1/auth/login`: xác minh BCrypt; email không có và sai mật khẩu cùng lỗi 401; dummy hash cho email không có.
- GET `/api/v1/auth/me`: lấy user từ JWT subject, không nhận userId từ client; không lộ hash/password.
- JWT HS256 dùng Spring Security Resource Server/Nimbus, kiểm tra chữ ký, issuer/audience, thời gian và account tồn tại. Secret Base64 tối thiểu 32 byte; token mặc định 900 giây.
- Stateless, Bearer header; không cookie/query token hoặc Basic/form login. Token/secret/password được redacted trong toString.
- Quyền ADMIN/CUSTOMER lấy từ database mỗi request; token cũ không giữ quyền đã bị thu hồi.
- Unknown fields bị từ chối, gồm `role` trong register. Không có tài khoản mặc định, API tự nâng quyền hoặc admin bootstrap chứa mật khẩu.
- V2 chuẩn hóa email; nếu dữ liệu V1 xung đột, migration rollback, không merge/xóa account.

## File tạo và sửa

File mới:

- `backend/src/main/java/com/example/ticketbooking/auth/`: `AuthController.java`, `AuthService.java`, `DatabaseJwtAuthenticationConverter.java`, `EmailNormalizer.java`, `JwtConfig.java`, `JwtSettings.java`, `LoginRequest.java`, `LoginResponse.java`, `PasswordValidator.java`, `RegisterRequest.java`, `TokenService.java`, `ValidPassword.java`.
- `backend/src/main/java/com/example/ticketbooking/user/`: `Role.java`, `User.java`, `UserRepository.java`, `UserResponse.java`.
- `backend/src/main/resources/db/migration/V2__normalize_user_email.sql`.
- `backend/src/test/java/com/example/ticketbooking/auth/AuthIntegrationTest.java`, `JwtConfigTest.java`.
- `scripts/Test-P2.ps1`, `docs/P2-report.md`.

File sửa:

- `backend/pom.xml`: thêm Spring OAuth2 Resource Server theo Spring Boot BOM; không thêm dịch vụ OAuth bên ngoài.
- `backend/src/main/java/com/example/ticketbooking/config/SecurityConfig.java`: JWT, public auth, me, admin rules và lỗi JSON.
- `backend/src/main/java/com/example/ticketbooking/common/GlobalExceptionHandler.java`: giữ lỗi method access denied ở 403.
- `backend/src/main/resources/application.yml`: JWT settings, từ chối JSON field lạ.
- `backend/src/test/resources/application-test.yml`: khóa riêng cho test, không đóng gói trong JAR runtime.
- `backend/src/test/java/com/example/ticketbooking/InfrastructureIntegrationTest.java`: nâng kỳ vọng V2 và kiểm tra upgrade/rollback dữ liệu cũ.
- `.env.example`: placeholder JWT, không chứa khóa thật.
- `scripts/Test-P1.ps1`: dùng chung lifecycle PostgreSQL, truyền key ngẫu nhiên, kiểm tra số migration hiện tại và tùy chọn auth smoke.
- `README.md`, `docs/api-contract.md`, `docs/architecture.md`: hướng dẫn và contract P2.

Local `.env` bị Git bỏ qua: đã bổ sung khóa JWT ngẫu nhiên, giữ nguyên username/password/URL PostgreSQL trước đó. Không đưa secrets vào mã nguồn. V1 và `database/01_schema.sql` không đổi.

## Lệnh và bằng chứng

```powershell
# Biên dịch ban đầu trong backend
.\mvnw.cmd -B -DskipTests package

# Nghiệm thu cuối tại thư mục gốc
.\scripts\Test-P2.ps1 -JavaHome 'C:\Program Files\Java\jdk-21.0.12' -Offline

# Kiểm tra diff
git diff --check
```

Script chạy full build/tests rồi khởi động JAR hai lần trên cluster riêng, không sửa database/service local của người dùng. Evidence cuối ở `.local/p2-20261025-025536-1926c49b/verification.json`, application logs và pgctl stop log; test reports ở `backend/target/surefire-reports/`.

Lần chạy đầy đủ trước đó có 42 tests pass nhưng script HTTP smoke bị lỗi do log tiến trình lọt vào giá trị token trả về từ function PowerShell. Đã chuyển log ra host stream, bổ sung 6 test và chạy lại toàn bộ: 48 tests cùng HTTP smoke/restart đều pass.

## Kiểm thử bảo mật và dữ liệu

Đã kiểm tra: trùng email khác case/khoảng trắng; role injection; validation email/tên/password; BCrypt hash thực; mật khẩu Unicode đúng biên 72 byte và vượt biên; sai password/unknown email; me qua hai request; cookie/query token bị từ chối; CUSTOMER 403/ADMIN được phép; thay đổi role ảnh hưởng token cũ; xóa account vô hiệu token; đăng ký đồng thời chỉ một account.

JWT bị từ chối với: hết hạn, bị sửa, sai key, issuer/audience khác, thiếu exp/iat, iat/nbf tương lai, unsigned, sai cấu trúc, subject sai hoặc account không tồn tại. Các lỗi không lộ nguyên nhân chữ ký hoặc stacktrace.

Migration được kiểm tra cả database trống và schema V1 có dữ liệu: email chuẩn hóa thành công mà hash giữ nguyên; khi hai email xung đột thì V2 thất bại nguyên tử và hai account cũ vẫn còn.

## Tồn tại và cách chạy local

- Mã P2 được bàn giao trên nhánh `develop`; danh sách thay đổi và bằng chứng kiểm thử nằm trong báo cáo này.
- Chưa có refresh token, logout thu hồi token, UI hoặc CRUD admin. Handler admin kiểm chứng chỉ có trong tests, không trong JAR.
- Admin được cấp bằng thao tác quản trị database với tài khoản đã đăng ký; README có SQL mẫu, không cấp sẵn quyền cho email cố định.
- Test framework vẫn có cảnh báo Mockito dynamic agent trên JDK 21; không ảnh hưởng tests/JAR runtime.
- Kiểm chứng trên PostgreSQL 18.4/Windows/JDK 21; chưa chạy ma trận riêng PostgreSQL 16/17 hoặc Linux.
- Thư mục socket Java riêng từ P1 tiếp tục được script áp dụng trên máy này.
- Database local thật chưa được nâng V2 trong lượt kiểm thử này. Khởi động lại backend theo README sẽ tự chạy Flyway V2 với `.env` hiện có. Nếu phát hiện email cũ xung đột, dừng để xác minh dữ liệu; không chỉnh V1 hoặc tự xóa account.

## WBS và bước tiếp theo

WBS chính: `1.2.2.2` (REST API/bảo mật), `1.3.1.2` (đăng ký/đăng nhập/phân quyền); kết quả kiểm thử hỗ trợ `1.6.1.2` nhưng chưa tuyên bố toàn bộ kiểm thử dự án hoàn tất.

P3 tiếp theo: Route/Vehicle/Seat/Trip entities, CRUD quản trị, tìm chuyến/phân trang, kiểm tra lịch phương tiện và dữ liệu demo tách biệt. Chưa triển khai P3 trong lượt này.
