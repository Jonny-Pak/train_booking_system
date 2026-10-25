# Báo cáo hoàn thành P3

Ngày kiểm chứng: 25/10/2026, giờ Việt Nam. Nhánh: `feature/catalog-trips`, tạo từ `develop` đã có P2. Phạm vi: Catalog/Trip API, dữ liệu demo và kiểm thử; chưa thực hiện P4.

## Kết quả

**P3 đạt nghiệm thu trên PostgreSQL 18.4 thật:** clean verify thành công; 69 tests pass; CRUD và public search/detail/seats qua HTTP thật pass; seed chạy lặp không nhân đôi dữ liệu; restart vẫn UP và token P2 còn hạn tiếp tục dùng được.

| Kiểm tra | Kết quả |
|---|---|
| `mvnw.cmd -o -B clean verify` | BUILD SUCCESS |
| CatalogIntegrationTest | 21 pass |
| AuthIntegrationTest | 29 pass |
| InfrastructureIntegrationTest | 12 pass |
| JwtConfigTest | 3 pass |
| GlobalExceptionHandlerTest | 4 pass |
| Tổng | 69 chạy, 0 failures, 0 errors, 0 skipped |
| Migration | V1/V2 giữ nguyên; V3 thêm indexes; Hibernate validate 5 entities |
| HTTP | CRUD catalog, public list/detail/seats và overlap 409 pass |
| Demo | Hai lần seed; vẫn đúng một chuyến bus tương ứng trong public search |
| Restart | Hai lần health UP; 3 migration không chạy lại; Bearer token còn dùng được |
| Dọn process | PostgreSQL và app kiểm chứng đã dừng |

## Chức năng

Theo yêu cầu code đơn giản: service dùng if/else và vòng lặp for, mỗi câu lệnh một dòng; truy vấn tìm chuyến chuyển từ Specification/Criteria sang JPQL có tham số trong TripQueryRepository. Controller tách rõ annotation và thân hàm. Giữ nguyên transaction, khóa PostgreSQL, API và các quy tắc nghiệp vụ.

Sau khi đơn giản hóa phần nghiệp vụ, chạy lại Test-P3.ps1: 69 tests đạt, HTTP catalog/auth đạt, khởi động lại đạt. Bằng chứng tại `.local/p3-20261025-152516-6dcfd04b/verification.json` (08:26:45 UTC ngày 25/10/2026). Các thay đổi định dạng sau đó được build lại thành công.

- ADMIN quản lý tuyến, phương tiện BUS/TRAIN, ghế theo phương tiện và chuyến: list phân trang, get, create, update, delete có bảo vệ references/history.
- Public GET `/api/v1/trips`: filter origin/destination/date/vehicleType, phân trang 0-based, size 1–100, sort departureAt/id.
- Public GET `/api/v1/trips/{id}` và `/seats`: chi tiết đang mở bán, ghế AVAILABLE/HELD/BOOKED, không lộ booking/user.
- Search date theo ngày `Asia/Ho_Chi_Minh`, đổi sang UTC; chỉ SCHEDULED chưa khởi hành, route/vehicle active.
- Chặn lịch phương tiện chồng nhau, cho phép hai khoảng nối đúng giờ; CANCELLED giải phóng lịch. Tạo chuyến cần phương tiện có ghế.
- Chặn sửa/xóa chuyến có booking history; chặn thay layout ghế của phương tiện có booking history; không cascade xóa lịch sử.
- Tắt route/vehicle để ngừng công khai; route đã có trips không đổi endpoints, vehicle đã có trips không đổi type.
- COMPLETED chỉ khi giờ đến cũ và mới đã qua; CANCELLED/COMPLETED không sửa tiếp. Chưa hỗ trợ hủy chuyến có booking hoặc hoàn tiền.
- Demo SQL opt-in, không tài khoản/password, không startup seed, phương tiện có mã và tên DEMO rõ ràng.

## File tạo và sửa

File mới:

- `common/`: `CatalogGuard.java`, `PageResponse.java`.
- `route/`: `Route.java`, `RouteRequest.java`, `RouteResponse.java`, `RouteRepository.java`, `RouteService.java`, `AdminRouteController.java`.
- `vehicle/`: `VehicleType.java`, `Vehicle.java`, `VehicleRequest.java`, `VehicleResponse.java`, `VehicleRepository.java`, `Seat.java`, `SeatRequest.java`, `SeatResponse.java`, `SeatRepository.java`, `VehicleService.java`, `AdminVehicleController.java`.
- `trip/`: `TripStatus.java`, `Trip.java`, `TripRequest.java`, `TripResponse.java`, `TripRepository.java`, `TripSeatResponse.java`, `TripService.java`, `TripController.java`, `AdminTripController.java`.
- Các package trên nằm tại `backend/src/main/java/com/example/ticketbooking/`.
- `backend/src/main/resources/db/migration/V3__catalog_query_indexes.sql`.
- `backend/src/test/java/com/example/ticketbooking/trip/CatalogIntegrationTest.java`.
- `database/02_demo_seed.sql`, `scripts/Test-P3.ps1`, `docs/catalog-api.md`, `docs/P3-report.md`.

File sửa:

