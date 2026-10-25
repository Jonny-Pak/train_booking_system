# Báo cáo hoàn thành P5 — React Customer

Ngày kiểm chứng: 25/10/2026. Nhánh feature/customer-ui, tạo từ develop đã có P4 (41714fd).

## Kết quả

Giao diện Customer đã kết nối backend thật để đăng ký, đăng nhập, tìm chuyến, chọn ghế, giữ chỗ, xem lịch sử/chi tiết và hủy đơn. Responsive desktop/mobile, ngôn ngữ tiếng Việt; không dùng dữ liệu thành công giả khi API lỗi.

| Kiểm tra | Kết quả |
|---|---|
| npm run lint | Đạt, không lỗi hoặc warning |
| npm test | 17 tests đạt trong 4 file |
| npm run build | Đạt, xuất frontend/dist |
| Backend clean verify | 104 tests đạt; 0 failures, errors, skipped |
| Playwright / Edge thật | 4 E2E tests đạt, không retry |
| HTTP regression | Auth, catalog, booking và scheduler đạt |
| Restart | Health UP hai lần; JWT và booking/expiry nhất quán |
| Schema | V1–V4 giữ nguyên; không thêm migration hoặc bảng |
| Visual QA | Đã xem ảnh home/seat/booking trên desktop và mobile |
| Mobile overflow | Kiểm tra home và booking detail tại 375px, không tràn ngang |
| Database local | Không sửa cấu hình hoặc dữ liệu database cá nhân |
| Secrets | Không tìm thấy DB password/JWT secret local trong file có thể đưa vào Git |

Môi trường: Windows, Node 24.19.0, Java 21.0.12, PostgreSQL 18.4, React 19.3.1, Vite 8.3.5. Dependency có phiên bản cụ thể và package-lock.json.

Bằng chứng cuối: .local/p5-20261025-164921-dafc819e/verification.json, verifiedAt 2026-10-25T09:54:39Z. Playwright frontend/test-results/.last-run.json có status passed; report tại frontend/playwright-report. Các kết quả sinh tự động bị Git bỏ qua và có thể tái tạo bằng script.

## Các luồng trình duyệt đã kiểm chứng

1. Tìm chuyến thật, lọc tàu hỏa, hiển thị empty, mô phỏng mất mạng rồi retry; chụp desktop/mobile.
2. Guard trang lịch sử → đăng ký → login sai mật khẩu được báo lỗi → login đúng → reload giữ phiên → chọn hai ghế → tạo đơn → xem lịch sử/chi tiết → xác nhận hủy → ghế mở lại → logout.
3. Chọn ghế trước login, quay lại vẫn giữ lựa chọn; một khách khác giữ ghế qua API thật; submit nhận 409, UI báo lỗi, bỏ lựa chọn và vô hiệu ghế HELD sau khi tải lại.
4. Tạo hold có hạn, đếm ngược kết thúc, UI ngừng cho hủy; scheduler ghi EXPIRED trong database, API xác nhận và sơ đồ cho đặt lại ghế.

Trong E2E, chỉ backend phục vụ browser được cấu hình hold 30 giây; regression tests vẫn kiểm tra mặc định 600 giây. Origin Vite ngẫu nhiên được thêm rõ ràng vào CORS của tiến trình tạm, không dùng wildcard và không sửa .env cá nhân.

## File/module

- frontend/package.json, package-lock.json, Vite/ESLint/Playwright config, index.html và .env.example.
- src/auth: AuthProvider, AuthContext, RequireAuth, session theo tab.
- src/pages: SearchPage, AuthPage, TripPage, BookingsPage, BookingPage.
- src/components: TripCard, Pagination, StatusBadge, loading/error/empty states.
- src/lib: fetch API client, tiền VND, giờ Việt Nam, deadline, validation password và return path.
- src/hooks/useNow: đồng hồ hiển thị.
- src/styles.css: bố cục, màu, responsive, focus và reduced motion.
- public: favicon SVG và giấy phép font; Be Vietnam Pro đóng gói qua npm, không tải font từ CDN.
- src/**/*.test và e2e/customer.spec.js: unit/regression tests và browser tests.
- Backend CorsConfig, SecurityConfig, application.yml: CORS theo danh sách origin cụ thể; CorsIntegrationTest có 4 tests.
- scripts/Test-P5.ps1 và Test-P1.ps1: lifecycle kiểm chứng trên PostgreSQL riêng, HTTP, Vite/Edge tạm, restart và khôi phục biến môi trường.
- README gốc, frontend/README.md, API contract và architecture: hướng dẫn sử dụng/cấu hình.

