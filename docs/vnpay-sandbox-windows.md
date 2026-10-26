# VNPAY Sandbox — HTTPS callback trên Windows

## 1. Kết quả kiểm tra mã nguồn

| Nội dung | Hiện trạng / xử lý |
|---|---|
| Tạo thanh toán | `POST /api/v1/bookings/{id}/payments`, JWT chủ đơn; amount lấy từ booking |
| Return hiện có | React `/payment/return`, chỉ lấy txnRef rồi đọc API backend; không ghi trạng thái |
| IPN hiện có | Public `GET /api/v1/payments/vnpay/ipn`; xác minh HMAC trước khi truy vấn payment |
| Ký PAY 2.1.0 | Sắp xếp tên, encode UTF-8, HMAC-SHA512; VND nhân 100; CreateDate/ExpireDate giờ Việt Nam |
| Kiểm tra IPN | Merchant, txnRef, amount, response/status, provider transaction ID, pay date, tham số trùng |
| Transaction/idempotency | Trip → Booking → Payment; PAID/SUCCESS/vé nguyên tử; callback trùng không tạo thêm vé; lỗi DB trả 99 |
| Thanh toán muộn | Cờ đối soát; không lấy lại ghế hoặc phát hành vé cho đơn đã đóng |
| CORS/Security | Origin frontend cụ thể, không cookie; GET IPN public không cần JWT, API riêng vẫn có phân quyền |
| Thiếu trước lượt này | Chưa có public HTTPS/callback relay, merchant credentials trống, VNPAY bị tắt |
| Thay đổi tối thiểu | URL gateway qua env (chỉ cho Sandbox), public Return bắt buộc HTTPS; thêm relay, script cấu hình và kiểm tra |

Không thay service transaction, migration, quy tắc giữ ghế hoặc frontend đang hoạt động. `VNPAY_IPN_URL` là URL cần đăng ký với VNPAY, không phải tham số PAY và không tự đăng ký trên portal.

## 2. Topology cho demo local

```text
Trình duyệt localhost:5173 → Vite /api → Spring Boot localhost:8080
    └── paymentUrl có HMAC → VNPAY Sandbox

VNPAY IPN → HTTPS Cloudflare → relay 127.0.0.1:8787
                              └── GET IPN → backend 8080 → transaction PostgreSQL

Trình duyệt Return → HTTPS Cloudflare → relay /payment/return
                                      └── 302 → localhost:5173/payment/return?vnp_TxnRef=...
                                                └── API đọc trạng thái; không xác nhận tiền
```

Relay chỉ công khai hai đường GET. Không công khai Vite, file nguồn, `.env`, đăng nhập, Admin hoặc các API khác. Không chuyển tiếp JWT/cookie/Origin/X-Forwarded-For từ callback vào backend. IPN query giữ nguyên, kể cả field trùng để backend từ chối. Lỗi backend trả HTTP 502 và mã 99, không giả ACK thành công.

HTTPS Return là điểm vào công khai rồi chuyển về frontend local để giữ sessionStorage trên **đúng origin đã đăng nhập**. Cách này dành cho người thử trên cùng máy. Demo từ điện thoại/máy khác phải dùng frontend triển khai HTTPS thực ở phần 9. Không chuyển JWT qua URL để mang phiên giữa domain.

## 3. Chuẩn bị

- Windows, PowerShell 7 (`pwsh`), Java 21, Node theo `frontend/package.json`, PostgreSQL đã chạy.
- Backend 8080 và frontend 5173 theo README. Dùng `npm.cmd` để tránh lỗi ExecutionPolicy của `npm.ps1`.
- Tài khoản merchant **VNPAY Sandbox** cùng TMN Code/Hash Secret, không dùng khóa môi trường thật.
- Cloudflared: tải từ [trang chính thức](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/downloads/). Máy hiện tại có portable `.local/tools/cloudflared.exe` đã đối chiếu SHA-256 với GitHub release chính thức; binary không vào Git.

Thực hiện các lệnh dưới từ thư mục gốc `D:\Code\QL_DuAn_PhanMem\train_booking_system`, trừ nơi ghi rõ khác.

