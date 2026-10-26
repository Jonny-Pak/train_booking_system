# Sơ đồ xe giường nằm 34 chỗ và tàu 11 toa

## Cấu hình đã xác nhận

Xe khách là **xe giường nằm 34 chỗ**, 2 tầng, mỗi tầng 17 giường, 2 dãy và 1 lối đi. Tầng dưới giường 1–17, tầng trên giường 18–34; mỗi tầng có một hàng cuối chỉ một giường. Vị trí tài xế không bán vé. Mã A1–A34 và ID cũ được giữ nguyên.

Tàu có **434 chỗ trong 11 toa**:

| Toa | Loại | Chỗ/toa | Khoang | Tầng |
|---|---|---:|---:|---:|
| 1–2 | Ghế ngồi mềm | 56 | Không chia khoang ngủ | 1 |
| 3–6 | Giường nằm khoang 4 | 28 | 7 | 2 |
| 7–11 | Giường nằm khoang 6 | 42 | 7 | 3 |

Người dùng đã xác nhận **28**, thay cho con số 26 ban đầu, vì 7 × 4 = 28 và ảnh đánh số đến 28. Mỗi tầng trong một khoang có hai giường. Mã chỗ tàu dạng `T03-28`, không trùng giữa các toa. Số chỗ bắt đầu lại từ 1 ở mỗi toa; không bổ sung ghế phụ GP vì không thuộc yêu cầu đã chốt.

Toa ghế ngồi theo hình mẫu: bốn vị trí mỗi cột, đảo chiều đánh số ở cột kế tiếp; có lối đi giữa hai dãy. Toa giường hiển thị tầng cao ở trên, tầng 1 dưới cùng. Tầng/khoang lấy từ backend, không suy đoán từ trạng thái hoặc dữ liệu giả.

## PostgreSQL và API

Migration mới **V7__seat_layout_positions.sql**, giữ chín bảng và V1–V6 nguyên trạng:

- `vehicles.seat_layout`: CUSTOM (dữ liệu cũ), BUS_SLEEPER_34, TRAIN_11; CHECK khớp loại phương tiện.
- `seats`: thêm `coach_number`, `compartment_number`, `deck_number`, `position_number` nullable để tương thích dữ liệu cũ.
- Coach 0 dùng cho xe; coach 1–11 cho tàu. Null toàn bộ vị trí là ghế legacy.
- CHECK giới hạn số chỗ, khoang, tầng và liên hệ số giường với khoang/tầng. Unique partial index trên `(vehicle_id, coach_number, position_number)` khi có tọa độ.
- Không sửa mã/ID ghế hiện có hoặc tự chuyển đổi ghế đã gắn với booking bằng migration.

Endpoint mới:

```http
POST /api/v1/admin/vehicles/{vehicleId}/seat-layout
Authorization: Bearer <admin-token>
```

Không nhận body. BUS tạo 34 giường; TRAIN tạo 434 chỗ. Response là VehicleResponse có `seatLayout`. Chỉ ADMIN, anonymous 401, CUSTOMER 403.

Service lấy advisory lock catalog, khóa các Trip liên quan trước khi kiểm tra lịch sử và đổi ghế. Nếu đã đúng layout, trả lại và **giữ nguyên các ID ghế**, kể cả đã có booking. Nếu đang CUSTOM và có bất kỳ lịch sử đặt vé, trả 409 SEATS_IN_USE. Nếu chưa có lịch sử, thay danh sách ghế trong một transaction. Mọi lỗi rollback.

Các API thêm/sửa/xóa ghế đơn lẻ trả 409 STANDARD_LAYOUT_LOCKED với layout chuẩn để không làm sai sức chứa/vị trí. Không cho đổi BUS↔TRAIN trên phương tiện đã có layout chuẩn. Muốn cấu hình khác, tạo phương tiện mới; không sửa SQL để vượt qua bảo vệ lịch sử.

SeatResponse, public TripSeatResponse, BookingSeatResponse và TicketResponse bổ sung bốn trường vị trí. Booking vẫn gửi `tripId`, danh sách `seatIds`; một đơn tối đa 10 chỗ có thể ở nhiều toa. Giá vẫn theo `trip.basePrice` cho mỗi chỗ, không tự thêm giá khác theo toa/tầng. Thanh toán/IPN và unique active seat giữ nguyên.

## Giao diện

