import { Link, NavLink, Route, Routes, useLocation } from "react-router-dom";
import { ArrowUpRight, Globe, LogOut, Ticket, TrainFront } from "lucide-react";
import { useAuth } from "./auth/AuthContext";
import RequireAuth from "./auth/RequireAuth";
import SearchPage from "./pages/SearchPage";
import TripPage from "./pages/TripPage";
import AuthPage from "./pages/AuthPage";
import BookingsPage from "./pages/BookingsPage";
import BookingPage from "./pages/BookingPage";
import { EmptyState } from "./components/Feedback";

export default function App() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const isAuth =
    location.pathname === "/login" || location.pathname === "/register";
  return (
    <div className="app">
      <a className="skip-link" href="#main">
        Đến nội dung chính
      </a>
      <header className="site-header">
        <Link to="/" className="brand" aria-label="Lên Đường — Trang chủ">
          <span>
            <TrainFront size={23} />
          </span>
          lên đường<span className="brand-dot">.</span>
        </Link>
        <p className="brand-promise">
          Đặt vé tàu xe trực tuyến
          <br />
          Chọn chuyến dễ dàng, chủ động hành trình
        </p>
        <nav aria-label="Điều hướng chính">
          <NavLink to="/" end>
            Khám phá
          </NavLink>
          <NavLink to="/bookings">
            <Ticket size={16} /> Đơn hàng của tôi
          </NavLink>
          <a className="header-guide" href="/#booking-guide">
            Hướng dẫn đặt vé
          </a>
        </nav>
        <span className="header-language">
          <Globe size={17} /> Tiếng Việt
        </span>
        <div className="account-nav">
          {user ? (
            <>
              <span className="user-name" title={user.fullName}>
                Chào, {user.fullName}
              </span>
              <button
                className="icon-button"
                onClick={logout}
                aria-label="Đăng xuất"
              >
                <LogOut size={19} />
              </button>
            </>
          ) : (
            <Link
              className="button secondary small"
              to="/login"
              state={{ from: location.pathname + location.search }}
            >
              Đăng nhập <ArrowUpRight size={16} />
            </Link>
          )}
        </div>
      </header>
      <main
        id="main"
        className={
          isAuth
            ? "main home-main"
            : location.pathname === "/"
              ? "main home-main"
              : "main"
        }
      >
        <Routes>
          <Route path="/" element={<SearchPage />} />
          <Route
            path="/trips/:id"
            element={<TripPage key={location.pathname} />}
          />
          <Route
            path="/login"
            element={
              <>
                <div inert>
                  <SearchPage />
                </div>
                <AuthPage key="login" />
              </>
            }
          />
          <Route
            path="/register"
            element={
              <>
                <div inert>
                  <SearchPage />
                </div>
                <AuthPage key="register" register />
              </>
            }
          />
          <Route
            path="/bookings"
            element={
              <RequireAuth>
                <BookingsPage />
              </RequireAuth>
            }
          />
          <Route
            path="/bookings/:id"
            element={
              <RequireAuth>
                <BookingPage key={location.pathname} />
              </RequireAuth>
            }
          />
          <Route
            path="*"
            element={
              <EmptyState title="Bạn vừa đi lạc một chút">
                <p>Trang này không tồn tại.</p>
                <Link className="button" to="/">
                  Về trang tìm chuyến
                </Link>
              </EmptyState>
            }
          />
        </Routes>
      </main>
      <footer className="site-footer">
        <Link to="/" className="footer-brand">
          lên đường.
        </Link>
        <p>Mỗi hành trình, một khởi đầu mới.</p>
        <span>Đồ án hệ thống đặt vé tàu xe · 2026</span>
      </footer>
    </div>
  );
}
