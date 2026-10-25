import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { QRCodeSVG } from "qrcode.react";
import { useAuth } from "../auth/AuthContext";
import { api } from "../lib/api";
import { date, time, money } from "../lib/format";
import { ErrorNotice, Loading } from "../components/Feedback";

export default function TicketsPage() {
  const { id } = useParams();
  const { token } = useAuth();
  const [tickets, setTickets] = useState(null);
  const [error, setError] = useState(null);
  const [reload, setReload] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    api("/bookings/" + id + "/tickets", { token, signal: controller.signal })
      .then(setTickets)
      .catch((failure) => {
        if (failure.name !== "AbortError") setError(failure);
      });
    return () => controller.abort();
  }, [id, token, reload]);
  if (!tickets && !error) return <Loading text="Đang tải vé điện tử…" />;
  return (
    <section className="page-section electronic-tickets">
      <div className="section-heading ticket-actions">
        <div>
          <h1>Vé điện tử của bạn</h1>
          <p>Giữ mã vé để xuất trình khi lên xe hoặc tàu.</p>
        </div>
        {tickets?.length > 0 && (
          <button className="button" onClick={() => window.print()}>
            In vé / Lưu PDF
          </button>
        )}
      </div>
      <ErrorNotice
        error={error}
        retry={() => {
          setError(null);
          setReload(reload + 1);
        }}
      />
      {tickets?.length === 0 && (
        <p className="info-notice" role="status">
          Đơn này chưa có vé điện tử. Vé chỉ được phát hành sau khi hệ thống xác
          nhận thanh toán hợp lệ.
        </p>
      )}
      <div className="electronic-ticket-list">
        {tickets?.map((ticket) => (
          <article className="electronic-ticket" key={ticket.ticketCode}>
            <div className="electronic-ticket-information">
              <span className="eyebrow muted">
                LÊN ĐƯỜNG · VÉ ĐIỆN TỬ · SANDBOX
              </span>
              <h2>
                {ticket.origin} → {ticket.destination}
              </h2>
              <p>
                {ticket.vehicleName} · {ticket.vehicleCode}
              </p>
              <dl>
                <div>
                  <dt>Hành khách</dt>
                  <dd>{ticket.passengerName}</dd>
                </div>
                <div>
                  <dt>Ghế</dt>
                  <dd>
                    {ticket.seatCode} · {ticket.seatClass}
                  </dd>
                </div>
                <div>
                  <dt>Khởi hành</dt>
                  <dd>
                    {time(ticket.departureAt)}, {date(ticket.departureAt)}
                  </dd>
                </div>
                <div>
                  <dt>Đến nơi</dt>
                  <dd>
                    {time(ticket.arrivalAt)}, {date(ticket.arrivalAt)}
                  </dd>
                </div>
                <div>
                  <dt>Giá vé</dt>
                  <dd>{money(ticket.unitPrice)}</dd>
                </div>
              </dl>
              <p className="payment-reference">
                Mã vé: <strong>{ticket.ticketCode}</strong>
              </p>
              <small>
                Phát hành: {time(ticket.issuedAt)}, {date(ticket.issuedAt)}
              </small>
            </div>
            <div className="ticket-qr">
              <QRCodeSVG
                value={ticket.qrContent}
                title={"QR vé ghế " + ticket.seatCode}
                size={160}
                marginSize={4}
                level="M"
              />
              <strong>Ghế {ticket.seatCode}</strong>
              <small>Vé thử nghiệm · Không dùng đi lại thực tế</small>
            </div>
          </article>
        ))}
      </div>
      <Link className="back-link ticket-actions" to={"/bookings/" + id}>
        ← Quay lại đơn hàng
      </Link>
    </section>
  );
}
