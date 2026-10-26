# Deploy backend Spring Boot lên Render với Neon PostgreSQL

Audit migration bổ sung đã được kiểm thử riêng: xem [neon-migration-safety.md](neon-migration-safety.md). Báo cáo đó cập nhật kết luận có thể bật Flyway cho Neon trống; profile vẫn mặc định false và không tự chạy trên Neon.

## Kết quả rà soát và phạm vi

Chuẩn bị cho project Neon `train-booking-db`, branch `production`, database `neondb`, role `neondb_owner`, AWS Singapore. Không sử dụng mật khẩu trong ảnh, không kết nối Neon, không chạy SQL thay đổi dữ liệu hay lệnh DROP trong lượt chuẩn bị này. Mật khẩu từng xuất hiện trong ảnh cần được reset trên Neon; nhập mật khẩu mới trực tiếp vào Render.

Backend giữ Java 21/Spring Boot 3.5.16, Spring Data JPA và PostgreSQL. JAR đóng gói PostgreSQL JDBC **42.7.11**, Flyway Core và Flyway PostgreSQL **11.20.3**. Không cần thêm driver hoặc đổi ORM. Không đổi nghiệp vụ đăng ký, giữ chỗ, thanh toán, vé hay quyền Admin.

Đã bổ sung `application-prod.yml`, `backend/Dockerfile`, `.dockerignore` và kiểm thử cấu hình. Cấu hình chung ưu tiên `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, vẫn hỗ trợ `DB_URL/USERNAME/PASSWORD` ở local. Profile prod bắt buộc dùng ba biến Spring chuẩn, không tự lấy thông tin DB local khi thiếu chúng.

## 1. Chuẩn bị Neon và lấy JDBC URL

1. Reset mật khẩu role đã xuất hiện trong ảnh. Không gửi mật khẩu vào chat, issue hoặc PR.
2. Mở Connect, chọn đúng **production / neondb / neondb_owner**.
3. **Tắt Connection pooling** và chọn Java. Dùng hostname trực tiếp không có `-pooler`. Với một backend đồ án, một Hikari pool nhỏ và cùng một kết nối direct cho ứng dụng/Flyway là cấu hình đơn giản. Neon yêu cầu dùng direct connection cho Flyway; không lấy hostname pooled rồi tự cắt chuỗi, hãy sao chép hostname direct từ giao diện.
4. Chuyển thành URL sau, chỉ thay `DIRECT_NEON_HOST`, không chèn username/password vào URL:

```text
jdbc:postgresql://DIRECT_NEON_HOST:5432/neondb?sslmode=require&channelBinding=require
```

`postgresql://user:password@host/...` trong ảnh là URI PostgreSQL, không phải giá trị JDBC dùng nguyên văn trong Spring. Tách role/password sang hai biến riêng. JDBC dùng `channelBinding` (camelCase), khác `channel_binding` trong chuỗi libpq/psql. Nhập dấu `&` thật, không dùng `&amp;`; không bọc giá trị bằng dấu nháy trong Render.

Profile prod cũng đặt thuộc tính pgJDBC `sslmode=require`, `channelBinding=require`, connect timeout 15 giây và socket timeout 60 giây. Không đặt `sslmode=disable/prefer` trong URL hoặc biến ghi đè. `require` bắt buộc mã hóa; bản thân nó không kiểm tra chuỗi CA/hostname như `verify-full`. Channel binding được yêu cầu theo hướng dẫn Neon. Nếu dùng `verify-full`, phải chuẩn bị CA/trust configuration phù hợp với pgJDBC; không sửa bằng cách tắt SSL khi gặp lỗi chứng chỉ.

Hikari tối đa 5 kết nối, minimum idle 0, chờ kết nối tối đa 30 giây, timezone UTC. Dữ liệu ngày tìm chuyến tiếp tục được xử lý theo Việt Nam như trước. Chưa cấu hình pooler hoặc datasource Flyway riêng để tránh thêm biến và thay đổi hành vi phiên SQL.

## 2. Rà soát database trước khi khởi tạo

Trong Neon SQL Editor, chọn đúng branch/database và chỉ chạy các câu đọc sau:

