# Chọn nơi xuất phát và nơi đến

Homepage có dropdown gợi ý theo ảnh tham khảo của người dùng. Dữ liệu lấy từ tuyến có chuyến tương lai đang SCHEDULED, route/vehicle active trong database; không dựng danh sách thành phố giả và không dùng riêng trang kết quả đầu tiên. Vì chưa có thống kê mức độ phổ biến, nhãn là **Địa điểm có chuyến**.

## Luồng

1. Nhấn ô Điểm đi để mở danh sách; gõ có dấu/không dấu để lọc.
2. Chọn điểm đi bằng chuột hoặc ArrowDown/ArrowUp + Enter. Form chuyển focus sang Điểm đến và chỉ gợi ý điểm đến có tuyến tương ứng.
3. Chọn điểm đến, chọn ngày/phương tiện rồi nhấn Tìm kiếm. Tên địa điểm được chuẩn hóa về tên trong catalog khi có thể, giữ nguyên API tìm chuyến so khớp tên đầy đủ.
4. Nút đổi chiều hoán đổi hai trường. Lựa chọn được giữ trong URL tìm kiếm khi reload/back; thao tác chọn chưa tự gửi tìm chuyến.

Dropdown đóng khi Escape, rời focus hoặc nhấn bên ngoài. Có thông báo tải, rỗng và lỗi, kèm nút thử lại. Người dùng vẫn nhập tự do và tìm kiếm khi danh sách chưa tải được. Nếu không có route hoạt động với ngày/phương tiện đã chọn thì danh sách rỗng, không tự chèn dữ liệu demo.

Nếu chọn điểm đi mới không tương thích với điểm đến cũ, form xóa điểm đến để chọn lại. Các tuyến vẫn có hướng: đổi chiều không tự tạo tuyến chiều về khi database chưa có.

## API

```http
GET /api/v1/trips/locations?vehicleType=BUS&date=2026-11-01
```

Hai query params đều tùy chọn. Ngày theo Asia/Ho_Chi_Minh, cùng quy tắc tìm chuyến. Trả cặp tuyến distinct, không phụ thuộc phân trang kết quả chuyến:

```json
[
  {"origin":"Hà Nội","destination":"Hải Phòng"},
  {"origin":"Hồ Chí Minh","destination":"Phan Thiết"}
]
```

Endpoint public, chỉ trả tên điểm đi/đến. Không cần migration hoặc thay đổi chín bảng. Danh sách trả toàn bộ cặp tuyến đang có chuyến cho MVP; khi dữ liệu lớn cần bổ sung API autocomplete có giới hạn thay vì tải toàn bộ catalog.

## File chính

- Backend: `LocationRouteResponse`, `TripController`, `TripService`, `TripQueryRepository`.
- Frontend: `components/LocationPicker.jsx`, `lib/locations.js`, `pages/SearchPage.jsx`, `location.css`.
- Kiểm thử: `CatalogIntegrationTest`, `locations.test.js`, `e2e/locations.spec.js`.

Kiểm chứng đầy đủ bằng `scripts/Test-P6.ps1` với PostgreSQL/SMTP/backend/Vite/Edge tạm. Browser tests kiểm tra chọn tuyến, gõ không dấu, bàn phím, đổi chiều, đổi phương tiện, URL/reload, lỗi/thử lại và kích thước dropdown mobile.

Lượt nghiệm thu ngày 25/10/2026: `.local/p6-20261025-212938-b565c724/verification.json`, log `.local/locations-acceptance.log`. Đạt lint/build, 138 tests backend, 20 tests frontend và 11 browser tests. Đã xem `locations-desktop.png`, `locations-mobile.png` trong thư mục screenshots của lượt kiểm chứng; máy chủ kiểm thử tạm đã dừng. Backend chính cần khởi động lại để nạp endpoint mới.
