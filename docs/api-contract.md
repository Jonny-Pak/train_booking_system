# Contract API P1

## Endpoint hiện tại

| Method | Path | Quyền | Response |
|---|---|---|---|
| GET | `/actuator/health` | Public | 200 `{"status":"UP"}` khi các health indicator hoạt động |

Database không sẵn sàng làm health không UP. Health không công khai component/details. Actuator khác không được expose. Chưa có REST endpoint nghiệp vụ trong P1; security mặc định chặn request khác (401 anonymous, 403 authenticated).

## Lỗi JSON

```json
{
  "timestamp": "2026-10-25T00:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "fieldErrors": {"name": "must not be blank"}
}
```

Ví dụ chỉ mô tả cấu trúc; P1 chưa có input nghiệp vụ `name`. Lỗi không có field validation trả `fieldErrors: {}`. Không trả stacktrace, SQL hoặc thông báo exception nội bộ. Security filter dùng cùng cấu trúc.

Các endpoint `/api/v1/auth/*`, trips, bookings, payments, tickets và admin được bổ sung/kiểm thử trong các pha tương ứng. Chưa tuyên bố chúng tồn tại ở P1.
