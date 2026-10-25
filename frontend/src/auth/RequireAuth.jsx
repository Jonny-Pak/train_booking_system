import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "./AuthContext";
import { Loading } from "../components/Feedback";

export default function RequireAuth({ children }) {
  const { session, checking } = useAuth();
  const location = useLocation();
  if (checking) return <Loading text="Đang kiểm tra phiên đăng nhập…" />;
  if (!session)
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: location.pathname + location.search }}
      />
    );
  return children;
}
