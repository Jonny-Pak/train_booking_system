# Hướng dẫn đặt vé và quản lý logo

## Sử dụng

- Khách hàng: mở `/booking-guide` hoặc chọn **Hướng dẫn đặt vé** trên thanh điều hướng/trang chủ. Không cần đăng nhập.
- Admin: đăng nhập, mở `/admin/site` hoặc **Quản trị → Nội dung & logo**.
- Soạn hướng dẫn, chọn **Xem trước**, sau đó **Lưu hướng dẫn** để công khai. Có tiêu đề đoạn, đậm/nghiêng/gạch chân/gạch ngang, cỡ/màu chữ, danh sách, thụt lề danh sách, căn lề, hoàn tác/làm lại và toàn màn hình (Esc để đóng).
- Chọn logo PNG/JPG, kiểm tra trên nền xanh và trắng, rồi **Lưu logo**. **Khôi phục logo mặc định** trả lại SVG Vexesieure. Logo thay thế áp dụng đầu trang và chân trang; favicon vẫn dùng biểu tượng mặc định.
- Ảnh đầu vào tối đa 2 MiB, mỗi chiều 16–4096 px. Nên dùng ảnh ngang trong suốt. Backend xác minh định dạng thực tế và chuyển thành PNG; không chấp nhận SVG tải lên.
- Mỗi lần lưu tăng `revision`. Nếu hai Admin cùng sửa, phiên cũ nhận 409; sao chép nội dung đang soạn và tải lại trước khi sửa tiếp.

## API và lưu trữ

| Method | URL | Quyền | Nội dung |
| --- | --- | --- | --- |
| GET | `/api/v1/site/settings` | Public | `guideTitle`, `guideHtml`, `customLogo`, `revision`, `updatedAt` |
| GET | `/api/v1/site/logo?v=revision` | Public | PNG; 404 khi đang dùng logo SVG mặc định |
| PUT | `/api/v1/admin/site/guide` | ADMIN | JSON `{title, html, revision}` |
| PUT | `/api/v1/admin/site/logo` | ADMIN | JSON `{imageBase64, revision}`; base64 thuần, không kèm tiền tố data URL |
| DELETE | `/api/v1/admin/site/logo?revision=...` | ADMIN | Khôi phục logo mặc định |

Flyway **V10** thêm bảng cấu hình `site_settings` một dòng (id=1) và nội dung hướng dẫn ban đầu. Chín bảng nghiệp vụ vẫn giữ nguyên; tổng cộng có thêm một bảng cấu hình. HTML và logo BYTEA lưu trong PostgreSQL, tồn tại sau khởi động lại; không phụ thuộc thư mục upload trên máy. API cập nhật dùng transaction và điều kiện revision để tránh ghi đè. Không gọi API public để sửa nội dung.

HTML tối đa 100.000 ký tự. Backend dùng jsoup với danh sách thẻ/thuộc tính cho phép; chỉ giữ màu RGB/hex, cỡ chữ định sẵn và căn lề, loại script, sự kiện, ảnh, liên kết, CSS tùy ý. Frontend dùng DOMPurify trước khi hiển thị. Ảnh được đọc kích thước trước khi giải mã và mã hóa lại để bỏ metadata/nội dung đính kèm. GET dùng `Cache-Control: no-store`; logo có `nosniff`.

Tiptap tải riêng khi vào mục Admin này, tránh bắt khách hàng tải trình soạn thảo. Nội dung được lưu ngay khi bấm lưu; chưa có quy trình bản nháp/phê duyệt hoặc lịch sử phiên bản. Không tự thay đổi màu sắc của ảnh logo, vì vậy cần kiểm tra cả hai nền trước khi lưu.

## Chạy và kiểm thử trên Windows

Sau khi cập nhật mã nguồn, khởi động lại backend để Flyway tự áp dụng V10 (không sửa V1–V9). Frontend:

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

Kiểm thử đầy đủ với PostgreSQL tạm độc lập, không sử dụng dữ liệu thật:

```powershell
pwsh -NoProfile -File scripts/Test-P7.ps1 -JavaHome 'C:\Program Files\Java\jdk-21.0.12'
```

Lần đầu cần mạng để Maven tải jsoup; các lần sau có thể thêm `-Offline`. Bộ kiểm thử gồm quyền public/CUSTOMER/ADMIN, lọc XSS/CSS, dữ liệu rỗng/quá giới hạn, ảnh giả/quá kích thước, lưu/đọc/khôi phục logo, xung đột revision, soạn định dạng và đọc hướng dẫn ở desktop/mobile. Nội dung kiểm thử được khôi phục trong database tạm.

Tài liệu thư viện: [Tiptap React](https://tiptap.dev/docs/editor/getting-started/install/react), [jsoup safelist](https://jsoup.org/cookbook/cleaning-html/safelist-sanitizer).

## Kiểm chứng ngày 26/10/2026

`Test-P7.ps1 -Offline` đã qua: 157 kiểm thử backend, 25 kiểm thử frontend, 19 kiểm thử trình duyệt, 4 kiểm thử relay; lint/build thành công. Backend tạm khởi động hai lần với 10 migration, kiểm tra đơn/thanh toán/vé sau khởi động lại vẫn qua. Kiểm thử trình duyệt xác nhận định dạng đậm/màu/cỡ chữ được lưu và hiển thị ở trang khách, logo tồn tại sau tải lại, khôi phục mặc định thành công và lần lưu cũ nhận xung đột mà không làm mất bản mới.

Database local đã được sao lưu trong `.local/site-settings-runtime/before-v10.dump`, áp dụng V10 và chạy backend mới thành công. Không đưa bản sao lưu, thông tin đăng nhập hay `.env` vào Git.
