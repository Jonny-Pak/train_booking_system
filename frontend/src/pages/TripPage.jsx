import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import {
  ArrowLeft,
  ArrowRight,
  Armchair,
  BusFront,
  Clock3,
  RefreshCw,
  ShieldCheck,
  TrainFront,
} from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import { api } from "../lib/api";
import { date, duration, money, time } from "../lib/format";
import { EmptyState, ErrorNotice, Loading } from "../components/Feedback";

export default function TripPage() {
  const { id } = useParams();
  const { token } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [trip, setTrip] = useState(null);
  const [seats, setSeats] = useState([]);
  const [selected, setSelected] = useState(location.state?.seatIds || []);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    Promise.all([
      api("/trips/" + id, { signal: controller.signal }),
      api("/trips/" + id + "/seats", { signal: controller.signal }),
    ])
      .then(([currentTrip, currentSeats]) => {
        setTrip(currentTrip);
        setSeats(currentSeats);
        setSelected((current) =>
          current.filter((seatId) =>
            currentSeats.some(
              (seat) => seat.id === seatId && seat.status === "AVAILABLE",
            ),
          ),
        );
      })
      .catch((failure) => {
        if (failure.name !== "AbortError") setError(failure);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [id, reload]);

  function toggleSeat(seatId) {
    setError(null);
    if (selected.includes(seatId)) {
      setSelected(selected.filter((value) => value !== seatId));
    } else if (selected.length < 10) {
      setSelected([...selected, seatId]);
    } else {
      setError("Bạn chỉ có thể chọn tối đa 10 ghế trong một đơn.");
    }
  }

  async function book() {
    if (!selected.length || busy) return;
    if (!token) {
      navigate("/login", {
        state: { from: "/trips/" + id, seatIds: selected },
      });
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const booking = await api("/bookings", {
        method: "POST",
        token,
        body: { tripId: Number(id), seatIds: selected },
      });
      navigate("/bookings/" + booking.id, { state: { trip, created: true } });
    } catch (failure) {
      setError(failure);
      // Sau conflict phải lấy lại trạng thái, không tự gửi lại POST.
      if (failure.status === 409) {
        setSelected([]);
        try {
          setSeats(await api("/trips/" + id + "/seats"));
        } catch {
          /* Giữ lỗi đặt chỗ để người dùng chủ động tải lại. */
        }
      }
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <Loading text="Đang chuẩn bị sơ đồ ghế…" />;
  if (!trip)
    return (
      <div className="page-section">
        <Link className="back-link" to="/">
          <ArrowLeft size={16} /> Về tìm chuyến
        </Link>
        <ErrorNotice
          error={error}
          retry={() => setReload((value) => value + 1)}
        />
      </div>
    );
  const VehicleIcon = trip.vehicleType === "TRAIN" ? TrainFront : BusFront;
  const selectedSeats = seats.filter((seat) => selected.includes(seat.id));

  return (
    <div className="page-section">
      <Link className="back-link" to="/">
        <ArrowLeft size={16} /> Về tìm chuyến
      </Link>
      <div className="section-heading">
        <div>
          <span className="eyebrow muted">HÀNH TRÌNH CỦA BẠN</span>
          <h1>
            {trip.origin} <ArrowRight className="heading-arrow" />{" "}
            {trip.destination}
          </h1>
          <p>
            {date(trip.departureAt)} · {trip.vehicleName} · {trip.vehicleCode}
          </p>
        </div>
        <span className="type-label">
          <VehicleIcon size={18} />
          {trip.vehicleType === "TRAIN" ? "Tàu hỏa" : "Xe khách"}
        </span>
      </div>
      <div className="booking-layout">
        <section className="panel seat-panel" aria-labelledby="seat-title">
          <div className="panel-heading">
            <div>
              <h2 id="seat-title">Chọn chỗ ngồi yêu thích</h2>
              <p>Tối đa 10 ghế cho một đơn đặt chỗ.</p>
            </div>
            <button
              className="icon-button"
              onClick={() => setReload((value) => value + 1)}
              disabled={busy}
              aria-label="Tải lại sơ đồ ghế"
            >
              <RefreshCw size={18} />
            </button>
          </div>
          <div className="seat-legend">
            <span>
              <i className="available" />
              Còn trống
            </span>
            <span>
              <i className="selected" />
              Đang chọn
            </span>
            <span>
              <i className="held" />
              Đang giữ
            </span>
            <span>
              <i className="booked" />
              Đã đặt
            </span>
          </div>
          {seats.length === 0 ? (
            <EmptyState title="Chưa có sơ đồ ghế">
              <p>Chuyến này chưa sẵn sàng nhận đặt chỗ.</p>
            </EmptyState>
          ) : (
            <div
              className={
                "seat-cabin " +
                (trip.vehicleType === "TRAIN" ? "train-cabin" : "")
              }
            >
              <div className="cabin-front">
                <VehicleIcon size={22} />
                <span>ĐẦU PHƯƠNG TIỆN</span>
              </div>
              <div className="seat-grid" aria-label="Sơ đồ ghế">
                {seats.map((seat) => {
                  let state = seat.status.toLowerCase();
                  if (selected.includes(seat.id)) state = "selected";
                  const label = {
                    AVAILABLE: "còn trống",
                    HELD: "đang giữ",
                    BOOKED: "đã đặt",
                  }[seat.status];
                  return (
                    <button
                      key={seat.id}
                      className={"seat " + state}
                      disabled={seat.status !== "AVAILABLE" || busy}
                      aria-pressed={selected.includes(seat.id)}
                      aria-label={"Ghế " + seat.seatCode + ", " + label}
                      title={seat.seatClass}
                      onClick={() => toggleSeat(seat.id)}
                    >
                      <Armchair size={23} />
                      <span>{seat.seatCode}</span>
                    </button>
                  );
                })}
              </div>
            </div>
          )}
          <p className="seat-footnote">
            Sơ đồ minh họa theo danh sách ghế, không thể hiện chính xác vị trí
            vật lý. Ghế chỉ được giữ khi tạo đơn thành công.
          </p>
        </section>
        <aside className="panel booking-summary">
          <span className="eyebrow muted">TÓM TẮT HÀNH TRÌNH</span>
          <div className="summary-route">
            <div>
              <strong>{time(trip.departureAt)}</strong>
              <span>{trip.origin}</span>
            </div>
            <span className="summary-connector" />
            <div>
              <strong>{time(trip.arrivalAt)}</strong>
              <span>{trip.destination}</span>
            </div>
          </div>
          <p className="muted-row">
            <Clock3 size={15} />
            {duration(trip.departureAt, trip.arrivalAt)} ·{" "}
            {date(trip.departureAt)}
          </p>
          <hr />
          <div className="summary-row">
            <span>Giá mỗi ghế</span>
            <strong>{money(trip.basePrice)}</strong>
          </div>
          <div className="summary-row">
            <span>Ghế đã chọn</span>
            <strong>
              {selectedSeats.length
                ? selectedSeats.map((seat) => seat.seatCode).join(", ")
                : "Chưa chọn"}
            </strong>
          </div>
          <div className="summary-total">
            <span>Tạm tính</span>
            <strong>{money(trip.basePrice * selectedSeats.length)}</strong>
          </div>
          <ErrorNotice error={error} />
          <button
            className="button full"
            disabled={!selected.length || busy}
            onClick={book}
          >
            {busy
              ? "Đang giữ chỗ…"
              : token
                ? "Giữ ghế đã chọn"
                : "Đăng nhập để giữ ghế"}
            <ArrowRight size={17} />
          </button>
          <p className="summary-note">
            <ShieldCheck size={16} />
            Giá và thời hạn giữ ghế được xác nhận khi tạo đơn.
          </p>
        </aside>
      </div>
    </div>
  );
}
