# Kiến trúc và quyết định backend

## Phạm vi

Một Spring Boot application và một PostgreSQL theo Modular Monolith. P1 dựng hạ tầng; P2 triển khai auth/user; P3 triển khai route/vehicle/trip; P4 triển khai booking và giữ ghế; P5 có React Customer. Payment và phát hành vé thuộc P6.

Stack khóa: Java 21, Spring Boot 3.5.16, Maven Wrapper 3.9.12, Spring Data JPA/Hibernate, Flyway Core và module PostgreSQL 11.20.3. Dependency versions lấy từ Spring Boot BOM, ngoại trừ Flyway được khóa đồng bộ core/module ở 11.20.3 để hỗ trợ PostgreSQL 18 đang có trên máy. Bản mặc định của Boot cảnh báo chỉ được kiểm chứng đến PostgreSQL 17.

Các module dự kiến: `auth`, `user`, `route`, `vehicle` (gồm ghế), `trip`, `booking`, `payment`, `ticket`, `admin`. Chỉ tạo module khi có mã thực tế, tránh các lớp/trang giữ chỗ không có hành vi.

## Trách nhiệm

Controller nhận DTO và kiểm tra input; service thực thi nghiệp vụ/transaction; repository truy cập dữ liệu. Không trả entity JPA trực tiếp qua REST. Backend quyết định user, giá, thanh toán và quyền sở hữu dữ liệu.

`common` có lỗi API, exception nghiệp vụ và exception advice. `config` quản lý SecurityFilterChain stateless và CORS; `auth` phát hành/xác minh JWT; `user` quản lý JPA entity và DTO tài khoản.

## Xác thực P2

Spring OAuth2 Resource Server/Nimbus xác minh Bearer JWT HS256, khóa Base64 từ môi trường tối thiểu 256 bit, issuer/audience cố định, bắt buộc iat/exp, kiểm tra thời gian không có clock skew. Backend tự cấp token; không cần dịch vụ OAuth/Keycloak riêng. Token mặc định 15 phút, không refresh token.

Mật khẩu BCrypt cost 12; tối thiểu 8 Unicode code points, tối đa 72 byte UTF-8. Không trim mật khẩu. Unknown account và sai mật khẩu cùng trả lỗi; dummy BCrypt hash giảm khác biệt timing. Password và token không được đưa vào toString hoặc response profile.

Đăng ký chỉ CUSTOMER; field lạ/role gửi lên bị từ chối. Email strip/lowercase với Locale.ROOT và V2 bảo vệ ở DB. Kiểm tra tồn tại trước và bắt unique conflict khi saveAndFlush để xử lý hai đăng ký đồng thời.

Sau V5, tài khoản mới cần xác nhận email trước khi đăng nhập. Token ngẫu nhiên một lần có hạn dùng, DB lưu SHA-256, khóa ghi user bảo vệ xác nhận/resend. JavaMailSender gửi SMTP đồng bộ với timeout; lỗi gửi rollback transaction. Tài khoản cũ giữ quyền truy cập. Chi tiết và giới hạn tại [email-verification.md](email-verification.md).

JWT subject là ID user, không có email/password. Role claim hỗ trợ client nhưng quyền backend đọc từ database mỗi request để không giữ quyền ADMIN đã bị thu hồi. User bị xóa không tiếp tục dùng token. Không có tài khoản Admin mặc định hoặc API tự nâng quyền.

Chỉ nhận header Bearer, không cookie/query token; tắt session/Basic/form login và CSRF theo contract này. Nếu bổ sung cookie authentication sau này phải thiết kế lại CSRF. Tất cả response auth thành công có Cache-Control no-store. Login/registration DTO không chứa role; entity không trả trực tiếp qua REST.

## Database

Giữ nguyên V1 chín bảng. V2 bổ sung chuẩn hóa email, unique index và CHECK; migration không tự merge/xóa khi dữ liệu cũ xung đột. PostgreSQL thật chịu trách nhiệm FK kép, CHECK và partial unique index. JPA không thay thế database constraints.

V3 chỉ thêm indexes cho truy vấn catalog và booking theo trip. P3 map Route/Vehicle/Seat/Trip bằng JPA; liên kết many-to-one lazy. Trip list dùng JPQL join fetch, detail dùng EntityGraph để tải route/vehicle trong transaction và trả DTO, không serialize entity/proxy.

## Catalog P3

ADMIN CRUD tuyến/phương tiện/ghế/chuyến. Search dùng JPQL trong TripQueryRepository; từng điều kiện được thêm bằng if và giá trị được truyền qua tham số. Ngày tìm là nửa khoảng ngày Việt Nam, đổi sang UTC; departure phải sau Clock hiện tại; sort cố định departureAt/id; phân trang size tối đa 100.

