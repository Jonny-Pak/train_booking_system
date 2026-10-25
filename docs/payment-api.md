# P6 — VNPAY Sandbox và vé điện tử

## Phạm vi và luồng

Customer giữ ghế PENDING → tạo yêu cầu thanh toán → chuyển đến VNPAY Sandbox → IPN hợp lệ xác nhận SUCCESS → booking PAID và phát hành một vé cho mỗi ghế trong cùng transaction. Return URL chỉ đọc dữ liệu backend, không đổi trạng thái dù URL có `vnp_ResponseCode=00`.

Stack giữ nguyên Java 21/Spring Boot/JPA/Flyway/PostgreSQL và React/Vite. Chỉ tích hợp Sandbox, không có chuyển đổi sang cổng thanh toán tiền thật. Hoàn tiền tự động, querydr tự động và check-in vé không nằm trong P6.

## API

| Method | Endpoint | Quyền | Kết quả |
|---|---|---|---|
| POST | `/api/v1/bookings/{id}/payments` | Chủ đơn | 200 payment và URL VNPAY đã ký; không nhận amount từ client |
| GET | `/api/v1/bookings/{id}/payments` | Chủ đơn/ADMIN | Lịch sử các lần thanh toán, mới nhất trước |
| GET | `/api/v1/payments/{txnRef}` | Chủ đơn/ADMIN | Trạng thái từ backend; dùng trên trang return |
| GET | `/api/v1/payments/vnpay/ipn` | Public, bắt buộc HMAC hợp lệ | JSON `RspCode`/`Message`, HTTP 200 cho các kết quả xử lý IPN |
| GET | `/api/v1/bookings/{id}/tickets` | Chủ đơn/ADMIN | Danh sách vé; rỗng nếu chưa có vé PAID |
| GET | `/api/v1/admin/payments/reconciliation?page=0&size=20` | ADMIN | Các payment cần xử lý thủ công |

Người khác nhận 404 để không lộ giao dịch/vé. Thiếu JWT nhận 401. ADMIN không được tự thanh toán đơn của người khác. Các response thành công có `Cache-Control: no-store`.

Ví dụ tạo thanh toán (JWT của chủ đơn):

```http
POST /api/v1/bookings/42/payments
Authorization: Bearer <access-token>
```

```json
{
  "id": 5,
  "bookingId": 42,
  "txnRef": "<unique-reference>",
  "amount": 300000.00,
  "status": "PENDING",
  "expiresAt": "2026-10-25T14:10:00Z",
  "paidAt": null,
  "reconciliationRequired": false,
  "reconciliationReason": null,
  "responseCode": null,
  "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?<signed-query>"
}
```

Các GET trả `paymentUrl: null`: lịch sử không cung cấp lại URL cũ. Gọi POST khi còn hợp lệ sẽ trả lại cùng attempt PENDING; không tạo thêm lần thanh toán. Sau FAILED có thể tạo attempt mới nếu booking còn hạn và không cần đối soát. Backend từ chối khi còn dưới hoặc bằng 5 giây giữ chỗ, đã đóng đơn, trip ngừng hoạt động hoặc amount không phù hợp.

Amount phải là số nguyên VND dương và sau nhân 100 nằm trong trường 12 chữ số. Giá lấy từ snapshot booking, không dùng giá/amount do trình duyệt gửi. Thời gian VNPAY theo Asia/Ho_Chi_Minh với định dạng `yyyyMMddHHmmss`, expire không vượt deadline giữ ghế và được làm tròn xuống giây.

## IPN và tính toàn vẹn