```sql
SELECT current_database(), current_user;
SHOW server_version;
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
ORDER BY table_name;
SELECT to_regclass('public.flyway_schema_history');
```

Đối chiếu database là `neondb`, role `neondb_owner`, PostgreSQL 16+. Không có kết quả đọc trực tiếp từ Neon trong báo cáo này, vì chưa sử dụng kết nối hoặc mật khẩu Neon.

- Database trống: sau khi rà soát migration, có thể cho Flyway tạo schema.
- Có bảng ứng dụng nhưng không có `flyway_schema_history`: dừng để đối chiếu nguồn schema. Không bật baseline tự động, không chạy `database/01_schema.sql` trước Flyway.
- Có lịch sử Flyway: đọc version, checksum và trạng thái; cần backup/nhánh thử nghiệm trước khi áp dụng version mới. Không dùng `repair` để che lỗi checksum hoặc bỏ qua migration lỗi.

Chỉ khi bảng lịch sử tồn tại, đọc:

```sql
SELECT installed_rank, version, description, checksum, success
FROM flyway_schema_history ORDER BY installed_rank;
```

## 3. Audit V1–V10

| Version | Tác động hiện có |
| --- | --- |
| V1 | Tạo 9 bảng nghiệp vụ, khóa ngoại, index tìm chuyến, unique partial `uq_active_trip_seat` chống đặt trùng chỗ đang hoạt động. |
| V2 | Chuẩn hóa email bằng lower/trim và thêm unique index/CHECK. Dữ liệu email trùng sau chuẩn hóa làm migration thất bại, không gộp/xóa tài khoản. |
| V3 | Index cho tuyến, tìm chuyến, lịch phương tiện và booking. |
| V4 | Index phân trang lịch sử đơn. |
| V5 | Thêm xác nhận email và token có thời hạn. Tài khoản cũ được giữ quyền đăng nhập theo thiết kế đã có. |
| V6 | Backfill hạn payment từ booking, thêm thông tin IPN/đối soát và unique một payment PENDING/booking. Dữ liệu cũ không hợp lệ có thể làm migration dừng. |
| V7 | Thêm loại sơ đồ và tọa độ toa/khoang/tầng/chỗ, CHECK và unique vị trí. |
| V8 | Kiểm tra dữ liệu xe 16 chỗ, thêm chỗ 17–34; thay hai CHECK và đổi layout, giữ ID cũ. |
| V9 | Kiểm tra 34 chỗ, chuyển thành giường hai tầng ×17; thay hai CHECK, giữ ID và tham chiếu đơn. |
| V10 | Thêm bảng cấu hình `site_settings`, seed một nội dung hướng dẫn; không tạo tài khoản hay chuyến mẫu. |

Có **4 câu ALTER TABLE … DROP CONSTRAINT** ở V8/V9, sau đó tạo CHECK thay thế trong migration. Không có DROP TABLE/DATABASE/SCHEMA, TRUNCATE hoặc DELETE FROM trong V1–V10. Đây là việc thay ràng buộc, không xóa bảng, nhưng vẫn thuộc thay đổi DDL cần rà soát. Các file migration được giữ nguyên byte/checksum; không tạo bản migration "đã bỏ DROP" để thay vào lịch sử.

Để tôn trọng yêu cầu chưa chạy DROP, profile prod mặc định **`SPRING_FLYWAY_ENABLED=false`**. Đây là khóa chặn trước khởi tạo; với database trống, Hibernate `validate` sẽ báo thiếu bảng. Không sửa thành `create`, `update` hoặc `create-drop` để vượt qua lỗi này.

Sau khi bạn đã chấp nhận phạm vi V1–V10, bao gồm việc thay CHECK ở V8/V9, chủ động đặt `SPRING_FLYWAY_ENABLED=true` tại Render rồi deploy. Nếu vẫn cấm cả DROP CONSTRAINT, giữ false và chưa khởi tạo bằng các migration này. Không có lệnh thay đổi Neon nào được thực hiện tự động bởi tác vụ chuẩn bị.

## 4. Tạo Web Service trên Render

Trong Dashboard: **New → Web Service**, chọn repository `Jonny-Pak/train_booking_system` và branch muốn deploy.