## 4. Chạy relay và tunnel

Terminal A, giữ chạy:

```powershell
node --env-file=.env scripts/vnpay-callback-server.mjs
```

Terminal B, giữ chạy:

```powershell
.\.local\tools\cloudflared.exe tunnel --url http://127.0.0.1:8787 --protocol http2 --no-autoupdate
# Nếu cài cloudflared vào PATH, dùng cloudflared.exe thay đường dẫn portable.
```

HTTP/2 dùng khi mạng chặn QUIC/UDP. Lấy `https://...trycloudflare.com` từ output. Không bật Access, email OTP, CAPTCHA hoặc trang đăng nhập trước đường IPN: callback của VNPAY là server-to-server.

[Quick Tunnel](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/) dùng URL tạm; hostname thay đổi khi tạo tunnel mới và URL ngừng hoạt động khi process dừng. Giữ máy/tunnel/relay/backend chạy trong lúc thử; muốn URL cố định dùng named tunnel/domain do bạn quản lý.

Nếu báo cổng 8787 đã dùng, không chạy relay lần hai. Lượt cấu hình hiện tại đã để relay/tunnel chạy nền; PID và log trong `.local/vnpay-tunnel/`. Khi mở terminal mới, chỉ chạy lại nếu các process cũ đã dừng.

## 5. Điền biến môi trường, đăng ký IPN

Chỉnh **`.env` gốc bị Git bỏ qua**, không chỉnh code hoặc `frontend/.env` để đưa merchant keys vào:

```dotenv
VNPAY_ENABLED=false
VNPAY_PAYMENT_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
VNPAY_TMN_CODE=<TMN Sandbox của bạn>
VNPAY_HASH_SECRET=<Hash Secret Sandbox của bạn>
VNPAY_RETURN_URL=https://<public-host>/payment/return
VNPAY_IPN_URL=https://<public-host>/api/v1/payments/vnpay/ipn
VNPAY_CALLBACK_PORT=8787
VNPAY_CALLBACK_BACKEND_URL=http://127.0.0.1:8080
VNPAY_CALLBACK_FRONTEND_URL=http://localhost:5173
```

Không để dấu `< >` quanh giá trị thật. Secret sao chép chính xác, không thêm dấu cách. Không đưa khóa vào `VITE_*`, Git, ảnh màn hình hoặc chat. `.env.example` chỉ chứa placeholder. Không ghi đè DB/JWT/SMTP hiện có.

Sau khi điền merchant keys, dùng script để đặt hai URL và bật thanh toán:

```powershell
.\scripts\Set-VnpayPublicUrl.ps1 -PublicBaseUrl 'https://<public-host>' -EnablePayment
```

Script chỉ sửa Return/IPN và cờ bật khi được yêu cầu, không in khóa. Có thể bỏ `-EnablePayment` để cấu hình URL trước khi có credentials. Sau đó:

1. Đăng ký **IPN URL đầy đủ** trong cấu hình merchant hoặc gửi cho đầu mối Sandbox VNPAY nếu tài khoản không có mục tự chỉnh.
2. Đăng ký/cập nhật Return/website URL nếu merchant yêu cầu. Return phải khớp URL được backend ký.
3. **Khởi động lại backend** để đọc `.env`. Không cần bật forwarded-header toàn cục hoặc bỏ phân quyền Spring Security.
4. Nếu dùng `http://127.0.0.1:5173` để đăng nhập, đổi `VNPAY_CALLBACK_FRONTEND_URL` sang đúng origin đó rồi khởi động lại relay. Không đổi origin giữa đặt vé và Return.

