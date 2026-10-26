import { useEffect, useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { api } from "../lib/api";
import { money } from "../lib/format";
import { ErrorNotice, Loading } from "../components/Feedback";

export default function AdminDashboard() {
  const { token } = useAuth();
  const [range, setRange] = useState({ from: "", to: "" });
  const [query, setQuery] = useState("");
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [reload, setReload] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setData(null);
    setError(null);
    api("/admin/statistics?" + query, { token, signal: controller.signal })
      .then(setData)
      .catch((e) => {
        if (e.name !== "AbortError") setError(e);
      });
    return () => controller.abort();
  }, [token, query, reload]);
  function filter(event) {
    event.preventDefault();
    const params = new URLSearchParams();
    if (range.from) params.set("from", range.from);
    if (range.to) params.set("to", range.to);
    setQuery(params.toString());
    setReload((value) => value + 1);
  }
  return (
    <>
      <header className="admin-heading">
        <p>HIỆU QUẢ KINH DOANH</p>
        <h1>Tổng quan hệ thống</h1>
        <p>Dữ liệu thực từ đơn hàng, thanh toán và vé điện tử.</p>
      </header>
      <form className="admin-filters" onSubmit={filter}>
        <label>
          Từ ngày
          <input
            type="date"
            value={range.from}
            onChange={(e) => setRange({ ...range, from: e.target.value })}
          />
        </label>
        <label>
          Đến ngày
          <input
            type="date"
            value={range.to}
            onChange={(e) => setRange({ ...range, to: e.target.value })}
          />
        </label>
        <button className="button">Xem thống kê</button>
      </form>
      <ErrorNotice
        error={error}
        retry={() => setReload((value) => value + 1)}
      />
      {!data && !error && <Loading />}
      {data && (
        <>
          <p className="admin-note">
            Kỳ thống kê: {data.from} → {data.to} · Giờ Việt Nam
          </p>
          <div className="admin-metrics">
            <article>
              <p>Doanh thu đã xác nhận</p>
              <strong>{money(data.revenue)}</strong>
              <small>Theo ngày thanh toán, loại trừ cần đối soát</small>
            </article>
            <article>
              <p>Tổng đơn tạo mới</p>
              <strong>{data.totalBookings}</strong>
              <small>Theo ngày tạo đơn</small>
            </article>
            <article>
              <p>Vé đã phát hành</p>
              <strong>{data.issuedTickets}</strong>
              <small>Theo ngày phát hành</small>
            </article>
            <article>
              <p>Giao dịch cần đối soát</p>
              <strong>{data.reconciliationPayments}</strong>
              <small>
                {money(data.reconciliationAmount)} · Theo ngày tạo giao dịch
              </small>
            </article>
          </div>
          <section className="admin-panel">
            <h2>Trạng thái đơn trong kỳ</h2>
            <dl className="admin-counts">
              <div>
                <dt>Đã thanh toán</dt>
                <dd>{data.paidBookings}</dd>
              </div>
              <div>
                <dt>Đang giữ chỗ</dt>
                <dd>{data.pendingBookings}</dd>
              </div>
              <div>
                <dt>Hết hạn</dt>
                <dd>{data.expiredBookings}</dd>
              </div>
              <div>
                <dt>Đã hủy</dt>
                <dd>{data.cancelledBookings}</dd>
              </div>
            </dl>
            <p className="admin-note">
              Đơn giữ chỗ quá hạn được tính là hết hạn. Mặc định xem từ đầu
              tháng đến hôm nay; tối đa 366 ngày.
            </p>
          </section>
        </>
      )}
    </>
  );
}