- Customer chọn toa 1–11, xem loại/sức chứa, chọn ghế/giường; đổi toa vẫn giữ lựa chọn, có số chỗ đã chọn ở mỗi tab.
- Tab toa hỗ trợ chuột và phím trái/phải. Ghế có aria-label gồm toa/khoang/tầng/trạng thái.
- Ghế AVAILABLE mới được chọn. HELD/BOOKED bị khóa; chọn trên màn hình chưa đồng nghĩa giữ chỗ.
- Mobile cuộn ngang bên trong sơ đồ toa và thanh chọn toa, không làm tràn ngang toàn trang.
- Booking/lịch sử/vé hiển thị vị trí đọc được, ví dụ “Giường 28 · Toa 3 · Khoang 7 · Tầng 2”.
- Ghế legacy vẫn dùng danh sách cũ và mã cũ, để đọc lại lịch sử chính xác.

Admin: Phương tiện → Quản lý ghế → **Tạo sơ đồ chuẩn** → xác nhận. Trước khi tạo chuyến mới, tạo sơ đồ này cho phương tiện. API tạo phương tiện/CRUD ghế CUSTOM cũ vẫn tương thích; không tự thêm hàng trăm chỗ khi một client cũ tạo phương tiện.

## Dữ liệu mẫu và nâng cấp đang dùng

`database/03_sample_trips.sql` sau V9 tạo mới bốn xe giường nằm 34 chỗ và hai tàu 434 chỗ, tổng 1004 chỗ, 70 chuyến trong bảy ngày tới. Không tự đổi ghế của phương tiện đã tồn tại. `02_demo_seed.sql` vẫn giữ dataset nhỏ legacy để kiểm thử hồi quy.

Với dữ liệu đã có: nâng cấp trực tiếp phương tiện chưa có booking qua API mới. Phương tiện có lịch sử phải giữ nguyên ghế, dùng phương tiện mới và chuyển các chuyến chưa có booking sang phương tiện mới; chuyến có booking được giữ làm lịch sử, có thể tạo chuyến thay thế riêng. Chỉ ngừng hoạt động phương tiện cũ sau khi xác nhận không còn hold đang hiệu lực. Không xóa đơn/vé cũ.

## Kiểm chứng

```powershell
.\scripts\Test-P7.ps1 -JavaHome 'C:\Program Files\Java\jdk-21.0.12' -Offline
```

Integration tests kiểm tra 34/434 chỗ, toa 1–11, tầng/khoang cuối cùng, idempotency giữ ID, chặn thay thế ghế có lịch sử, quyền API và đặt/thanhtoán/vé xuyên toa. Seed được chạy hai lần trên schema PostgreSQL riêng và kiểm tra 1004 chỗ/70 chuyến. Browser tests kiểm tra tạo sơ đồ Admin, xe ghế 1/34, tàu ghế 56/toa 1 và giường cuối toa 3/7/11, chọn nhiều toa, IPN mô phỏng và vị trí trên vé, desktop/mobile. Bộ hồi quy Customer/Admin/payment cũ vẫn được chạy.

Các kiểm tra ký IPN dùng merchant giả lập, không gửi thanh toán thật.

V8 bổ sung 18 ghế (17–34) cho từng BUS_16 và đổi thành BUS_34. Giữ nguyên mọi ID ghế 1–16, booking và vé; không sửa migration V7 đã áp dụng. Tàu không thay đổi. Mọi lỗi dữ liệu không nhất quán làm migration rollback.

V9 sửa loại xe thành BUS_SLEEPER_34, seat_class BUS_SLEEPER và chia deck_number: giường 1–17 tầng 1, 18–34 tầng 2. Không thêm/xóa chỗ, không đổi ID/mã/số giường; tàu và giá đã đặt giữ nguyên.

### Kết quả ngày 26/10/2026

- Lượt `.local/p7-20261026-014026-69e5cbf1/verification.json`, log `.local/seat-layout-acceptance-final.log`: **151 backend tests, 25 frontend unit tests, 16 browser E2E, 4 callback relay tests đạt**; lint/build đạt.
- Hai lần khởi động database kiểm thử: 7 migration hợp lệ, booking/payment/ticket tồn tại sau restart. Cluster/server kiểm thử đã dừng.
- Đã xem ảnh `bus-16-desktop.png`, `bus-16-mobile.png`, `train-seated-56.png`, `train-sleeper-4.png`, `train-sleeper-6.png`, `train-mobile.png`; vị trí/đánh số theo mẫu và không tràn ngang trang.
- Database đang dùng đã được sao lưu tại `.local/seat-layout-runtime/before-v7.dump` trước khi nâng V7. Backend chính đã khởi động lại và health UP.
- Năm phương tiện mẫu không có lịch sử nâng cấp trực tiếp. `SAMPLE-BUS-01` có một đơn cũ nên giữ nguyên 20 ghế và đơn; tạo `SAMPLE-BUS-01-L16` với 16 ghế. Chuyển 13 chuyến chưa có booking sang xe mới, tạo một chuyến thay thế cho chuyến có lịch sử. Ngừng mở bán xe cũ trong transaction khóa Trip và kiểm tra không còn hold hiệu lực.
- Kiểm tra public API sau cập nhật: **70 chuyến**, bốn xe active ×16 ghế, hai tàu active ×434 chỗ. Kết quả `.local/seat-layout-runtime/public-api-check.json`. Đơn cũ được giữ, không xóa booking/booking seat/ticket.
- Chưa commit/push; các thay đổi P7/VNPAY có sẵn trong working tree được giữ nguyên.