1. Chỉ chấp nhận một giá trị cho mỗi tham số. Giới hạn số lượng/kích thước tham số; bỏ `vnp_SecureHash` và `vnp_SecureHashType` khỏi chuỗi ký.
2. Sắp xếp tên tham số; URL-encode UTF-8 và ký HMAC-SHA512; so sánh chữ ký theo thời gian hằng. Kiểm tra merchant trước khi truy cập payment.
3. Tìm txn_ref đã tạo, khóa theo thứ tự **Trip → Booking → Payment**. Cancel và expiry dùng cùng khóa Trip, nên không thể giải phóng ghế của booking đã được xác nhận PAID.
4. Kiểm tra số tiền khớp payment và booking, currency VND nếu có, response/status hai chữ số, provider transaction ID và pay date hợp lệ. Chỉ cặp response `00`, status `00` được xem là thành công.
5. Khi đơn còn giữ ghế hợp lệ, transaction cập nhật booking PAID, giữ active seats, tạo vé và payment SUCCESS. Mọi lỗi ghi vé/DB rollback toàn bộ; IPN trả 99 để VNPAY thử lại.

`txn_ref`, `provider_transaction_id` và `booking_seat_id` của vé có uniqueness tại PostgreSQL. Partial unique index mới chỉ cho phép một payment PENDING trên mỗi booking. IPN trùng trả 02, không ghi thêm vé hoặc ghi nhận doanh thu lần hai. Callback mâu thuẫn không được hạ SUCCESS thành FAILED.

| RspCode | Ý nghĩa phản hồi merchant |
|---|---|
| 00 | Đã ghi nhận kết quả vào database, gồm trường hợp ghi nhận cần đối soát |
| 02 | Đã xác nhận trước đó |
| 01 | Không tìm thấy giao dịch |
| 04 | Số tiền/currency không hợp lệ |
| 97 | Sai chữ ký, merchant hoặc bộ tham số không hợp lệ |
| 99 | Chưa thể ghi nhận/xử lý; cho phép provider retry |

### Thanh toán muộn và trạng thái cần đối soát

Nếu IPN SUCCESS tới lúc deadline đã qua (kể cả đúng deadline), đơn bị hủy/hết hạn, ghế không còn active, chuyến đóng, pay date bất thường hoặc trước đó đã FAILED: ghi nhận financial payment SUCCESS nhưng bật `reconciliationRequired`; **không** chuyển đơn thành PAID, không lấy lại ghế và không tạo vé.

Callback trạng thái chưa hoàn tất, đảo giao dịch, hoàn tiền, nghi ngờ gian lận (01/04/05/06/07/09), hoặc response/status mâu thuẫn cũng được gắn cần đối soát; không cho tạo thêm thanh toán cho cùng đơn khi chưa xử lý. Payment có thể mang FAILED cùng flag đối soát vì chưa được chấp nhận hoàn tất đơn, không có nghĩa là chắc chắn chưa bị trừ tiền.

Admin xem API danh sách đối soát, dùng txnRef/provider transaction ID đối chiếu với portal Sandbox và hỗ trợ người dùng. P6 không có API tự đánh dấu PAID/giải quyết đối soát, không tự gửi lệnh hoàn tiền. Thống kê doanh thu đặt vé ở pha sau phải lọc SUCCESS **và không cần đối soát**, gắn booking PAID; không cộng mọi SUCCESS vào doanh thu vé.

## Vé điện tử và giao diện

`/bookings/{id}/tickets` có mã vé ngẫu nhiên, hành trình, phương tiện, giờ đi/đến, hạng/ghế, snapshot giá và tên tài khoản đặt chỗ. Schema hiện chưa có tên hành khách riêng cho từng ghế; không tự bổ sung dữ liệu hành khách giả. Không có API công khai phát hành vé hoặc tra cứu thông tin cá nhân qua QR.

QR tạo cục bộ bằng `qrcode.react`, nội dung chỉ `LEN_DUONG:<ticketCode>`, không gửi dữ liệu vé sang dịch vụ tạo QR bên ngoài. QR dùng nhận diện vé, chưa có nghiệp vụ check-in/chống quét lại. Nút **In vé / Lưu PDF** dùng chức năng in của trình duyệt; không có endpoint tạo PDF phía server. Vé và UI luôn ghi rõ Sandbox, không dùng đi lại thực tế.

Trang `/payment/return` chỉ lấy txnRef từ query, yêu cầu đăng nhập đúng chủ đơn và đọc payment từ backend. Poll 3 giây tối đa 20 lượt, sau đó có nút kiểm tra lại. Không nhận IPN hoặc phát hành vé qua trang này. Trang chi tiết đơn có lịch sử thanh toán, nút thanh toán khi còn hạn và đường dẫn xem vé khi PAID.