- `SecurityConfig.java`: mở đúng GET public trips; các admin APIs vẫn yêu cầu ADMIN.
- `GlobalExceptionHandler.java`: data conflict trả JSON 409, không lộ SQL.
- `InfrastructureIntegrationTest.java`: kỳ vọng schema V3; upgrade V1→latest vẫn kiểm chứng được.
- `scripts/Test-P1.ps1`: tái sử dụng lifecycle cluster/build, thêm P3 HTTP smoke và seed lặp.
- `README.md`, `docs/api-contract.md`, `docs/architecture.md`, `database/README.md`.

Không thêm dependency, sửa stack, sửa V1/V2 hoặc đưa secrets vào source. `.env` và database/service local của người dùng giữ nguyên.

## Lệnh và bằng chứng

```powershell
# Trong backend, bước compile ban đầu
.\mvnw.cmd -B -o -DskipTests package

# Từ thư mục gốc, nghiệm thu đầy đủ
.\scripts\Test-P3.ps1 -JavaHome 'C:\Program Files\Java\jdk-21.0.12' -Offline

# Kiểm tra diff và bảo toàn migration cũ
git diff --check
git diff --exit-code -- backend/src/main/resources/db/migration/V1__initial_schema.sql backend/src/main/resources/db/migration/V2__normalize_user_email.sql database/01_schema.sql
```

Evidence lần cuối: `.local/p3-20261025-150738-d939966f/verification.json`, application logs, pgctl logs. XML/text reports: `backend/target/surefire-reports/`. Script chỉ tạo account/database/demo trong cluster riêng, bind localhost, rồi dừng app/PostgreSQL do nó tạo.

Lần đầu đã pass 68 tests cùng HTTP smoke; sau rà soát thêm điều kiện COMPLETED không nhận arrival mới ở tương lai và thêm test tương ứng. Seed cũng được chặn khi phương tiện demo đã có booking. Lần cuối chạy lại toàn bộ đạt 69 tests và HTTP smoke.

## Phạm vi kiểm thử

- CRUD đầy đủ tài nguyên chưa được sử dụng; list/detail/PUT/DELETE và mã 201/204.
- Anonymous 401 và CUSTOMER 403 ở các admin resources; public chỉ GET.
- Code vehicle/seat trùng khác case; ghế khác phương tiện; ID/query/body/enum sai; tiền âm/quá precision/quá scale; thiếu tài nguyên.
- Không xóa references; không đổi endpoints/type đã có trips; booking history chặn trip/layout mutations kể cả booking CANCELLED.
- Ngày Việt Nam gồm đúng đầu ngày, loại đầu ngày tiếp; kết quả sắp xếp/phân trang ổn định; filter type/status; ẩn past/cancelled/completed/inactive.
- BOOKED/HELD/AVAILABLE, hold quá hạn nhưng active chưa dọn vẫn hiển thị AVAILABLE; GET không tự cập nhật `is_active` và không lộ chủ đơn.
- Chồng lịch trả 409; nối giờ được phép; khác vehicle được trùng giờ; CANCELLED không chiếm lịch; COMPLETED phải thực sự đã đến.
- Hai request đồng thời xếp cùng vehicle/khoảng giờ: một 201, một 409, database chỉ có một trip.
- Hồi quy toàn bộ auth P2 và schema/security P1.

## Quyết định và giới hạn

- Admin writes dùng transaction advisory lock PostgreSQL chung để tuần tự hóa kiểm tra lịch/metadata; phù hợp lưu lượng ghi thấp của đồ án. Reads không bị khóa này. Đây không phải cơ chế giữ ghế P4.
- Trip/route/vehicle/layout writes khóa các trip liên quan trước kiểm tra history. P4 cần khóa Trip trước tạo booking để bảo vệ chung với catalog.
- Không có exclusion constraint chống chồng lịch cho SQL ghi trực tiếp; không cài extension btree_gist. Nghiệp vụ phải đi qua service; seed chỉ dùng database demo.
- API search khớp tên đầy đủ, không hỗ trợ tìm substring/bỏ dấu; chưa có custom sort.
- Chưa tạo Booking/BookingSeat entities, API giữ ghế, expiry scheduler, UI, payment hoặc tickets. Các rows booking trong tests chỉ là fixture.
- Seed không cập nhật thời gian của chuyến demo cũ; nếu đã qua ngày, dùng database demo mới hoặc quản trị qua API. Không chạy seed vào database người dùng thật.
- Kiểm chứng Windows/JDK 21/PostgreSQL 18.4; chưa chạy ma trận PostgreSQL 16/17 hoặc Linux. Workaround socket Java P1 vẫn được script áp dụng.
- Mã P3 được quản lý trên nhánh `feature/catalog-trips`, tạo từ `develop`.
- Khi khởi động lại backend local, Flyway tự áp dụng V3. Test không nâng schema hoặc chèn demo vào database local của người dùng.

## WBS và tiếp theo

WBS chính: `1.3.2.1` (CRUD tuyến/phương tiện/ghế), `1.3.2.2` (lịch/giá/tìm chuyến). Seed/test hỗ trợ `1.6.1.1`, `1.6.1.2`. T03/T04 và các quy tắc catalog đã có bằng chứng; chưa coi T05 chống đặt trùng ghế là hoàn thành.

P4 tiếp theo: Booking/BookingSeat entities, tạo đơn giữ ghế có hạn, transaction/unique conflict, hủy và hết hạn nguyên tử, thử hai khách tranh cùng ghế. Không triển khai P4 trong lượt này.