Code ưu tiên cách viết dễ đọc cho đồ án: mỗi câu lệnh một dòng, if/else rõ ràng, vòng lặp for để chuyển danh sách sang DTO, tên biến thể hiện ý nghĩa. Không dùng Specification/Criteria hoặc chuỗi stream cho nghiệp vụ catalog. Các transaction và khóa PostgreSQL vẫn cần thiết để kiểm tra lịch chuyến an toàn khi có nhiều request đồng thời.

Trạng thái ghế đọc booking_seats và bookings: active + PAID là BOOKED; active + PENDING còn hạn là HELD; còn lại AVAILABLE. GET không thay đổi dữ liệu và không bảo đảm ghế sẽ còn trống ở lần đặt sau. P4 kiểm tra/release hết hạn trong transaction trước khi giữ ghế.

Các ghi catalog ít và chỉ dành cho admin nên dùng một `pg_advisory_xact_lock(713003)` cho toàn bộ catalog services. Điều này đơn giản hóa thứ tự ghi và ngăn hai request đồng thời vượt qua kiểm tra lịch trống, kể cả nhiều instance. Không giữ khóa qua HTTP bên ngoài. Khóa chỉ tuần tự hóa ghi catalog, không khóa toàn bộ bảng hoặc chặn tìm kiếm.

Trip update/delete còn khóa hàng Trip; thay đổi route/vehicle/seat layout khóa các Trip liên quan theo ID trước khi kiểm tra bookings. P4 dùng cùng khóa Trip trước khi tạo booking để tránh chạy đua với đổi/xóa metadata. Các kiểm tra history/row lock catalog vẫn dùng JDBC; Booking/BookingSeat được map bằng JPA ở P4.

Khoảng lịch `[departureAt, arrivalAt)` không được chồng cho cùng phương tiện; CANCELLED không chiếm lịch. Quy tắc này được bảo vệ bởi service và transaction lock, chưa có exclusion constraint cho SQL ghi trực tiếp. Không cần btree_gist/extension mới. SQL seed chỉ chạy rõ ràng trên database demo.

Giữ lịch sử: route có trips chỉ được đổi active, không đổi endpoints; vehicle có trips không đổi type; không xóa route/vehicle có references. Trip có booking history chặn toàn bộ sửa/xóa; vehicle có booking history chặn mọi thêm/sửa/xóa ghế. Vehicle code/display name/active vẫn chỉnh được. P4 lưu snapshot giá ghế; tên/mã phương tiện hiển thị ở catalog là giá trị hiện tại.

Trip mới phải SCHEDULED, tương lai, có route/vehicle active và phương tiện có ghế. SCHEDULED có thể đổi lịch hoặc chuyển CANCELLED/COMPLETED nếu chưa có booking; COMPLETED yêu cầu giờ đến cũ và mới đã qua. CANCELLED/COMPLETED không sửa tiếp; có thể xóa nếu không có booking. Xóa không cascade vào dữ liệu lịch sử.

`ddl-auto=validate`; Flyway `clean` bị vô hiệu hóa; không tự baseline schema có sẵn. Connection và Hibernate dùng UTC. Tiền dùng `NUMERIC(12,2)` và sẽ map sang `BigDecimal` khi triển khai entity.

Tính duy nhất active `(trip_id, seat_id)` chỉ bảo vệ hàng có `is_active=true`; không tự giải phóng hold theo thời gian. P4 service và integration tests bảo đảm đơn có ghế, tổng tiền đúng, hủy/hết hạn nguyên tử. Liên kết trạng thái payment/booking và phát hành vé chỉ sau PAID thuộc P6.

## Booking P4

BookingService giữ transaction ngắn: khóa Trip → đọc và kiểm tra chuyến/ghế → khóa các Booking PENDING hết hạn theo ID → chuyển EXPIRED và release ghế → kiểm tra active → insert Booking và snapshot BookingSeat. Flush release trước insert để không vướng partial unique index cũ. Nếu bước sau thất bại, toàn bộ transaction (kể cả cleanup expiry) rollback; request/scheduler sau sẽ thử cleanup lại.

Khóa Trip được lấy bằng SELECT FOR UPDATE trước khi đọc entity; thời điểm now được lấy sau khi khóa để không dùng thời gian đã cũ khi chờ. Booking P4 dùng ID scalar cho user/trip/vehicle; DB vẫn áp dụng đầy đủ các FK kép V1. BookingSeat liên kết Seat lazy và tải ghế bằng EntityGraph khi tạo response. Giới hạn 10 ghế mỗi đơn; giá snapshot BigDecimal từ database; hold mặc định 600 giây và chặn tại departureAt.

Cancel đọc trip_id qua JDBC, khóa Trip rồi khóa Booking, kiểm tra owner/ADMIN, xử lý expiry trước khi chuyển PENDING còn hạn thành CANCELLED. Cancel lặp trả trạng thái hiện tại; PAID trả 409. Không xóa booking history và không có endpoint tự đặt PAID.