Code chia theo trang và trách nhiệm, dùng state/effect thông thường, hàm có tên rõ nghĩa, if/else và cách render danh sách React cơ bản. Không thêm Redux, service frontend phức tạp hoặc UI framework lớn. JSX và CSS đã được định dạng thành khối dễ đọc.

## Quy tắc và giới hạn

- sessionStorage chỉ giữ phiên trong tab; reload gọi /auth/me. Hết hạn hoặc protected API trả 401 sẽ xóa phiên và yêu cầu login. Logout chỉ xóa phiên client, không thu hồi JWT server.
- Guard frontend hỗ trợ UX; owner/role, giá, deadline và chống đặt trùng vẫn do backend quyết định.
- Chọn tối đa 10 ghế; chưa giữ ghế cho đến khi POST thành công. Tạm tính không được gửi làm giá chính thức.
- Không tự retry POST, không tự đánh dấu PAID hoặc phát hành vé. Thanh toán chưa khả dụng được nêu rõ trên chi tiết đơn.
- Countdown là hiển thị theo đồng hồ trình duyệt; dữ liệu backend và trường holdExpired có quyền quyết định nghiệp vụ. Detail poll mỗi 15 giây, tạm dừng khi hủy.
- Schema chưa có tọa độ/toa/tầng; sơ đồ minh họa theo danh sách ghế. Khi chuyến không còn public, đơn vẫn hiển thị ID chuyến, ghế và giá snapshot.
- Có pagination cho tìm chuyến/lịch sử. Search theo tên tuyến đầy đủ theo API P3, chưa có autocomplete địa danh, tìm bỏ dấu hoặc bản đồ.
- Chưa làm UI quản trị, VNPAY, IPN, vé điện tử, reset password, refresh token hoặc triển khai hosting. Không thay đổi PostgreSQL hoặc stack đã thống nhất.
- Đã kiểm chứng Edge trên Windows; chưa chạy ma trận Safari/Firefox hoặc PostgreSQL 16/17.
- Các thay đổi P5 hiện ở workspace, chưa commit/push.

## Chạy

Backend chạy bằng Java 21 trong terminal riêng theo README gốc. Sau đó:

```powershell
cd frontend
npm ci
npm run dev
```

Truy cập http://127.0.0.1:5173. Vite proxy /api đến localhost:8080; cấu hình khác dùng frontend/.env.local. Không đưa secrets vào VITE_*.

Từ repository root để kiểm chứng toàn bộ:

```powershell
.\scripts\Test-P5.ps1 -JavaHome 'C:\Program Files\Java\jdk-21.0.12' -Offline
```

Script cần Node, dependency đã npm ci, PostgreSQL binaries và Edge; tạo cluster riêng, không chạy test trên database người dùng. Hướng dẫn preview/production, API URL, SPA fallback và CORS ở [frontend README](../frontend/README.md).

## Ảnh

Trong .local/p5-20261025-164921-dafc819e/screenshots/:

- home-desktop.png, home-mobile.png
- seats-desktop.png
- booking-desktop.png, booking-mobile.png

Các ảnh chứa dữ liệu demo của cluster kiểm chứng, không phải tài khoản hoặc đơn người dùng thật.

## WBS và tiếp theo

Homepage sau đó được chỉnh theo hai ảnh tham khảo của người dùng. Chi tiết bố cục, banner tạo mới và prompt tại [homepage-design.md](homepage-design.md); homepage vẫn dùng các API và luồng nghiệp vụ P5 hiện có.

Đăng nhập/đăng ký được chỉnh thành hộp thoại theo ảnh tham khảo tiếp theo. Chi tiết hành vi và phạm vi tại [auth-design.md](auth-design.md).

Hoàn thành phần Customer của 1.4.1.1 (React/router/auth), 1.4.1.2 (search/detail), 1.4.1.3 (seat/booking) và phần kết nối API/xử lý lỗi 1.4.3.1. WBS 1.4.1.4 đã có lịch sử đơn; vé điện tử chờ P6. Chưa đánh dấu Admin UI 1.4.2.* hoặc E2E thanh toán hoàn thành.

Tiếp theo P6: VNPAY Sandbox, kiểm tra IPN/idempotency và phát hành vé từ booking PAID. UI sẽ dựa vào trạng thái backend đã xác nhận.
