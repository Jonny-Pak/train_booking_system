import { Link } from "react-router-dom";
import { ArrowUpRight, ShieldCheck, Ticket } from "lucide-react";
import BrandLogo from "../site/BrandLogo";

export default function SiteFooter() {
  return (
    <footer className="site-footer site-footer-dark">
      <div className="footer-inner">
        <div className="footer-invitation">
          <div>
            <p className="footer-eyebrow">VEXESIEURE · CÙNG BẠN LÊN ĐƯỜNG</p>
            <h2>Hành trình mới, trải nghiệm mới.</h2>
          </div>
          <Link className="footer-search" to="/">
            Tìm chuyến phù hợp <ArrowUpRight size={20} />
          </Link>
        </div>
        <div className="footer-columns">
          <div className="footer-about">
            <Link
              to="/"
              className="footer-brand"
              aria-label="Vexesieure — Trang chủ"
            >
              <BrandLogo footer light />
            </Link>
            <p>
              Kết nối những hành trình bằng một trải nghiệm đặt vé đơn giản. Tìm
              chuyến tàu, xe khách và chọn chỗ phù hợp với bạn.
            </p>
            <span className="footer-tagline">
              <Ticket size={16} /> Mỗi hành trình, một khởi đầu mới.
            </span>
          </div>
          <nav className="footer-links" aria-label="Khám phá ở chân trang">
            <h3>Khám phá</h3>
            <Link to="/">Tìm chuyến đi</Link>
            <Link to="/?vehicleType=BUS">Vé xe khách</Link>
            <Link to="/?vehicleType=TRAIN">Vé tàu hỏa</Link>
            <Link to="/bookings">Đơn hàng của tôi</Link>
          </nav>
          <nav className="footer-links" aria-label="Hỗ trợ ở chân trang">
            <h3>Đồng hành cùng bạn</h3>
            <Link to="/booking-guide">Hướng dẫn đặt vé</Link>
            <Link to="/bookings">Quản lý vé điện tử</Link>
            <Link to="/login">Đăng nhập tài khoản</Link>
            <Link to="/register">Tạo tài khoản</Link>
          </nav>
        </div>
        <div className="footer-bottom">
          <p>
            © {new Date().getFullYear()} Vexesieure. Đồ án hệ thống bán vé tàu
            xe trực tuyến.
          </p>
          <span>
            <ShieldCheck size={16} /> Thanh toán thử nghiệm VNPAY Sandbox
          </span>
        </div>
      </div>
    </footer>
  );
}