BookingExpiryScheduler chạy mỗi 30 giây, lấy tối đa 100 trip ID có hold hết hạn; gọi BookingExpiryService qua Spring bean để mỗi trip có transaction riêng. Các instance cùng khóa Trip nên cleanup lặp an toàn. Test profile tắt lịch chạy nền để kiểm thử thời gian chủ động; HTTP smoke vẫn chạy scheduler thật. App restart tiếp tục dọn đơn quá hạn từ dữ liệu PostgreSQL, không dựa vào timer giữ trong RAM.

GET history/detail không ghi database. Field holdExpired phản ánh thời gian; status/active có thể chờ lượt scheduler để lưu EXPIRED/inactive. Tạo đơn và cancel luôn kiểm tra lại deadline trong transaction. Admin filter áp dụng status đã lưu. P6 phải kiểm tra trạng thái và deadline dưới cùng khóa trước khi chấp nhận payment, không tin riêng response GET.

V4 chỉ thêm indexes history theo user và cho admin; không sửa V1/V2/V3, không thêm bảng. API history dùng page tối đa 100 và một truy vấn ghế cho mỗi booking để giữ code đơn giản; khi cần lưu lượng lớn có thể tải ghế theo batch.

## React Customer P5

React + Vite + React Router trong frontend/, JavaScript và CSS thuần. Mỗi trang có component riêng; AuthContext dùng cho session, state nghiệp vụ giữ ở trang tương ứng. Không dùng Redux hoặc generic workflow. API client dùng fetch, Bearer header và credentials omit; không tự retry POST.

Token/expiry/profile trong sessionStorage theo tab, có fallback bộ nhớ khi storage bị chặn. Reload xác minh /auth/me; 401 hoặc deadline JWT xóa phiên client. Guard chỉ hỗ trợ điều hướng, backend vẫn quyết định quyền. Logout không thu hồi token server. Không ghi token/password vào URL hoặc log.

Search dùng query string để hỗ trợ reload và back/forward. Loading, error và empty được hiển thị rõ; không thay dữ liệu thật bằng dữ liệu demo giả khi API lỗi. Chọn ghế trước login được chuyển qua router state và kiểm tra lại khi quay về; conflict 409 xóa lựa chọn và tải lại sơ đồ.

Sơ đồ ghế chỉ là minh họa vì database không có tọa độ ghế. Booking detail dùng tổng/giá từ server, countdown chỉ hiển thị; PENDING quá hạn được hiển thị EXPIRED dù scheduler chưa ghi status. Poll detail mỗi 15 giây, tạm dừng trong lúc hủy để response cũ không ghi đè kết quả mới. Chuyến không còn public trả 404 không làm mất khả năng xem đơn và giá snapshot.

Vite proxy /api đến backend trong dev; production cần reverse proxy hoặc VITE_API_BASE_URL phù hợp và SPA fallback. Backend CorsConfig chỉ cho origin cụ thể từ APP_CORS_ALLOWED_ORIGINS, mặc định localhost/127.0.0.1:5173; không wildcard, không cookie credentials. CORS không thay thế authentication.

Font tiếng Việt đóng gói trong bundle, không phụ thuộc CDN; mobile có layout riêng, focus rõ, semantic labels, live status và reduced motion. Vitest kiểm tra API/session/deadline, Playwright chạy Edge với backend/PostgreSQL thật trên cluster tạm.

## Quyết định cho các pha sau

Các mục sau là thiết kế, chưa được triển khai:

- P6 tiếp tục thứ tự khóa đã dùng ở P4: Trip → Booking → Payment.
- P6 IPN xác minh chữ ký, merchant, txn_ref, amount và trạng thái; khóa và xử lý idempotent.
- Payment, booking và tickets được cập nhật atomically; Return không phát hành vé.
- IPN thành công đến sau expiry/cancel không lấy lại ghế; cần đối soát. Xem xét migration bổ sung dấu hiệu đối soát ở P6.
- Không sửa phương tiện hoặc ghế của chuyến đã có booking; không xóa lịch sử đã tham chiếu.

## Nguồn và tương thích

- [Spring Boot 3.5 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [Spring Security 6.5 JWT resource server](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html)
- [PostgreSQL 16 partial indexes](https://www.postgresql.org/docs/16/indexes-partial.html)
- [Flyway 11.20.3 PostgreSQL support implementation](https://github.com/flyway/flyway/blob/flyway-11.20.3/flyway-database/flyway-database-postgresql/src/main/java/org/flywaydb/database/postgresql/PostgreSQLDatabase.java)
- Maven Wrapper scripts lấy từ tag chính thức `maven-wrapper-3.3.4`, loại `only-script`; Maven archive được đối chiếu SHA-512 chính thức trước khi khóa SHA-256 trong wrapper properties.
