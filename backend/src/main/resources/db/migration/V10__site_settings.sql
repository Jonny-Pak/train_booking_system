CREATE TABLE site_settings (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    guide_title VARCHAR(160) NOT NULL,
    guide_html TEXT NOT NULL CHECK (length(guide_html) <= 100000),
    logo_png BYTEA CHECK (octet_length(logo_png) <= 4194304),
    revision BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO site_settings (id, guide_title, guide_html) VALUES (1, 'Hướng dẫn đặt vé tàu xe', $guide$
<h2>Chủ động hành trình cùng Vexesieure</h2>
<p>Từ tìm chuyến đến nhận vé điện tử, hãy làm theo các bước dưới đây. Chuẩn bị email có thể nhận thư, thông tin hành khách và phương thức thanh toán trước khi đặt vé.</p>
<h2>1. Tạo tài khoản và xác nhận email</h2>
<ol><li>Chọn <strong>Đăng ký</strong>, nhập họ tên, số điện thoại, email, mật khẩu và xác nhận mật khẩu. Đọc và tích chấp nhận điều khoản.</li><li>Mở email xác nhận và nhấn vào liên kết trong thời hạn ghi trong thư. Không chia sẻ liên kết này với người khác.</li><li>Quay lại trang đăng nhập bằng email và mật khẩu. Nếu chưa nhận thư, kiểm tra mục Spam hoặc chọn <strong>Gửi lại email xác nhận</strong>. Liên kết hết hạn cần được gửi lại.</li></ol>
<h2>2. Tìm chuyến phù hợp</h2>
<ol><li>Chọn <strong>Xe khách</strong> hoặc <strong>Tàu hỏa</strong>.</li><li>Chọn điểm xuất phát, nơi đến từ danh sách gợi ý và ngày khởi hành. Để trống ngày nếu muốn xem các chuyến sắp khởi hành.</li><li>Nhấn <strong>Tìm kiếm</strong>. Kiểm tra giờ khởi hành, giờ đến, giá và số chỗ trống trước khi mở chi tiết chuyến.</li></ol>
<p>Nếu không có kết quả, thử ngày khác hoặc tuyến khác. Hệ thống chỉ hiển thị chuyến đang mở bán và còn trong thời gian đặt vé.</p>
<h2>3. Chọn ghế hoặc giường và giữ chỗ</h2>
<ul><li><strong>Xe khách giường nằm:</strong> 34 giường, hai tầng, mỗi tầng 17 giường. Kiểm tra số giường và tầng trước khi chọn.</li><li><strong>Tàu hỏa:</strong> 11 toa. Toa 1–2 có 56 ghế ngồi/toa; toa 3–6 có 7 khoang, 4 giường/khoang, 2 tầng; toa 7–11 có 7 khoang, 6 giường/khoang, 3 tầng.</li><li>Chỉ chọn chỗ còn trống theo chú thích màu trên sơ đồ. Có thể chuyển toa để xem và chọn chỗ khác.</li><li>Kiểm tra danh sách chỗ và tổng tiền rồi nhấn nút giữ chỗ. Cần đăng nhập để hoàn tất bước này.</li></ul>
<p>Chỗ trống có thể thay đổi khi người khác đặt cùng lúc. Nếu có thông báo chỗ không còn trống, tải lại sơ đồ và chọn chỗ khác. Mỗi đơn chọn tối đa 10 chỗ.</p>
<h2>4. Thanh toán trong thời gian giữ chỗ</h2>
<ol><li>Tại chi tiết đơn, đối chiếu tuyến, ngày giờ, số ghế/giường và tổng tiền.</li><li>Theo dõi đồng hồ đếm ngược; thời gian giữ chỗ thông thường là 10 phút và có thể ngắn hơn khi chuyến sắp khởi hành. Thời hạn hiển thị trên đơn là thời hạn áp dụng.</li><li>Nhấn <strong>Thanh toán VNPAY Sandbox</strong> để chuyển sang cổng thanh toán. Làm theo hướng dẫn của cổng, sau đó quay lại hệ thống.</li><li>Chờ hệ thống xác minh giao dịch. Chỉ coi đơn đã thanh toán khi trạng thái trong hệ thống là <strong>Đã thanh toán</strong> và đã có vé điện tử.</li></ol>
<blockquote><p>Đây là môi trường thử nghiệm Sandbox, không dùng tiền thật. Không nhập thông tin thẻ thật. Không đóng trình duyệt trong lúc đang chuyển về hệ thống.</p></blockquote>
<h2>5. Nhận và lưu vé điện tử</h2>
<p>Mở <strong>Đơn hàng của tôi</strong>, chọn đơn đã thanh toán rồi nhấn <strong>Xem vé điện tử</strong>. Mỗi chỗ có mã vé và mã QR riêng. Kiểm tra hành trình, số chỗ và thời gian khởi hành; có thể in hoặc lưu PDF từ trang vé. Giữ mã vé và QR riêng tư.</p>
<h2>6. Hủy đơn chưa thanh toán hoặc xử lý hết hạn</h2>
<p>Trong chi tiết đơn đang giữ chỗ, chọn <strong>Hủy đơn đặt chỗ</strong> và xác nhận. Chỗ sẽ được mở lại. Đơn chưa thanh toán tự hết hạn khi hết thời gian giữ chỗ; hãy tạo đơn mới nếu vẫn muốn đi. Hệ thống hiện chưa hỗ trợ tự hủy hoặc hoàn tiền cho đơn đã thanh toán.</p>
<h2>Câu hỏi thường gặp</h2>
<h3>Nhấn thanh toán nhưng chưa chuyển trang?</h3><p>Đọc thông báo ngay dưới nút thanh toán. Nếu VNPAY chưa được cấu hình, liên hệ quản trị viên. Nếu mất kết nối, kiểm tra trạng thái đơn và lịch sử giao dịch trước khi thử lại, đồng thời theo dõi thời hạn giữ chỗ.</p>
<h3>Đã thanh toán ở cổng nhưng chưa thấy vé?</h3><p>Chờ hệ thống nhận xác nhận từ cổng và kiểm tra lại đơn. Trang quay về không tự xác nhận thanh toán. Nếu có thông báo cần đối soát, không thanh toán lại; cung cấp mã đặt chỗ và mã giao dịch cho quản trị viên để kiểm tra.</p>
<h3>Thanh toán báo thất bại hoặc hủy ở cổng?</h3><p>Kiểm tra trạng thái đơn. Có thể thử lại khi đơn vẫn đang giữ chỗ và không có giao dịch cần đối soát. Nếu đã hết hạn, chọn lại chuyến và tạo đơn mới.</p>
<h3>Cần chuẩn bị gì trước giờ khởi hành?</h3><p>Đọc thông tin chuyến và yêu cầu của đơn vị vận chuyển. Chuẩn bị vé điện tử cùng giấy tờ phù hợp; kiểm tra lại điểm đón hoặc ga đi và chủ động đến sớm.</p>
$guide$);
