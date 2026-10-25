import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { ArrowRight, RefreshCw, Ticket } from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import { api } from "../lib/api";
import { date, displayStatus, money, time } from "../lib/format";
import useNow from "../hooks/useNow";
import StatusBadge from "../components/StatusBadge";
import Pagination from "../components/Pagination";
import { EmptyState, ErrorNotice, Loading } from "../components/Feedback";

export default function BookingsPage() {
  const { token } = useAuth();
  const [params, setParams] = useSearchParams();
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [reload, setReload] = useState(0);
  const now = useNow();
  const requestedPage = Number(params.get("page") || 0);
  const page =
    Number.isInteger(requestedPage) && requestedPage >= 0 ? requestedPage : 0;

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    api("/bookings/my?page=" + page + "&size=6", {
      token,
      signal: controller.signal,
    })
      .then(setResult)
      .catch((failure) => {
        if (failure.name !== "AbortError") setError(failure);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [page, token, reload]);

  return (
    <section className="page-section">
      <div className="section-heading">
        <div>
          <span className="eyebrow muted">MỌI HÀNH TRÌNH, CÙNG MỘT NƠI</span>
          <h1>Vé của tôi</h1>
          <p>Theo dõi các đơn giữ chỗ và chuyến đi của bạn.</p>
        </div>
        <button
          className="button secondary small"
          onClick={() => setReload((value) => value + 1)}
        >
          <RefreshCw size={16} /> Làm mới
        </button>
      </div>
      <div className="history-heading">
        <strong>Tất cả đơn đặt chỗ</strong>
        {result && <span>{result.totalElements} đơn</span>}
      </div>
      {loading ? (
        <Loading />
      ) : error ? (
        <ErrorNotice
          error={error}
          retry={() => setReload((value) => value + 1)}
        />
      ) : !result?.content.length ? (
        <EmptyState title="Hành trình đầu tiên đang chờ">
          <p>Bạn chưa có đơn trên trang này. Cùng tìm một chuyến đi mới nhé.</p>
          <Link className="button" to="/">
            Khám phá chuyến đi <ArrowRight size={16} />
          </Link>
          {page > 0 && (
            <button className="text-button" onClick={() => setParams({})}>
              Về trang đầu
            </button>
          )}
        </EmptyState>
      ) : (
        <>
          <div className="history-list">
            {result.content.map((booking) => (
              <article className="history-card" key={booking.id}>
                <div className="history-icon">
                  <Ticket size={25} />
                </div>
                <div className="history-info">
                  <span>CHUYẾN #{booking.tripId}</span>
                  <h2>
                    Ghế {booking.seats.map((seat) => seat.seatCode).join(", ")}
                  </h2>
                  <p>
                    Đặt lúc {time(booking.createdAt)}, {date(booking.createdAt)}
                  </p>
                  <small className="booking-code">{booking.bookingCode}</small>
                </div>
                <div className="history-status">
                  <StatusBadge status={displayStatus(booking, now)} />
                  <strong>{money(booking.totalAmount)}</strong>
                </div>
                <Link
                  className="button secondary small"
                  to={"/bookings/" + booking.id}
                >
                  Chi tiết <ArrowRight size={16} />
                </Link>
              </article>
            ))}
          </div>
          <Pagination
            page={page}
            totalPages={result.totalPages}
            onChange={(next) => setParams({ page: String(next) })}
          />
        </>
      )}
    </section>
  );
}
