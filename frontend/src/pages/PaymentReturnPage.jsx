import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { api } from "../lib/api";
import { money } from "../lib/format";
import { ErrorNotice, Loading } from "../components/Feedback";

export default function PaymentReturnPage() {
  const [query] = useSearchParams();
  const txnRef = query.get("vnp_TxnRef") || "";
  const { user, token } = useAuth();
  const [payment, setPayment] = useState(null);
  const [error, setError] = useState(null);
  const [reload, setReload] = useState(0);
  const [checking, setChecking] = useState(true);

  useEffect(() => {
    if (!token || !txnRef || txnRef.length > 100) return;
    const controller = new AbortController();
    let timer;
    let attempts = 0;
    async function load() {
      try {
        // Never trust response codes or amounts in the browser's return URL.
        const result = await api("/payments/" + encodeURIComponent(txnRef), {
          token,
          signal: controller.signal,
        });
        if (controller.signal.aborted) return;
        setPayment(result);
        setError(null);
        attempts++;
        if (result.status === "PENDING" && attempts < 20)
          timer = window.setTimeout(load, 3000);
        else setChecking(false);
      } catch (failure) {
        if (failure.name !== "AbortError") {
          setError(failure);
          setChecking(false);
        }
      }
    }
    load();
    return () => {
      controller.abort();
      window.clearTimeout(timer);
    };
  }, [token, txnRef, reload]);

  const from = "/payment/return?vnp_TxnRef=" + encodeURIComponent(txnRef);
  if (!user)
    return (
      <section className="verification-card">
        <h1>Kết quả thanh toán</h1>
        <p>Đăng nhập để xem kết quả được xác nhận bởi hệ thống.</p>
        <Link className="button" to="/login" state={{ from }}>
          Đăng nhập
        </Link>
      </section>
    );
  if (!txnRef || txnRef.length > 100)
    return (
      <section className="verification-card">
        <h1>Thiếu mã giao dịch</h1>
        <Link to="/bookings">Xem đơn hàng của tôi</Link>
      </section>
    );
  if (!payment && !error) return <Loading text="Đang kiểm tra thanh toán…" />;
  let title = "Đang chờ xác nhận thanh toán";
  let description =
    "Hệ thống chưa nhận xác nhận từ VNPAY. Việc quay về trang này chưa xác nhận thanh toán thành công.";
  if (payment?.reconciliationRequired) {
    title = "Giao dịch cần được đối soát";
    description =
      "Giao dịch không thể tự động hoàn tất đơn. Vui lòng liên hệ hỗ trợ và cung cấp mã giao dịch; không thanh toán lại khi chưa được xác minh.";
  } else if (payment?.status === "SUCCESS") {
    title = "Thanh toán thành công";
    description =
      "Hệ thống đã xác nhận thanh toán và phát hành vé điện tử cho bạn.";
  } else if (payment?.status === "FAILED") {
    title = "Thanh toán chưa thành công";
    description =
      "Giao dịch đã thất bại hoặc bị hủy. Bạn có thể thử lại nếu đơn vẫn còn thời gian giữ ghế.";
  }
  return (
    <section className="verification-card payment-result">
      <h1>{title}</h1>
      <p role="status">{description}</p>
      <ErrorNotice error={error} />
      {payment && (
        <>
          <p>
            Mã giao dịch:{" "}
            <strong className="payment-reference">{payment.txnRef}</strong>
            <br />
            Số tiền: <strong>{money(payment.amount)}</strong>
          </p>
          {payment.status === "SUCCESS" && !payment.reconciliationRequired && (
            <Link
              className="button"
              to={"/bookings/" + payment.bookingId + "/tickets"}
            >
              Xem vé điện tử
            </Link>
          )}
          <Link
            className="button secondary"
            to={"/bookings/" + payment.bookingId}
          >
            Chi tiết đơn hàng
          </Link>
        </>
      )}
      {!checking || error ? (
        <button
          className="text-button"
          onClick={() => {
            setChecking(true);
            setReload(reload + 1);
          }}
        >
          Kiểm tra lại trạng thái
        </button>
      ) : (
        <small>
          Đang tự động kiểm tra; không tạo thêm giao dịch trong lúc chờ.
        </small>
      )}
      <Link className="text-button" to="/bookings">
        Đơn hàng của tôi
      </Link>
    </section>
  );
}
