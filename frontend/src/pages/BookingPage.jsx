import { useEffect, useState } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { ArrowLeft, ArrowRight, Check, Clock3, Ticket, X } from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import { api } from "../lib/api";
import { date, displayStatus, money, remaining, time } from "../lib/format";
import useNow from "../hooks/useNow";
import StatusBadge from "../components/StatusBadge";
import { ErrorNotice, Loading } from "../components/Feedback";

export default function BookingPage() {
  const { id } = useParams();
  const { token } = useAuth();
  const location = useLocation();
  const [booking, setBooking] = useState(null);
  const [trip, setTrip] = useState(location.state?.trip || null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [reload, setReload] = useState(0);
  const now = useNow();

  useEffect(() => {
    if (busy) return;
    const controller = new AbortController();
    let inFlight = false;
    async function load() {
      if (inFlight) return;
      inFlight = true;
      try {
        const data = await api("/bookings/" + id, {
          token,
          signal: controller.signal,
        });
        if (controller.signal.aborted) return;
        setBooking(data);
        setError(null);
        try {
          const currentTrip = await api("/trips/" + data.tripId, {
            signal: controller.signal,
          });
          if (!controller.signal.aborted) setTrip(currentTrip);
        } catch {
          /* Chuyến đã khởi hành có thể không còn public; đơn vẫn hiển thị đầy đủ ghế và giá. */
        }
      } catch (failure) {
        if (failure.name !== "AbortError") setError(failure);
      } finally {
        if (!controller.signal.aborted) setLoading(false);
        inFlight = false;
      }
    }
    load();
    const timer = window.setInterval(load, 15000);
    return () => {
      controller.abort();
      window.clearInterval(timer);
    };
  }, [id, token, reload, busy]);

  async function cancel() {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      const updated = await api("/bookings/" + id + "/cancel", {
        method: "POST",
        token,
      });
      setBooking(updated);
      setConfirming(false);
    } catch (failure) {
      setError(failure);
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <Loading text="Đang tải đơn đặt chỗ…" />;
  if (!booking)
    return (
      <section className="page-section">
        <Link className="back-link" to="/bookings">
          <ArrowLeft size={16} /> Vé của tôi
        </Link>
        <ErrorNotice
          error={error}
          retry={() => setReload((value) => value + 1)}
        />
      </section>
    );
  const status = displayStatus(booking, now);

  return (
    <div className="page-section">
      <Link className="back-link" to="/bookings">
        <ArrowLeft size={16} /> Vé của tôi
      </Link>
      <div className="section-heading">
        <div>
          <span className="eyebrow muted">ĐƠN ĐẶT CHỖ #{booking.id}</span>
          <h1>
            {location.state?.created
              ? "Đã giữ chỗ cho bạn."
              : "Chi tiết đơn đặt chỗ"}
          </h1>
          <p>Thông tin được xác nhận bởi hệ thống đặt vé.</p>
        </div>
        <StatusBadge status={status} />
      </div>
      {status === "PENDING" && (
        <div className="hold-banner">
          <Clock3 size={26} />
          <div>
            <strong>Chỗ ngồi đang được giữ riêng cho bạn</strong>
            <p>
              Hết hạn lúc {time(booking.holdExpiresAt)},{" "}
              {date(booking.holdExpiresAt)}. Ghế sẽ tự mở lại khi hết thời gian.
            </p>
          </div>
          <strong className="countdown" aria-label="Thời gian giữ ghế còn lại">
            {remaining(booking.holdExpiresAt, now)}
          </strong>
        </div>
      )}
      {status === "EXPIRED" && (
        <div className="info-notice" role="status">
          Thời gian giữ ghế đã kết thúc. Bạn có thể tìm chuyến và tạo đơn mới.
        </div>
      )}
      {status === "CANCELLED" && (
        <div className="success-notice" role="status">
          <Check size={18} /> Đơn đã hủy. Các ghế đã được giải phóng.
        </div>
      )}
      <ErrorNotice error={error} />
      <div className="booking-layout">
        <section className="panel ticket-detail">
          <div className="panel-heading">
            <div>
              <span className="eyebrow muted">THÔNG TIN HÀNH TRÌNH</span>
              <h2>
                {trip
                  ? trip.origin + " → " + trip.destination
                  : "Chuyến #" + booking.tripId}
              </h2>
              <p>
                {trip
                  ? trip.vehicleName + " · " + date(trip.departureAt)
                  : "Phương tiện #" + booking.vehicleId}
              </p>
            </div>
            <Ticket size={30} strokeWidth={1.3} />
          </div>
          {trip && (
            <div className="detail-times">
              <div>
                <span>Khởi hành</span>
                <strong>{time(trip.departureAt)}</strong>
                <small>{date(trip.departureAt)}</small>
              </div>
              <ArrowRight size={22} />
              <div>
                <span>Đến nơi</span>
                <strong>{time(trip.arrivalAt)}</strong>
                <small>{date(trip.arrivalAt)}</small>
              </div>
            </div>
          )}
          <div className="ticket-perforation" />
          <div className="detail-label">
            <span>MÃ ĐẶT CHỖ</span>
            <strong className="booking-code">{booking.bookingCode}</strong>
          </div>
          <div className="seat-table">
            <div className="seat-table-heading">
              <span>Ghế / hạng ghế</span>
              <span>Giá đã xác nhận</span>
            </div>
            {booking.seats.map((seat) => (
              <div className="seat-table-row" key={seat.id}>
                <span>
                  <strong>{seat.seatCode}</strong>
                  <small>{seat.seatClass}</small>
                </span>
                <strong>{money(seat.unitPrice)}</strong>
              </div>
            ))}
          </div>
          <p className="seat-footnote">
            Đơn được tạo lúc {time(booking.createdAt)},{" "}
            {date(booking.createdAt)}. Đây là thông tin đặt chỗ; vé điện tử được
            phát hành sau khi thanh toán được xác nhận.
          </p>
        </section>
        <aside className="panel booking-summary">
          <span className="eyebrow muted">CHI TIẾT ĐƠN</span>
          <div className="summary-row">
            <span>Số ghế</span>
            <strong>{booking.seats.length} ghế</strong>
          </div>
          <div className="summary-row">
            <span>Trạng thái</span>
            <span>
              {status === "PENDING"
                ? "Đang giữ chỗ"
                : status === "PAID"
                  ? "Đã thanh toán"
                  : status === "EXPIRED"
                    ? "Đã hết hạn"
                    : "Đã hủy"}
            </span>
          </div>
          <div className="summary-total">
            <span>Tổng tiền</span>
            <strong>{money(booking.totalAmount)}</strong>
          </div>
          {status === "PENDING" && (
            <p className="info-notice">
              Thanh toán trực tuyến hiện chưa khả dụng. Đơn này chỉ giữ ghế có
              thời hạn.
            </p>
          )}
          {status === "PENDING" && !confirming && (
            <button
              className="button danger-outline full"
              onClick={() => setConfirming(true)}
            >
              <X size={17} /> Hủy đơn đặt chỗ
            </button>
          )}
          {status === "PENDING" && confirming && (
            <div
              className="cancel-confirm"
              role="group"
              aria-label="Xác nhận hủy đơn"
            >
              <strong>Bạn muốn hủy đơn này?</strong>
              <p>Toàn bộ ghế sẽ được mở lại cho khách khác.</p>
              <button
                className="button danger full"
                disabled={busy}
                onClick={cancel}
              >
                {busy ? "Đang hủy…" : "Xác nhận hủy"}
              </button>
              <button
                className="text-button"
                disabled={busy}
                onClick={() => setConfirming(false)}
              >
                Tiếp tục giữ chỗ
              </button>
            </div>
          )}
          {status !== "PENDING" && (
            <Link className="button full" to="/">
              Tìm chuyến mới <ArrowRight size={17} />
            </Link>
          )}
          <Link className="text-button summary-link" to="/bookings">
            Xem tất cả đơn của tôi <ArrowRight size={15} />
          </Link>
        </aside>
      </div>
    </div>
  );
}