Lệnh backend ví dụ, điều chỉnh JAVA_HOME nếu khác máy:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12'
$socketPath = Join-Path (Get-Location).Path '.local\sockets'
New-Item -ItemType Directory -Path $socketPath -Force | Out-Null
Push-Location backend
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev' "-Dspring-boot.run.jvmArguments=-Djdk.net.unixdomain.tmpdir=$socketPath"
Pop-Location
```

Terminal khác chạy frontend:

```powershell
cd frontend
npm.cmd run dev
```

## 6. Kiểm tra kết nối trước khi thanh toán

```powershell
.\scripts\Test-VnpayPublicUrls.ps1 -PublicBaseUrl 'https://<public-host>'
```

Kỳ vọng: GET IPN không chữ ký trả HTTP 200 và `RspCode=97`; Return có mã giao dịch giả chỉ redirect về frontend, bỏ `vnp_ResponseCode`; Admin qua relay trả 404. Đây chỉ chứng minh HTTPS/routing hoạt động, chưa chứng minh merchant/IPN đã được VNPAY chấp nhận.

IPN không phải request từ JavaScript frontend nên không cần thêm domain VNPAY hoặc tunnel vào CORS. Đối với topology trên, giữ `APP_CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173`; callback không đính kèm Origin. Không dùng `*`, không đặt JWT vào callback và không trả 401 cho GET IPN.

## 7. Thử giao dịch qua gateway Sandbox

1. Đăng nhập Customer trên đúng frontend origin. Tạo **đơn mới** trên chuyến tương lai có ghế còn trống.
2. Bấm **Thanh toán VNPAY Sandbox** khi còn nhiều thời gian giữ ghế. Hoàn tất trước hạn khoảng 10 phút; gateway expiry không kéo dài thời gian giữ ghế.
3. Kiểm tra điều hướng đến `sandbox.vnpayment.vn/paymentv2/vpcpay.html`. Dùng thông tin thẻ/tài khoản/OTP thử nghiệm hiện hành do [VNPAY Sandbox](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html) cung cấp; không dùng thẻ thật.
4. Sau khi quay về, trang đọc trạng thái từ backend. Chỉ khi IPN xác nhận hợp lệ mới có payment SUCCESS không cần đối soát, booking PAID, mỗi ghế có một vé.
5. Mở **Xem vé điện tử**, kiểm tra đúng chuyến, ghế, số tiền và mã vé. Reload vẫn có vé.
6. Mở Admin → Tổng quan/Đơn hàng/Cần đối soát để kiểm tra. Đơn thành công cộng doanh thu một lần; khoản cần đối soát không tính vào doanh thu vé.

Ma trận thử thủ công:

| Tình huống | Kỳ vọng |
|---|---|
| Thanh toán thành công + IPN hợp lệ | PAID, một vé/ghế, SUCCESS |
| Hủy/thất bại trên gateway | Không có vé; chỉ thử lại khi đơn còn hạn và không cần đối soát |
| Sửa `vnp_ResponseCode=00` trên Return | Không thay đổi payment/booking |
| IPN thiếu/sai chữ ký, sai merchant/amount | Từ chối, không PAID/không vé |
| IPN lặp hợp lệ | ACK 02, không nhân đôi vé hoặc doanh thu |
| Thành công đến sau hủy/hết hạn | Cần đối soát, không lấy lại ghế, không tự phát hành vé |
| IPN không tới | Return chờ xác nhận; tra portal/cấu hình tunnel; không tự đánh dấu PAID |

Callback ký giả trong automated tests chỉ dùng khóa test độc lập. Không phát hành endpoint giả xác nhận tiền trong ứng dụng và không gửi callback ký thủ công vào đơn thật để "làm thành công" bài demo.

## 8. Lỗi thường gặp

| Hiện tượng | Cách kiểm tra |
|---|---|
| `VNPAY_NOT_CONFIGURED` / 503 | Điền merchant keys, bật true, restart backend |
| Backend từ chối URL | Public Return cần HTTPS, không query/fragment/userinfo; payment endpoint chỉ cho Sandbox |
| Gateway báo checksum | TMN/Secret cùng merchant Sandbox; không dư khoảng trắng; xem vector test, không in Secret hoặc toàn bộ signed URL ra log |
| IPN 502 | Backend 8080 dừng/sai port, upstream lỗi; relay không được ACK 00 |
| Cloudflare 1033/502 | Tunnel/relay đã dừng; kiểm tra log, outbound tunnel; thử HTTP/2 |
| IPN 97 khi VNPAY gọi | Sai merchant/Secret, query bị thay đổi hoặc field lặp; kiểm tra cấu hình portal |
| Return yêu cầu đăng nhập | Origin khác hoặc JWT hết hạn; đăng nhập lại để đọc trạng thái, không dùng Return để xác nhận |
| Return còn PENDING | IPN chưa đăng ký/không tới; kiểm tra portal và `Test-VnpayPublicUrls.ps1` |
| Có tiền thành công nhưng chưa có vé | Kiểm tra cờ đối soát/hết hạn; không đặt lại/cộng tiền thủ công |

Không log query đầy đủ vì có chữ ký và thông tin giao dịch. Cổng callback không log request. Mã ACK 00/02 chỉ phản ánh đã ghi nhận kết quả IPN, không đồng nghĩa mọi đơn đều PAID; xem trạng thái và cờ đối soát.

## 9. Dùng URL triển khai thực tế

Không cần relay nếu backend/frontend đã có HTTPS ổn định:

```dotenv
VNPAY_RETURN_URL=https://tickets.example.com/payment/return
VNPAY_IPN_URL=https://api.example.com/api/v1/payments/vnpay/ipn
APP_FRONTEND_URL=https://tickets.example.com
APP_CORS_ALLOWED_ORIGINS=https://tickets.example.com
```

Ví dụ domain ở trên là placeholder. Frontend build với `VITE_API_BASE_URL=https://api.example.com/api/v1` hoặc dùng reverse proxy `/api/v1` cùng domain. Web server cần SPA fallback cho `/payment/return`; API/IPN phải chuyển tới Spring, không trả `index.html`. Điền khóa ở environment/secret store của backend; giữ `VNPAY_PAYMENT_URL` ở Sandbox trong giai đoạn này. Nếu sau proxy, chỉ tin forwarded headers từ proxy được cấu hình rõ, không nhận X-Forwarded-For tùy ý từ Internet. Đăng ký URL mới với VNPAY.

