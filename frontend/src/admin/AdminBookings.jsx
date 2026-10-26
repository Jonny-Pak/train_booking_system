import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { api } from "../lib/api";
import { date, displayStatus, money, statusLabels, time } from "../lib/format";
import { ErrorNotice, Loading } from "../components/Feedback";
import AdminPagination from "./AdminPagination";

export default function AdminBookings({ reconciliation = false }) {
  const { token } = useAuth();
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [reload, setReload] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setData(null);
    setError(null);
    const path = reconciliation
      ? "/admin/payments/reconciliation"
      : "/admin/bookings";
    api(
      path + "?page=" + page + "&size=10" + (status ? "&status=" + status : ""),
      { token, signal: controller.signal },
    )
      .then(setData)
      .catch((e) => {
        if (e.name !== "AbortError") setError(e);
      });
    return () => controller.abort();
  }, [token, status, page, reload, reconciliation]);
  return (
    <>
      <header className="admin-heading">
        <h1>{reconciliation ? "Giao dịch cần đối soát" : "Đơn hàng"}</h1>
        <p>
          {reconciliation
            ? "Kiểm tra với merchant VNPAY trước khi xử lý thủ công. Trang này chỉ tra cứu."
            : "Tra cứu đơn của toàn bộ khách hàng."}
        </p>
      </header>
      <div className="admin-filters">
        {!reconciliation && (
          <label>
            Trạng thái đơn
            <select
              value={status}
              onChange={(e) => {
                setStatus(e.target.value);
                setPage(0);
              }}
            >
              <option value="">Tất cả</option>
              {Object.entries(statusLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
        )}
        <button
          className="button secondary"
          onClick={() => setReload((v) => v + 1)}
        >
          Tải lại
        </button>
      </div>
      {!reconciliation && (
        <p className="admin-note">
          Bộ lọc dùng trạng thái đã lưu. Đơn PENDING quá hạn sẽ có nhãn hết hạn
          trong lúc chờ scheduler cập nhật.
        </p>
      )}
      <ErrorNotice error={error} retry={() => setReload((v) => v + 1)} />
      {!data && !error && <Loading />}
      {data && (
        <>
          <div className="admin-table-wrap">
            <table>
              <caption>{data.totalElements} bản ghi</caption>
              <thead>
                <tr>
                  <th>Mã</th>
                  <th>Số tiền</th>
                  <th>Trạng thái</th>
                  <th>Chi tiết</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((item) => (
                  <tr key={item.id}>
                    <td>
                      <strong>
                        {reconciliation ? item.txnRef : item.bookingCode}
                      </strong>
                      {!reconciliation && (
                        <p>
                          Khách #{item.userId} · Chuyến #{item.tripId}
                        </p>
                      )}
                    </td>
                    <td>
                      {money(reconciliation ? item.amount : item.totalAmount)}
                    </td>
                    <td>
                      {reconciliation
                        ? item.status
                        : statusLabels[displayStatus(item)]}
                      {reconciliation && <p>{item.reconciliationReason}</p>}
                    </td>
                    <td>
                      <Link
                        className="text-button"
                        to={
                          "/admin/bookings/" +
                          (reconciliation ? item.bookingId : item.id)
                        }
                      >
                        Xem đơn
                      </Link>
                      {!reconciliation && (
                        <p>
                          {date(item.createdAt)} {time(item.createdAt)}
                        </p>
                      )}
                    </td>
                  </tr>
                ))}
                {data.content.length === 0 && (
                  <tr>
                    <td colSpan="4">Chưa có dữ liệu.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          <AdminPagination data={data} page={page} setPage={setPage} />
        </>
      )}
    </>
  );
}
