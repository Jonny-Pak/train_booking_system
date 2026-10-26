import { useEffect, useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { api } from "../lib/api";
import { ErrorNotice } from "../components/Feedback";

export default function AdminSeatLayout({
  vehicleId,
  onLayoutChange,
  onGenerated,
}) {
  const { token } = useAuth();
  const [vehicle, setVehicle] = useState(null);
  const [error, setError] = useState(null);
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [reload, setReload] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setError(null);
    api("/admin/vehicles/" + vehicleId, { token, signal: controller.signal })
      .then((data) => {
        setVehicle(data);
        onLayoutChange(data.seatLayout);
      })
      .catch((e) => {
        if (e.name !== "AbortError") setError(e);
      });
    return () => controller.abort();
  }, [vehicleId, token, reload, onLayoutChange]);
  async function generate() {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      const data = await api("/admin/vehicles/" + vehicleId + "/seat-layout", {
        token,
        method: "POST",
      });
      setVehicle(data);
      onLayoutChange(data.seatLayout);
      setConfirming(false);
      onGenerated();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="admin-panel">
      <h2>Sơ đồ chuẩn</h2>
      <ErrorNotice error={error} retry={() => setReload((v) => v + 1)} />
      {vehicle && (
        <>
          <p>
            {vehicle.vehicleType === "BUS"
              ? "Xe giường nằm 34 chỗ · 2 tầng, mỗi tầng 17 giường · 2 dãy, 1 lối đi."
              : "Tàu 11 toa · 434 chỗ: 2 toa ghế ngồi, 4 toa giường 4, 5 toa giường 6."}
          </p>
          {vehicle.seatLayout !== "CUSTOM" ? (
            <p role="status">
              Đã có sơ đồ chuẩn. Không thêm/xóa riêng lẻ để giữ đúng số chỗ và
              vị trí.
            </p>
          ) : (
            <>
              <button
                className="button"
                disabled={busy}
                onClick={() => setConfirming(true)}
              >
                Tạo sơ đồ chuẩn
              </button>
              {confirming && (
                <div role="alertdialog" aria-label="Xác nhận tạo sơ đồ">
                  <p>
                    Thay danh sách ghế hiện tại bằng sơ đồ chuẩn? Chỉ thực hiện
                    khi phương tiện chưa có lịch sử đặt vé.
                  </p>
                  <div className="admin-toolbar">
                    <button
                      className="button"
                      disabled={busy}
                      onClick={generate}
                    >
                      {busy ? "Đang tạo…" : "Xác nhận tạo sơ đồ"}
                    </button>
                    <button
                      className="button secondary"
                      disabled={busy}
                      onClick={() => setConfirming(false)}
                    >
                      Giữ sơ đồ cũ
                    </button>
                  </div>
                </div>
              )}
            </>
          )}
        </>
      )}
    </section>
  );
}