## 10. Kiểm thử tự động và dừng tunnel

```powershell
node --test scripts/vnpay-callback-server.test.mjs
.\scripts\Test-P7.ps1 -JavaHome 'C:\Program Files\Java\jdk-21.0.12' -Offline
```

Test-P7 gồm relay tests, PostgreSQL integration, frontend unit/build và Edge E2E. Merchant/test email/database tách biệt; signed IPN được mô phỏng độc lập, không thực hiện giao dịch gateway thật. Chi tiết transaction tại [payment-api.md](payment-api.md).

Nếu chạy relay/tunnel trong terminal, dùng Ctrl+C ở hai terminal. Với process nền do lượt này tạo, đọc PID trong `.local/vnpay-tunnel/relay.pid`, `tunnel.pid`, đối chiếu process trước khi dừng (PID có thể đã tái sử dụng):

```powershell
$relayProcessId = [int](Get-Content .local/vnpay-tunnel/relay.pid)
Get-CimInstance Win32_Process -Filter "ProcessId = $relayProcessId" | Select-Object ProcessId, Name, CommandLine
# Chỉ khi đúng node đang chạy scripts/vnpay-callback-server.mjs:
Stop-Process -Id $relayProcessId
$tunnelProcessId = [int](Get-Content .local/vnpay-tunnel/tunnel.pid)
Get-CimInstance Win32_Process -Filter "ProcessId = $tunnelProcessId" | Select-Object ProcessId, Name, CommandLine
# Chỉ khi đúng cloudflared của dự án đang chuyển tới cổng 8787:
Stop-Process -Id $tunnelProcessId
```

Tạo tunnel mới thì cập nhật cả `.env` và portal, restart backend, kiểm tra lại HTTPS rồi mới tạo thanh toán.

## Nguồn chính thức

- [VNPAY PAY/IPN 2.1.0](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).
- [VNPAY HMAC và IPN](https://sandbox.vnpayment.vn/apis/docs/chuyen-doi-thuat-toan/changeTypeHash.html).
- [Cloudflare Quick Tunnels](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/).
- [Cloudflared releases](https://github.com/cloudflare/cloudflared/releases).
