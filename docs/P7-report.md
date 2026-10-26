# P7 — Admin UI, thống kê và nghiệm thu hệ thống

Nhánh: `feature/custom-admin`. Phạm vi triển khai: quản trị tuyến/phương tiện/ghế/chuyến, tra cứu đơn, hủy đơn giữ chỗ có xác nhận, xem thanh toán/vé, danh sách đối soát và tổng quan kinh doanh theo ngày Việt Nam.

## Thiết kế

- Giữ Modular Monolith. Chỉ thêm module `admin` với controller/service/response cho thống kê; tận dụng các API catalog, booking, payment và ticket đã có.
- Giữ PostgreSQL xuyên suốt, chín bảng, sáu migration. Không thêm bảng chỉ để phục vụ dashboard, không thay đổi transaction giữ ghế hoặc IPN.
- Frontend tách `AdminLayout`, `AdminDashboard`, `AdminCatalog`, `AdminBookings`, `AdminBookingDetail`, `AdminPagination`. Cấu hình field catalog và chuyển thời gian nằm trong `catalog.js`; không thêm thư viện quản trị hoặc form phức tạp.
- Backend dùng SQL tổng hợp có tham số để không tải tất cả đơn vào bộ nhớ. Doanh thu loại khoản cần đối soát. API admin không nhận role do client gửi.
- Thao tác phá hủy cần xác nhận trên UI; quy tắc bảo vệ lịch sử tiếp tục do service backend quyết định.

Contract, hướng dẫn tạo quyền ADMIN và công thức thống kê: [admin-api.md](admin-api.md).

## Tiêu chí nghiệm thu

1. Anonymous được chuyển tới đăng nhập; CUSTOMER bị chặn ở UI và API; ADMIN dùng được trang quản trị.
2. CRUD tuyến/phương tiện/ghế/chuyến dùng dữ liệu thực; hiển thị lỗi trùng mã, lịch chồng, đang được sử dụng; không bỏ sót selector sau trang đầu.
3. Danh sách đơn phân trang/lọc; chi tiết có ghế, chuyến, lịch sử payment; chỉ hủy đơn chưa thanh toán; vé được xem qua API có phân quyền.
4. Thống kê đúng ranh giới ngày Việt Nam, range sai trả 400, số 0 đúng khi không có dữ liệu, không cộng payment thất bại/đối soát vào doanh thu.
5. Customer tìm chuyến/chọn ghế/đặt vé, email verification, khóa chống trùng, IPN idempotent và vé tiếp tục vượt bộ kiểm thử hồi quy.
6. Migration chạy trên DB trống và không chạy lại khi restart; thanh toán/vé đã xác nhận còn sau restart; không ghi khóa thật vào repo.
7. Dashboard hoạt động trên desktop/mobile, không tràn chiều ngang trang; bảng dài cuộn trong vùng riêng.

## Giới hạn nghiệm thu

Merchant VNPAY thực và IPN HTTPS công khai do chủ tài khoản cấu hình. Kiểm thử tự động chỉ dùng merchant tạm và IPN ký đúng để kiểm chứng nghiệp vụ. Chưa có quy trình hoàn tiền, xử lý đối soát tự động, biểu đồ kế toán hay phân quyền nhiều cấp; không nằm trong MVP P7.

## Cách chạy

Thực hiện `scripts/Test-P7.ps1` theo [admin-api.md](admin-api.md). Không cần khởi động các server chính để chạy nghiệm thu độc lập.

## Kết quả ngày 25/10/2026

Lượt cuối: `.local/p7-20261025-221926-63071c2a/verification.json`. Log: `.local/p7-acceptance-final.log`.

- Backend: **143 tests đạt**, `clean verify` thành công trên PostgreSQL 18.4 (không dùng H2/MySQL).
- Frontend: lint đạt, **23 unit tests đạt**, production build thành công.
- Edge: **14 E2E tests đạt** (3 Admin, 7 Customer, 2 chọn địa điểm, 2 payment/ticket).
- Admin E2E tạo/sửa/xóa tuyến, phương tiện, ghế và chuyến; kiểm tra lỗi trùng ghế; lọc đơn và mở chi tiết; hủy đơn có xác nhận và xác minh ghế được trả; thử lại lỗi thống kê; chặn anonymous/CUSTOMER.
- Statistics integration tests kiểm tra số 0, khoảng mặc định, range sai, phân quyền, hết hạn logic, doanh thu loại khoản đối soát, ngày thanh toán khác ngày tạo đơn và biên ngày Việt Nam.
- HTTP smoke đạt auth/email/JWT, catalog/lịch chồng, booking/chống đặt trùng/cancellation/scheduler thật.
- Hai lần khởi động backend health UP, sáu migration hợp lệ; JWT, đơn, thanh toán thành công và hai vé vẫn còn sau restart.
- Đã xem ảnh dashboard desktop/mobile: `screenshots/admin-desktop.png`, `admin-mobile.png`; không tràn ngang.
- Script đã dừng toàn bộ server/cluster thử nghiệm. Không khởi động lại các server chính, không tự cấp quyền ADMIN, chưa commit/push P7.

Trong integration tests dùng đồng hồ cố định, script tắt scheduler nền để các Spring context không xử lý fixture bằng đồng hồ khác nhau. Khi chạy ứng dụng tạm, scheduler được bật lại và được kiểm chứng bằng HTTP smoke thực.

Đây là nghiệm thu mã nguồn và môi trường độc lập. Giao dịch qua gateway VNPAY Sandbox thực còn phụ thuộc cấu hình merchant/IPN nêu ở phần giới hạn.
