import { seatDescription } from "../lib/seats";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { api } from "../lib/api";
import { date, displayStatus, money, statusLabels, time } from "../lib/format";
import { ErrorNotice, Loading } from "../components/Feedback";

export default function AdminBookingDetail() {
  const { id } = useParams();
  const { token } = useAuth();
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [reload, setReload] = useState(0);
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    setData(null);
    setError(null);
    async function load() {
      const options = { token, signal: controller.signal };
      try {
        const booking = await api("/bookings/" + id, options);
        const [trip, payments] = await Promise.all([
          api("/admin/trips/" + booking.tripId, options),
          api("/bookings/" + id + "/payments", options),
        ]);
        if (!controller.signal.aborted) setData({ booking, trip, payments });
      } catch (e) {
        if (e.name !== "AbortError") setError(e);
      }
    }
    load();
    return () => controller.abort();
  }, [id, token, reload]);
  async function cancel() {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      await api("/bookings/" + id + "/cancel", { token, method: "POST" });
      setConfirming(false);
      setReload((v) => v + 1);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <Link className="text-button" to="/admin/bookings">
        ← Danh sách đơn
      </Link>
      <header className="admin-heading">
        <h1>Chi tiết đơn hàng</h1>
      </header>
      <ErrorNotice error={error} retry={() => setReload((v) => v + 1)} />
      {!data && !error && <Loading />}
      {data && (
        <>
          <section className="admin-panel">
            <h2>{data.booking.bookingCode}</h2>
            <p>
              {data.trip.origin} → {data.trip.destination} ·{" "}
              {data.trip.vehicleCode}
            </p>
            <p>
              {date(data.trip.departureAt)} {time(data.trip.departureAt)}
            </p>
            <p>
              Khách #{data.booking.userId} ·{" "}
              {statusLabels[displayStatus(data.booking)]}
            </p>
            <h2>{money(data.booking.totalAmount)}</h2>
            <p>Ghế: {data.booking.seats.map(seatDescription).join("; ")}</p>
            <div className="admin-toolbar">
              <button
                className="button secondary"
                disabled={busy}
                onClick={() => setReload((v) => v + 1)}
              >
                Tải lại
              </button>
              {data.booking.status === "PAID" && (
                <Link className="button" to={"/bookings/" + id + "/tickets"}>
                  Xem vé điện tử
                </Link>
              )}
              {displayStatus(data.booking) === "PENDING" && (
                <button
                  className="button danger"
                  disabled={busy}
                  onClick={() => setConfirming(true)}
                >
                  Hủy đơn
                </button>
              )}
            </div>
            {confirming && (
              <div role="alertdialog" aria-label="Xác nhận hủy đơn">
                <p>
                  Hủy đơn và giải phóng ghế? Đơn đã thanh toán không được hủy.
                </p>
                <div className="admin-toolbar">
                  <button
                    className="button danger"
                    disabled={busy}
                    onClick={cancel}
                  >
                    Xác nhận hủy
                  </button>
                  <button
                    className="button secondary"
                    disabled={busy}
                    onClick={() => setConfirming(false)}
                  >
                    Giữ đơn
                  </button>
                </div>
              </div>
            )}
          </section>
          <section className="admin-panel">
            <h2>Lịch sử thanh toán</h2>
            <p className="admin-note">
              Chỉ IPN hợp lệ được xác nhận thanh toán; không có nút sửa trạng
              thái hoặc cộng doanh thu thủ công.
            </p>
            {data.payments.length === 0 && <p>Chưa có giao dịch.</p>}
            {data.payments.map((payment) => (
              <article className="admin-payment" key={payment.id}>
                <strong>{payment.txnRef}</strong>
                <p>
                  {money(payment.amount)} · {payment.status}
                </p>
                <p>Mã phản hồi: {payment.responseCode || "—"}</p>
                {payment.reconciliationRequired && (
                  <p className="admin-field-error">
                    Cần đối soát: {payment.reconciliationReason}
                  </p>
                )}
              </article>
            ))}
          </section>
        </>
      )}
    </>
  );
}
