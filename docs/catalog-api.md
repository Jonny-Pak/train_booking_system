# Catalog và Trip API P3

Base path `/api/v1`. ADMIN cần JWT Bearer theo auth contract. Các ví dụ ID/timestamp bên dưới là minh họa; dùng ID thực và thời điểm tương lai khi chạy.

## Endpoint

| Method | Path | Quyền | Kết quả |
|---|---|---|---|
| GET | `/trips` | Public | Page chuyến đang mở bán |
| GET | `/trips/locations` | Public | Array cặp điểm đi/đến có chuyến tương lai đang hoạt động |
| GET | `/trips/{id}` | Public | Chi tiết chuyến đang mở bán |
| GET | `/trips/{id}/seats` | Public | Array ghế và trạng thái |
| GET/POST | `/admin/routes` | ADMIN | Page tuyến / tạo tuyến |
| GET/PUT/DELETE | `/admin/routes/{id}` | ADMIN | Chi tiết / thay thế / xóa |
| GET/POST | `/admin/vehicles` | ADMIN | Page phương tiện / tạo phương tiện |
| GET/PUT/DELETE | `/admin/vehicles/{id}` | ADMIN | Chi tiết / thay thế / xóa |
| GET/POST | `/admin/vehicles/{vehicleId}/seats` | ADMIN | Page ghế / thêm ghế |
| POST | `/admin/vehicles/{vehicleId}/seat-layout` | ADMIN | Sinh sơ đồ chuẩn xe giường nằm 34 chỗ hoặc tàu 11 toa/434 chỗ |
| GET/PUT/DELETE | `/admin/vehicles/{vehicleId}/seats/{seatId}` | ADMIN | Ghế đúng phương tiện / thay thế / xóa |
| GET/POST | `/admin/trips` | ADMIN | Page chuyến mọi trạng thái / tạo chuyến |
| GET/PUT/DELETE | `/admin/trips/{id}` | ADMIN | Chi tiết quản trị / thay thế / xóa |

GET/PUT trả 200, POST trả 201, DELETE trả 204. PUT là thay thế đầy đủ DTO, không phải PATCH. CUSTOMER nhận 403 ở mọi admin endpoint; anonymous nhận 401. Search/detail/seats chỉ mở GET, không tạo booking.

## Body tuyến

```json
{"origin":"Hà Nội","destination":"Hải Phòng","active":true}
```

Điểm đi/đến bắt buộc, trim, tối đa 160 ký tự, phải khác nhau cả khi chỉ khác hoa/thường. Tuyến có trips không đổi endpoints và không xóa; có thể đổi active. Tắt active ẩn các chuyến khỏi public search/detail.

## Body phương tiện và ghế

Sơ đồ chuẩn có tọa độ toa/khoang/tầng, bảo vệ lịch sử và hướng dẫn Admin tại [seat-layouts.md](seat-layouts.md). Layout chuẩn khóa CRUD ghế riêng lẻ; CUSTOM cũ tiếp tục tương thích.

```json
{"vehicleCode":"BUS-01","vehicleType":"BUS","displayName":"Xe Hà Nội","active":true}
```

Code bắt buộc, tối đa 40, trim/uppercase, không trùng không phân biệt hoa/thường. Type BUS/TRAIN; tên tối đa 120. Có trips thì không đổi type. Không xóa phương tiện có trips hoặc seats; có thể ngừng active.

```json
{"seatCode":"A1","seatClass":"STANDARD"}
```

Ghế thuộc vehicleId trên path; không nhận vehicleId trong body. Code tối đa 20, class tối đa 40, đều bắt buộc và trim/uppercase. Code unique trong phương tiện, không phân biệt hoa/thường qua API. SeatId thuộc phương tiện khác trả 404. Phương tiện đã có booking history chặn mọi thêm/sửa/xóa ghế, kể cả booking đã hủy/hết hạn.

## Body chuyến

```json
{
  "routeId":1,
  "vehicleId":1,
  "departureAt":"2030-01-02T01:00:00Z",
  "arrivalAt":"2030-01-02T03:00:00Z",
  "basePrice":150000.00,
  "status":"SCHEDULED"
}
```

