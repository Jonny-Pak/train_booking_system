# P6 — Kết quả triển khai VNPAY Sandbox và vé điện tử

Mã nguồn nằm trên nhánh `feature/vnpay-payment`, bắt đầu từ `develop` tại commit `6b23ddf` sau khi merge [PR #3 Customer UI](https://github.com/Jonny-Pak/train_booking_system/pull/3). Các thay đổi P6 đã được lưu, khôi phục và đối chiếu sau khi chuyển nền nhánh. Phần mã P6 được theo dõi riêng trên nhánh này; chưa merge vào develop.

## Kết quả

- Tạo URL VNPAY PAY 2.1.0, amount từ database, HMAC-SHA512, deadline không vượt giữ ghế.
- Một attempt PENDING mỗi booking; gọi lặp tái sử dụng attempt, thất bại có thể thử lại khi còn hạn.
- IPN kiểm tra chữ ký, merchant, tham số trùng, amount, txn_ref, provider ID, status và pay date.
- Khóa Trip → Booking → Payment, chống tranh chấp với cancel/expiry và chống xử lý IPN trùng.
- Booking PAID, payment SUCCESS và một vé mỗi ghế được ghi nguyên tử; lỗi ghi vé rollback toàn bộ.
- Thanh toán đến sau hủy/hết hạn hoặc callback mâu thuẫn/review được gắn đối soát, không lấy lại ghế, không tạo vé hoặc mở thêm yêu cầu thu tiền.
- Customer có nút thanh toán, trang kết quả lấy trạng thái backend, lịch sử giao dịch, vé QR và in/lưu PDF bằng trình duyệt.
- API ADMIN đọc danh sách đối soát; Admin UI, tự hoàn tiền và check-in không thuộc P6.
- Flyway V6 giữ nguyên chín bảng; các migration cũ không đổi.

## File/module chính

- `backend/.../payment/`: Payment, repository, DTO, VnpayGateway, PaymentService, hai controller.
- `backend/.../ticket/`: Ticket, repository, DTO và service phát hành/đọc vé.
- `backend/.../booking/Booking.java`: chuyển PAID có kiểm soát trong service thanh toán.
- `backend/src/main/resources/db/migration/V6__payment_and_ticket_flow.sql`: expiry/reconciliation metadata và unique pending payment.
- `frontend/src/pages/PaymentReturnPage.jsx`, `TicketsPage.jsx`, `BookingPage.jsx`, `payment.css`.
- `frontend/e2e/payment.spec.js`, `backend/src/test/.../payment/`, `scripts/Test-P6.ps1`.
- API, chính sách trạng thái và cấu hình: [payment-api.md](payment-api.md).

## Kiểm chứng cuối

Hoàn tất ngày **25/10/2026 lúc 20:57:17 giờ Việt Nam**, bằng Java 21.0.12, PostgreSQL 18.4 và Edge. Chứng cứ: `.local/p6-20261025-205305-b0008cd6/verification.json`; log tổng `.local/p6-acceptance.log`.

| Kiểm tra | Kết quả |
|---|---|
| ESLint | Đạt |
| Frontend unit tests | 18 đạt |
| Production build Vite | Đạt |
| Backend tests trên PostgreSQL | 136 đạt, không bỏ qua |
| Browser E2E | 9 đạt |
| Lần khởi động backend đầu | UP, 6 migrations |
| Lần khởi động backend thứ hai | UP, 6 migrations không đổi |
| Dữ liệu thanh toán/vé sau restart | SUCCESS và hai vé đọc được bằng API |
| Máy chủ kiểm thử tạm | Đã dừng |

Các test bao phủ: chữ ký có vector Node crypto độc lập; amount/merchant/txn_ref sai; quyền truy cập; provider transaction trùng; attempt/IPN đồng thời; cancel/expiry race; đúng thời điểm hết hạn; retry sau FAILED; callback mâu thuẫn; trạng thái nghi ngờ/đảo giao dịch; rollback khi database từ chối ghi vé; return success giả không làm PAID; QR/mobile/print và reload.

Đã xem ảnh `payment-success-desktop.png`, `tickets-desktop.png`, `tickets-mobile.png`, `payment-reconciliation.png` trong thư mục screenshots cùng lượt kiểm chứng. Dữ liệu, mã vé và giao dịch trong ảnh chỉ thuộc database kiểm thử.

## Cách chạy lại

```powershell
.\scripts\Test-P6.ps1 -JavaHome 'C:\Program Files\Java\jdk-21.0.12' -Offline
```

Script cấp cổng riêng cho PostgreSQL, backend, SMTP, inbox và Vite; giữ lịch sử cổng đã cấp để tránh Windows trả lại cùng cổng ephemeral. Không ảnh hưởng server/database của người dùng.

## Phân biệt kiểm chứng cục bộ và Sandbox merchant

Các test tự động dùng gateway HTML được chặn tại browser và callback ký bằng merchant/secret fixture, gửi qua HTTP tới backend thật với PostgreSQL thật. Không gọi cổng VNPAY và không thực hiện giao dịch trên merchant của người dùng.

Người dùng đã có tài khoản Sandbox, nhưng tại thời điểm bàn giao chưa điền `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_IPN_URL` vào `.env`. Đã chuẩn bị các biến này; mặc định payment disabled để không giả lập thanh toán thành công trong ứng dụng. Cần điền merchant/secret, bật `VNPAY_ENABLED=true`, khai báo IPN HTTPS công khai với VNPAY và chạy một giao dịch Sandbox thật để nghiệm thu merchant. Return success không thay thế IPN.

Không đưa secret vào frontend/Git; cấu hình PostgreSQL và Gmail hiện có được giữ nguyên. Không tự bật tunnel công khai, thanh toán tiền thật, hoàn tiền hoặc phát hành vé có giá trị vận chuyển thực tế.

P6 hoàn thành phần mã nguồn và kiểm chứng cục bộ cho WBS thanh toán/IPN/phát hành vé, phục vụ Sprint 4–5. Nghiệm thu Sandbox merchant còn phụ thuộc cấu hình nêu trên. Tiếp theo P7: Admin UI, thống kê và nghiệm thu toàn hệ thống.