## Cấu hình và chạy Sandbox thật

Điền `.env` gốc trên máy (không commit khóa):

```dotenv
VNPAY_ENABLED=true
VNPAY_TMN_CODE=<merchant-sandbox-code>
VNPAY_HASH_SECRET=<merchant-sandbox-secret>
VNPAY_RETURN_URL=http://localhost:5173/payment/return
VNPAY_IPN_URL=https://<your-public-backend>/api/v1/payments/vnpay/ipn
```

`VNPAY_ENABLED=false` mặc định: backend vẫn khởi động được; tạo thanh toán trả 503 `VNPAY_NOT_CONFIGURED`. Bật true yêu cầu merchant/secret/return URL hợp lệ. Endpoint gateway luôn cố định Sandbox. Không đưa khóa vào `VITE_*`, frontend hoặc Git. Không tin `X-Forwarded-For` từ client; IP lấy từ servlet remote address. Nếu triển khai sau reverse proxy, cấu hình proxy tin cậy trước khi thay chính sách IP.

`VNPAY_IPN_URL` dùng để bạn đăng ký IPN trong cấu hình merchant với VNPAY; không phải tham số gửi theo pay URL và không tự đăng ký từ ứng dụng. VNPAY không gọi được localhost. Cần backend HTTPS công khai hoặc tunnel được kiểm soát, ánh xạ đúng route GET IPN. Return URL có thể về trình duyệt local nếu merchant cho phép; khi triển khai thực tế dùng địa chỉ frontend tương ứng và cấu hình SPA fallback cho `/payment/return` và `/bookings/*/tickets`.

Khởi động backend/frontend theo README hiện có. Flyway tự áp dụng V6. Thử từ một booking mới còn đủ thời gian giữ ghế; dùng dữ liệu thẻ/OTP kiểm thử do VNPAY cung cấp. Không dùng thông tin thẻ thật. Sau thanh toán, kiểm tra backend đã nhận IPN và booking PAID; return success một mình chưa đủ.

Nếu chưa có URL công khai/merchant hợp lệ, vẫn chạy được bộ kiểm chứng cục bộ phía dưới. Không bật endpoint giả lập thanh toán trong application.

## Kiểm thử tái lập

```powershell
.\scripts\Test-P6.ps1 -JavaHome 'C:\Program Files\Java\jdk-21.0.12' -Offline
```

Script chạy lint/unit/build, real PostgreSQL tests, hai lần khởi động backend, SMTP inbox cục bộ và Edge E2E. Tạo merchant/secret ngẫu nhiên chỉ cho lượt kiểm chứng, ghi đè trong process rồi khôi phục môi trường. Không dùng secret/email/database của người dùng, không gọi VNPAY thật: browser navigation đến gateway được chặn bằng fixture HTML, IPN được Node crypto ký độc lập và gửi tới backend tạm.

Bao phủ signature vector độc lập, amount/merchant/txn_ref sai, tham số lặp, quyền truy cập, tạo attempt đồng thời, IPN trùng/đồng thời, cancel/expiry race, giữ ghế sau PAID, provider transaction trùng, ngày thanh toán sai, retry sau thất bại, callback mâu thuẫn, provider review, rollback khi ghi vé thất bại, QR/print/mobile, reload và dữ liệu vé sau backend restart.

V6 giữ chín bảng, không sửa migration V1–V5. Nếu database tự chèn nhiều payment PENDING cho cùng booking từ trước, unique index V6 sẽ từ chối nâng schema; cần kiểm tra và xử lý dữ liệu thủ công, không tự xóa payment.

Nguồn kỹ thuật: [VNPAY PAY 2.1.0](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html), [quy tắc HMAC và IPN](https://sandbox.vnpayment.vn/apis/docs/chuyen-doi-thuat-toan/changeTypeHash.html), [qrcode.react](https://github.com/zpao/qrcode.react).