| Thiết lập | Giá trị |
| --- | --- |
| Language/runtime | Docker |
| Root Directory | `backend` |
| Dockerfile Path | `./Dockerfile` (tương đối với Root Directory) |
| Docker build context | `.` trong `backend`; không đặt toàn bộ monorepo làm context |
| Region | Singapore để gần Neon Singapore, nếu region này khả dụng cho loại service của bạn |
| Docker Command | Để trống, dùng ENTRYPOINT |
| Health Check Path | `/actuator/health` |

Dockerfile build bằng Maven/JDK 21 rồi chạy bằng Temurin JRE 21 với user không phải root. Chỉ copy `pom.xml` và `src/main`, không copy `.env`, bản dump hay frontend. Không có ARG/ENV mật khẩu và không mở kết nối DB trong build. Test integration không chạy trong Docker build vì cần database thử nghiệm độc lập.

Render cấp `PORT` (mặc định 10000); prod bind **0.0.0.0:${PORT:10000}**. `EXPOSE 10000` chỉ là metadata, không thay thế cấu hình port. Không cần Build/Start Command dành cho Node, không dùng `npm run dev` để chạy backend.

## 5. Nhập Environment trên Render

Mở service → **Environment → Add Environment Variable**. Tham khảo `docs/render-env.example`; chỉ nhập giá trị thật trong Dashboard, không commit file cấu hình thật.

| Biến | Giá trị |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SPRING_DATASOURCE_URL` | JDBC direct URL ở bước 1, SSL/channel binding, không chứa credentials |
| `SPRING_DATASOURCE_USERNAME` | `neondb_owner` |
| `SPRING_DATASOURCE_PASSWORD` | Mật khẩu mới của Neon, nhập riêng tại Render |
| `JWT_SECRET` | Base64 của ít nhất 32 byte ngẫu nhiên; tạo khóa riêng cho cloud |
| `SPRING_FLYWAY_ENABLED` | `false` khi chưa duyệt migration; chủ động đổi `true` sau bước 3 |
| `APP_FRONTEND_URL` | Origin HTTPS chính xác của frontend Vercel, không có dấu `/` cuối |
| `APP_CORS_ALLOWED_ORIGINS` | Cùng origin frontend; nhiều origin phân cách bằng dấu phẩy, không dùng `*` |

Sinh JWT secret trong PowerShell 7 tại máy, rồi dán trực tiếp vào Render:

```powershell
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

Không đặt tên secret với tiền tố `VITE_`. Frontend chỉ cần `VITE_API_BASE_URL=https://YOUR_BACKEND.onrender.com/api/v1` rồi rebuild/redeploy Vercel. Frontend/backend phải cùng cấu hình origin; không dùng proxy localhost trong bản Vite production.

Mail dùng các biến `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_SMTP_AUTH=true`, `MAIL_STARTTLS=true` như hiện tại. Với Gmail: host `smtp.gmail.com`, port `587`, mật khẩu ứng dụng nhập riêng tại Render. **Render Free chặn cổng 25/465/587**: Gmail SMTP không hoạt động ở gói đó. Muốn giữ nguyên triển khai SMTP hiện tại, dùng gói/network cho phép port 587; phương án email HTTP API là thay đổi riêng cần triển khai sau, không được tự động thay vào task này. Không bỏ xác nhận email để làm đăng ký hoạt động.

Health prod không probe SMTP để tránh tình trạng dịch vụ bị đánh DOWN chỉ vì probe email. DB vẫn được health check. Health UP không đồng nghĩa gửi email đã thành công; phải thử luồng xác nhận email trên môi trường deploy trước nghiệm thu.

VNPAY khi chưa kiểm thử cloud để `VNPAY_ENABLED=false`. Sau đó tự nhập `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, dùng Return URL `https://YOUR_FRONTEND/payment/return`, IPN `https://YOUR_BACKEND.onrender.com/api/v1/payments/vnpay/ipn` và đăng ký IPN với VNPAY. `VNPAY_IPN_URL` là thông tin triển khai, không tự cập nhật merchant portal. Không còn cần Cloudflare Tunnel cho backend đã có HTTPS trên Render. Cổng thanh toán vẫn Sandbox.

## 6. Khởi tạo và nghiệm thu an toàn

