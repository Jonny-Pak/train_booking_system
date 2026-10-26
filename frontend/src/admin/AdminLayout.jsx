import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import RequireAuth from "../auth/RequireAuth";
import { EmptyState } from "../components/Feedback";

function AdminContent() {
  const { user } = useAuth();
  if (user?.role !== "ADMIN")
    return (
      <EmptyState title="Không có quyền quản trị">
        <p>Tài khoản này không được truy cập khu vực Admin.</p>
      </EmptyState>
    );
  return (
    <div className="admin-layout">
      <aside className="admin-sidebar">
        <h2>Quản trị</h2>
        <p>Vexesieure / Admin</p>
        <nav aria-label="Quản trị">
          <NavLink to="/admin" end>
            Tổng quan
          </NavLink>
          <NavLink to="/admin/routes">Tuyến đường</NavLink>
          <NavLink to="/admin/vehicles">Phương tiện</NavLink>
          <NavLink to="/admin/trips">Chuyến đi</NavLink>
          <NavLink to="/admin/bookings">Đơn hàng</NavLink>
          <NavLink to="/admin/reconciliation">Cần đối soát</NavLink>
          <NavLink to="/admin/site">Nội dung & logo</NavLink>
        </nav>
      </aside>
      <section className="admin-content">
        <Outlet />
      </section>
    </div>
  );
}

export default function AdminLayout() {
  return (
    <RequireAuth>
      <AdminContent />
    </RequireAuth>
  );
}
