import { Link, NavLink, Route, Routes, useLocation } from "react-router-dom";
import { ArrowUpRight, Globe, LogOut, Ticket } from "lucide-react";
import { useAuth } from "./auth/AuthContext";
import RequireAuth from "./auth/RequireAuth";
import SearchPage from "./pages/SearchPage";
import TripPage from "./pages/TripPage";
import AuthPage from "./pages/AuthPage";
import VerifyEmailPage from "./pages/VerifyEmailPage";
import PaymentReturnPage from "./pages/PaymentReturnPage";
import TicketsPage from "./pages/TicketsPage";
import BookingsPage from "./pages/BookingsPage";
import BookingPage from "./pages/BookingPage";
import { EmptyState } from "./components/Feedback";
import AdminLayout from "./admin/AdminLayout";
import AdminDashboard from "./admin/AdminDashboard";
import AdminCatalog from "./admin/AdminCatalog";
import AdminBookings from "./admin/AdminBookings";
import AdminBookingDetail from "./admin/AdminBookingDetail";
import { lazy, Suspense } from "react";
import BookingGuidePage from "./pages/BookingGuidePage";
import BrandLogo from "./site/BrandLogo";
import SiteFooter from "./components/SiteFooter";

const AdminSiteSettings = lazy(() => import("./admin/AdminSiteSettings"));

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
        <Link to="/" className="brand" aria-label="Vexesieure — Trang chủ">
          <BrandLogo />
        </Link>
        <p className="brand-promise">
          Đặt vé tàu xe trực tuyến
          <br />
          Chọn chuyến dễ dàng, chủ động hành trình
        </p>
        <nav aria-label="Điều hướng chính">
          {user?.role === "ADMIN" && <NavLink to="/admin">Quản trị</NavLink>}
          <NavLink to="/" end>
            Khám phá
          </NavLink>
          <NavLink to="/bookings">
            <Ticket size={16} /> Đơn hàng của tôi
          </NavLink>
          <Link className="header-guide" to="/booking-guide">
            Hướng dẫn đặt vé
          </Link>
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
          <Route path="/admin" element={<AdminLayout />}>
            <Route index element={<AdminDashboard />} />
            <Route
              path="site"
              element={
                <Suspense fallback={<p>Đang tải trình soạn thảo…</p>}>
                  <AdminSiteSettings />
                </Suspense>
              }
            />
            <Route
              path="routes"
              element={<AdminCatalog key="routes" kind="routes" />}
            />
            <Route
              path="vehicles"
              element={<AdminCatalog key="vehicles" kind="vehicles" />}
            />
            <Route
              path="vehicles/:vehicleId/seats"
              element={<AdminCatalog key={location.pathname} kind="seats" />}
            />
            <Route
              path="trips"
              element={<AdminCatalog key="trips" kind="trips" />}
            />
            <Route path="bookings" element={<AdminBookings key="bookings" />} />
            <Route
              path="bookings/:id"
              element={<AdminBookingDetail key={location.pathname} />}
            />
            <Route
              path="reconciliation"
              element={<AdminBookings key="reconciliation" reconciliation />}
            />
          </Route>
          <Route
            path="/payment/return"
            element={<PaymentReturnPage key={location.search} />}
          />
          <Route
            path="/bookings/:id/tickets"
            element={
              <RequireAuth>
                <TicketsPage key={location.pathname} />
              </RequireAuth>
            }
          />
          <Route
            path="/verify-email"
            element={<VerifyEmailPage key={location.hash} />}
          />
          <Route path="/" element={<SearchPage />} />
          <Route path="/booking-guide" element={<BookingGuidePage />} />
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
      <SiteFooter />
    </div>
  );
}