Mọi field bắt buộc. Timestamps ISO 8601 có Z/offset; response UTC. Giá không âm, tối đa 10 chữ số nguyên và 2 chữ số thập phân, lưu BigDecimal/NUMERIC(12,2). Không lấy giá từ endpoint booking vì P4 chưa có.

Chuyến mới chỉ SCHEDULED, chưa khởi hành, tuyến/phương tiện active và phương tiện có ghế. Arrival phải sau departure. Các chuyến cùng vehicle không chồng khoảng `[departureAt, arrivalAt)`; giờ đến bằng giờ đi tiếp theo được phép. CANCELLED không chiếm lịch.

PUT chặn khi đã có bất kỳ booking history nào. SCHEDULED chưa có booking được đổi lịch/giá/phương tiện hoặc chuyển CANCELLED/COMPLETED. COMPLETED chỉ hợp lệ khi cả giờ đến cũ và mới đã qua. CANCELLED/COMPLETED không sửa tiếp. DELETE chỉ khi chưa có booking; không cascade xóa lịch sử.

Trip response phẳng:

```json
{
  "id":1,"routeId":1,"origin":"Hà Nội","destination":"Hải Phòng",
  "vehicleId":1,"vehicleCode":"BUS-01","vehicleType":"BUS","vehicleName":"Xe Hà Nội",
  "departureAt":"2030-01-02T01:00:00Z","arrivalAt":"2030-01-02T03:00:00Z",
  "basePrice":150000.00,"status":"SCHEDULED"
}
```

## Tìm chuyến và phân trang

`GET /trips?origin=Hà Nội&destination=Hải Phòng&date=2030-01-02&vehicleType=BUS&page=0&size=20`

Các filter tùy chọn. Origin/destination khớp toàn bộ tên sau trim, không phân biệt hoa/thường, không phải substring hoặc tìm bỏ dấu. Date là ngày khởi hành tại `Asia/Ho_Chi_Minh`: gồm đầu ngày, không gồm đầu ngày tiếp. Không gửi date thì lấy các chuyến tương lai. Loại phương tiện BUS/TRAIN.

Public chỉ trả SCHEDULED, departure sau giờ server, tuyến và phương tiện active. Detail/seats của chuyến không mở bán trả 404; admin vẫn xem được. Admin trips có các filter `status`, `routeId`, `vehicleId`.

Mọi danh sách quản trị và tìm chuyến có `page` bắt đầu 0, size mặc định 20, giới hạn 1–100. Trip sort departureAt/id; tuyến/phương tiện sort id; ghế sort seatCode/id. Không hỗ trợ sort tùy ý.

```json
{"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0}
```

## Trạng thái ghế public

```json
[{"id":1,"seatCode":"A1","seatClass":"STANDARD","status":"AVAILABLE"}]
```

- BOOKED: booking seat active, booking PAID.
- HELD: booking seat active, booking PENDING còn hạn.
- AVAILABLE: còn lại, gồm hold hết hạn nhưng scheduler chưa dọn.

Không lộ chủ đơn, bookingId hoặc email. GET không giải phóng ghế, không phải cam kết giữ ghế; P4 phải kiểm tra lại và giải phóng hold hết hạn trong transaction trước khi đặt.

## Lỗi

Dùng cấu trúc ApiError hiện có. 400 cho body/query/date/enum/ID/phân trang sai; 404 cho tài nguyên không tồn tại hoặc không công khai; 409 cho trùng mã, lịch chồng, catalog inactive, phương tiện chưa có ghế hoặc vi phạm bảo toàn lịch sử. Không lộ SQL/stacktrace. Một số code nghiệp vụ:

`INVALID_ROUTE`, `INVALID_TRIP_TIME`, `TRIP_IN_PAST`, `VEHICLE_CODE_EXISTS`, `SEAT_CODE_EXISTS`, `VEHICLE_SCHEDULE_CONFLICT`, `ROUTE_IN_USE`, `VEHICLE_IN_USE`, `TRIP_HAS_BOOKINGS`, `SEATS_IN_USE`, `CATALOG_INACTIVE`, `VEHICLE_HAS_NO_SEATS`, `TRIP_TERMINAL`, `TRIP_NOT_FINISHED`.

Trùng/FK conflict không được chặn sớm ở service vẫn trả 409 `DATA_CONFLICT`, không tiết lộ constraint nội bộ.