### Điều chỉnh xe khách lên 34 chỗ — 26/10/2026

Theo đính chính của người dùng, cấu hình hiện tại là **BUS_34**. V8 chỉ thêm ghế 17–34 và đổi tên layout cho các xe BUS_16, giữ nguyên ghế 1–16 cùng các booking/vé. Tàu vẫn 11 toa/434 chỗ. Các kết quả 16 ghế ở mục trước là lịch sử của lượt triển khai V7.

- Lượt `.local/p7-20261026-020029-3851e36e/verification.json`, log `.local/bus-34-acceptance.log`: **152 backend tests, 25 frontend tests, 16 browser E2E, 4 relay tests đạt**, lint/build đạt. Bổ sung test upgrade V7→V8 có booking ở ghế 16 để xác minh ID không đổi.
- Seed mới kiểm chứng 4×34 + 2×434 = **1004 chỗ**, 70 chuyến, chạy lại không tạo trùng.
- Đã xem `bus-34-desktop.png` và `bus-34-mobile.png`: sơ đồ 2+2 có lối đi giữa, đủ ghế 1–34, vị trí tài xế không bán vé; mobile không tràn ngang.
- Database thật được sao lưu tại `.local/bus-34-runtime/before-v8.dump`; đã áp dụng V8 và restart backend. Bản chạy dùng JAR trong `.local/bus-34-runtime/` để không khóa `backend/target` khi build trên Windows.
- Đối chiếu API với snapshot trước V8: giữ nguyên **932 ID/vị trí chỗ cũ** của đội xe active, bốn xe từ 16→34 và hai tàu giữ 434. Vẫn 70 chuyến public. Bằng chứng `.local/bus-34-runtime/verification.json`.
- Backend health UP, server/database kiểm thử đã dừng; chưa commit/push.

### Đính chính xe giường nằm — 26/10/2026

Người dùng xác nhận xe là **giường nằm 34 chỗ, 2 tầng ×17 giường, 2 dãy và 1 lối đi**. Cấu hình hiện tại là `BUS_SLEEPER_34`, không phải ghế ngồi. Tầng dưới giường 1–17, tầng trên giường 18–34; mỗi tầng có hàng cuối một giường. `BUS_SLEEPER` được hiển thị thành “Giường nằm xe khách”.

- V9 chuyển loại chỗ và tầng, không xóa/thêm chỗ hoặc đổi ID/mã A1–A34, không sửa V1–V8 đã áp dụng. Giá snapshot, booking, ticket và cấu hình tàu giữ nguyên.
- Customer có sơ đồ hai tầng, biểu tượng giường, nhãn tầng, giá mỗi giường và nút giữ giường. Đơn/vé hiển thị đúng số giường/tầng. Mỗi giường là một Seat ID được giữ chỗ độc lập như trước.
- Lượt `.local/p7-20261026-021219-c7c394a7/verification.json`, log `.local/bus-sleeper-acceptance.log`: **152 backend tests, 25 frontend tests, 16 E2E và 4 relay tests đạt**, lint/build đạt. E2E chọn giường 1/tầng 1 và 34/tầng 2, kiểm tra mỗi tầng 17 chỗ và metadata đơn đúng.
- Đã xem `bus-sleeper-34-desktop.png`, `bus-sleeper-34-mobile.png`; mobile hiển thị hai tầng không tràn ngang.
- Backup database trước V9: `.local/bus-sleeper-runtime/before-v9.dump`. Backend đã chạy V9, health UP. Đối chiếu snapshot: **1004 ID/mã chỗ hiện có được giữ**, bốn xe active có hai tầng ×17 giường, hai tàu giữ nguyên toàn bộ metadata và 434 chỗ/tàu, vẫn 70 chuyến public. Bằng chứng `.local/bus-sleeper-runtime/verification.json`.
- Đã dừng server/database kiểm thử; backend chính tiếp tục chạy. Chưa commit/push.
