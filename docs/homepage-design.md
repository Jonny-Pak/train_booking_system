# Homepage theo mẫu tham khảo

Homepage được chỉnh theo hai ảnh người dùng cung cấp: header xanh, banner lớn, form tìm kiếm trắng, nút vàng, tab phương tiện và các thẻ tuyến đường bên dưới.

## Các file chính

- frontend/src/pages/SearchPage.jsx: bố cục và tương tác tìm chuyến, đổi banner theo tab, chọn tuyến gợi ý.
- frontend/src/home.css: giao diện theo mẫu và responsive; tách khỏi CSS các trang nghiệp vụ.
- frontend/src/App.jsx: header xanh, thương hiệu Lên Đường, đơn hàng, hướng dẫn và đăng nhập.
- frontend/public/images/coach-hero.png: banner xe khách.
- frontend/public/images/railway-hero.png: banner tàu hỏa.

Giữ API hiện có. Xe khách/tàu hỏa/tất cả chuyến đều tìm kiếm được. Không thêm các tab đặt máy bay, thuê xe, khứ hồi hoặc chính sách giảm giá khi backend chưa hỗ trợ. Tuyến gợi ý lấy từ kết quả API, không phải số liệu thống kê tuyến phổ biến.

## Banner

Hai ảnh mới được tạo bằng công cụ image_gen tích hợp, chế độ generate. Không dùng ảnh chụp màn hình làm background trang, không chỉnh ảnh tham khảo hoặc lấy logo/đối tác của trang mẫu. Ảnh cuối được sao chép vào frontend/public/images để ứng dụng và production build dùng được.

Prompt tàu hỏa:

> Create a photorealistic cinematic panoramic website hero background, extra wide landscape 3:1 aspect ratio. Drone aerial photograph of a long modern Vietnamese blue and white passenger train gracefully curving through lush emerald green forest and rolling hills in Vietnam. Railway curves across the image from bottom left toward upper right, train mostly along lower and right portions. Beautiful natural daylight, vivid realistic foliage, richly textured, travel editorial photography. Large open green canopy area in central upper portion where a white heading will be overlaid in HTML. No text, no logos, no watermarks, no border. This will be a background for a real train booking website.

Prompt xe khách:

> Create an extra wide panoramic 3:1 travel website hero illustration background. Premium polished 3D advertising art in vivid electric blue, cyan and sunny yellow. A modern yellow intercity coach bus on a smooth curving highway in the bottom right of image, alongside a smaller streamlined red white train, lush distant Vietnamese hills and blue sky, soft clouds and bright sunshine. Beautiful professional travel booking campaign composition, cinematic realistic 3D vehicle rendering, reflective highlights. Leave the central 60 percent uncluttered blue sky so website headline and a large white search form can be overlaid in HTML. Do not put any text, numbers, discounts, logos, watermarks, smartphones or interface elements in image. Landscape extra wide, no border.

## Kiểm thử

Chạy scripts/Test-P5.ps1 để kiểm tra lint, unit tests, build và luồng đặt/hủy/tranh ghế với backend/PostgreSQL thật. Browser tests đã đổi nhãn tìm kiếm thành Tìm kiếm và bổ sung ảnh train-desktop.png.

Lần nghiệm thu cuối: .local/p5-20261025-183138-1926013b/verification.json, 25/10/2026 lúc 11:34:10 UTC. Lint và production build đạt; 17 unit tests frontend, 104 tests backend và 4 browser E2E tests đều đạt. Đã xem home-desktop.png, home-mobile.png và train-desktop.png trong thư mục screenshots của lượt kiểm chứng. Các tiến trình app/database tạm đã dừng.