1. Reset mật khẩu đã lộ; chọn đúng branch production. Nếu database có dữ liệu, tạo backup/điểm khôi phục phù hợp và kiểm tra trên branch thử nghiệm trước.
2. Chạy các truy vấn chỉ đọc ở bước 2. Đối chiếu toàn bộ migration ở bước 3.
3. Cấu hình service và biến môi trường, đặc biệt SSL, JWT, origin và mail. Chưa dùng dữ liệu local để ghi đè Neon.
4. Chỉ bật Flyway sau khi đã chấp nhận DDL hiện có. Deploy một instance ban đầu. Flyway áp dụng V1–V10 trước Hibernate validate; gặp lỗi thì dừng và xem nguyên nhân, không xóa bảng để thử lại.
5. Log cần cho thấy validate/migrate thành công, Hibernate validate không báo thiếu cột, server bind port Render cấp. `/actuator/health` trả 200 và `UP`; endpoint khác như `/` có thể trả 401/403 vì không có trang chủ backend.
6. Đọc lại `flyway_schema_history`: 10 version thành công. Có 10 bảng ứng dụng (9 nghiệp vụ + `site_settings`) và bảng lịch sử Flyway. Lần khởi động lại không được tạo lại bảng hay đổi checksum.
7. Database mới không tự có tài khoản Admin, đơn, vé hoặc chuyến. Tạo tài khoản qua luồng đăng ký/email trước; cấp quyền Admin bằng quy trình quản trị có kiểm soát, không hardcode mật khẩu vào seed.
8. Nếu cần dữ liệu demo, rà soát và chạy riêng `database/04_sample_trips_20261027_20261110.sql` trên môi trường demo. Không tự import `01_schema.sql`, dump local hoặc toàn bộ lịch sử người dùng lên production. Mẫu 2026 chỉ là dữ liệu trình diễn có ngày cố định.
9. Kiểm tra frontend gọi API, CORS preflight, đăng ký/xác nhận email/đăng nhập, tìm chuyến/chọn chỗ và thanh toán Sandbox. Đối chiếu IPN/ticket; Return không tự xác nhận thanh toán. Không coi health UP là nghiệm thu toàn hệ thống.

## 7. Kiểm thử đã thực hiện và giới hạn

Ngày 26/10/2026: `mvnw.cmd -o -B "-Dtest=ProductionProfileTest,VnpayGatewayTest" verify` đã qua **11 tests**, tạo Spring Boot JAR thành công. Test đọc/bind YAML, kiểm tra ưu tiên env chuẩn và fallback local, fail khi prod thiếu env DB, bind PORT, pgJDBC parse SSL/channel binding/timeouts và cờ an toàn Flyway/JPA. Đây là kiểm thử thuần Java, không mở socket DB, không chạy SQL/DROP. Cũng đã kiểm tra JAR chứa profile prod và JDBC/Flyway PostgreSQL.

Không chạy bộ integration cũ trong lượt này vì chúng tạo/xóa schema thử nghiệm và thực thi migration có DROP CONSTRAINT. Chưa kết nối hoặc thay đổi Neon, chưa deploy Render, chưa xác nhận bắt tay TLS thực tế. Docker CLI có trên máy nhưng Docker Engine chưa chạy, nên chưa build/run image; không tự khởi động Docker Desktop.

Khi Docker Engine sẵn sàng, có thể build image từ root mà không truyền secret:

```powershell
docker build -t vexesieure-backend .\backend
```

Chỉ chạy image với các biến cloud đã được bạn cấu hình; việc chạy cùng `SPRING_FLYWAY_ENABLED=true` có thể thực thi migration. Không dùng database Neon production để chạy các script test local.

## Nguồn chính thức

- [Neon Java/JDBC](https://neon.com/docs/guides/java)
- [Neon Flyway: direct connection](https://neon.com/docs/guides/flyway)
- [pgJDBC SSL](https://jdbc.postgresql.org/documentation/ssl/) và [connection properties](https://jdbc.postgresql.org/documentation/use/)
- [Render Docker](https://render.com/docs/docker), [port/environment](https://render.com/docs/environment-variables), [health checks](https://render.com/docs/health-checks)
- [Giới hạn Render Free, gồm SMTP](https://render.com/docs/free)
