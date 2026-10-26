import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { api } from "../lib/api";
import { date, money, time } from "../lib/format";
import { ErrorNotice, Loading } from "../components/Feedback";
import { catalogs, catalogBody, catalogOptions, vietnamInput } from "./catalog";
import AdminPagination from "./AdminPagination";
import AdminSeatLayout from "./AdminSeatLayout";
import { seatDescription, seatClassLabels } from "../lib/seats";

export default function AdminCatalog({ kind }) {
  const { vehicleId } = useParams();
  const { token } = useAuth();
  const config = catalogs[kind];
  const path =
    kind === "seats"
      ? "/admin/vehicles/" + vehicleId + "/seats"
      : "/admin/" + kind;
  const [data, setData] = useState(null);
  const [page, setPage] = useState(0);
  const [reload, setReload] = useState(0);
  const [error, setError] = useState(null);
  const [form, setForm] = useState(null);
  const [editing, setEditing] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [references, setReferences] = useState(null);
  const [referenceError, setReferenceError] = useState(null);
  const [seatLayout, setSeatLayout] = useState("CUSTOM");
  const fixedSeats = kind === "seats" && seatLayout !== "CUSTOM";
  useEffect(() => {
    const controller = new AbortController();
    setData(null);
    api(path + "?page=" + page + "&size=10", {
      token,
      signal: controller.signal,
    })
      .then((result) => {
        if (page > 0 && result.content.length === 0) setPage(page - 1);
        else setData(result);
      })
      .catch((e) => {
        if (e.name !== "AbortError") setError(e);
      });
    return () => controller.abort();
  }, [path, token, page, reload]);
  useEffect(() => {
    if (kind !== "trips") return;
    const controller = new AbortController();
    setReferences(null);
    setReferenceError(null);
    Promise.all([
      catalogOptions(api, "/admin/routes", {
        token,
        signal: controller.signal,
      }),
      catalogOptions(api, "/admin/vehicles", {
        token,
        signal: controller.signal,
      }),
    ])
      .then(([routes, vehicles]) => setReferences({ routes, vehicles }))
      .catch((e) => {
        if (e.name !== "AbortError") setReferenceError(e);
      });
    return () => controller.abort();
  }, [kind, token, reload]);
  function open(item) {
    const next = {};
    for (const field of config.fields) {
      next[field.name] = item ? item[field.name] : (field.value ?? "");
      if (item && field.type === "datetime-local")
        next[field.name] = vietnamInput(item[field.name]);
    }
    setEditing(item?.id ?? null);
    setForm(next);
    setDeleting(null);
    setError(null);
    setNotice("");
  }
  async function save(event) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      await api(path + (editing ? "/" + editing : ""), {
        token,
        method: editing ? "PUT" : "POST",
        body: catalogBody(kind, form),
      });
      setForm(null);
      setNotice("Đã lưu thành công.");
      setReload((value) => value + 1);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  async function remove() {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      await api(path + "/" + deleting.id, { token, method: "DELETE" });
      setDeleting(null);
      setNotice("Đã xóa thành công.");
      setReload((value) => value + 1);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <header className="admin-heading">
        <h1>{config.title}</h1>
        <p>
          {kind === "seats"
            ? "Quản lý ghế của phương tiện #" + vehicleId
            : "Tạo và cập nhật dữ liệu bán vé."}
        </p>
      </header>
      {kind === "seats" && (
        <Link className="text-button" to="/admin/vehicles">
          ← Quay lại phương tiện
        </Link>
      )}
      <div className="admin-toolbar">
        <button
          className="button"
          disabled={busy || fixedSeats}
          onClick={() => open(null)}
        >
          Thêm mới
        </button>
        <button
          className="button secondary"
          disabled={busy}
          onClick={() => {
            setError(null);
            setReload((v) => v + 1);
          }}
        >
          Tải lại
        </button>
      </div>
      {kind === "seats" && (
        <AdminSeatLayout
          vehicleId={vehicleId}
          onLayoutChange={setSeatLayout}
          onGenerated={() => {
            setForm(null);
            setPage(0);
            setReload((v) => v + 1);
          }}
        />
      )}
      <ErrorNotice error={error} />
      <ErrorNotice
        error={referenceError}
        retry={() => setReload((v) => v + 1)}
      />
      {notice && (
        <p role="status" className="admin-success">
          {notice}
        </p>
      )}
      {deleting && (
        <section
          className="admin-panel"
          role="alertdialog"
          aria-label="Xác nhận xóa"
        >
          <h2>Xóa bản ghi #{deleting.id}?</h2>
          <p>Dữ liệu đã có lịch sử sẽ được hệ thống bảo vệ.</p>
          <div className="admin-toolbar">
            <button className="button danger" disabled={busy} onClick={remove}>
              Xác nhận xóa
            </button>
            <button
              className="button secondary"
              disabled={busy}
              onClick={() => setDeleting(null)}
            >
              Giữ lại
            </button>
          </div>
        </section>
      )}
      {form && (
        <form
          className="admin-panel admin-form"
          onSubmit={save}
          aria-label="Biểu mẫu catalog"
        >
          <h2>
            {editing
              ? "Chỉnh sửa #" + editing
              : "Thêm " + config.title.toLowerCase()}
          </h2>
          {kind === "trips" && !references && !referenceError && (
            <Loading text="Đang tải tuyến và phương tiện…" />
          )}
          <fieldset disabled={busy || (kind === "trips" && !references)}>
            {config.fields.map((field) => (
              <label key={field.name}>
                {field.label}
                {field.options || field.reference ? (
                  <select
                    aria-label={field.label}
                    required
                    value={form[field.name]}
                    onChange={(e) =>
                      setForm({ ...form, [field.name]: e.target.value })
                    }
                  >
                    {field.reference && (
                      <option value="">Chọn {field.label.toLowerCase()}</option>
                    )}
                    {field.options?.map(([value, label]) => (
                      <option key={value} value={value}>
                        {label}
                      </option>
                    ))}
                    {field.reference &&
                      references?.[field.reference].map((item) => (
                        <option key={item.id} value={item.id}>
                          {field.reference === "routes"
                            ? item.origin + " → " + item.destination
                            : item.vehicleCode + " · " + item.displayName}
                          {!item.active ? " (ngừng hoạt động)" : ""}
                        </option>
                      ))}
                  </select>
                ) : (
                  <input
                    aria-label={field.label}
                    type={field.type || "text"}
                    required={field.type !== "checkbox"}
                    maxLength={field.maxLength}
                    min={field.type === "number" ? "0" : undefined}
                    step={field.type === "number" ? "0.01" : undefined}
                    checked={
                      field.type === "checkbox" ? form[field.name] : undefined
                    }
                    value={
                      field.type === "checkbox" ? undefined : form[field.name]
                    }
                    onChange={(e) =>
                      setForm({
                        ...form,
                        [field.name]:
                          field.type === "checkbox"
                            ? e.target.checked
                            : e.target.value,
                      })
                    }
                  />
                )}
                {error?.fields?.[field.name] && (
                  <small className="admin-field-error">
                    {error.fields[field.name]}
                  </small>
                )}
              </label>
            ))}
          </fieldset>
          <div className="admin-toolbar">
            <button
              className="button"
              disabled={busy || (kind === "trips" && !references)}
            >
              {busy ? "Đang lưu…" : "Lưu"}
            </button>
            <button
              type="button"
              className="button secondary"
              disabled={busy}
              onClick={() => setForm(null)}
            >
              Đóng biểu mẫu
            </button>
          </div>
          {kind === "trips" && (
            <p className="admin-note">
              Chuyến mới phải mở bán, trong tương lai và phương tiện có ghế.
              Không sửa chuyến đã có lịch sử đặt vé.
            </p>
          )}
          {kind === "seats" && (
            <p className="admin-note">
              Không thay đổi ghế khi phương tiện đã có lịch sử đặt vé.
            </p>
          )}
        </form>
      )}
      {!data && !error && <Loading />}
      {data && (
        <>
          <div className="admin-table-wrap">
            <table>
              <caption>
                {config.title} · {data.totalElements} bản ghi
              </caption>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Thông tin</th>
                  <th>Trạng thái / Chi tiết</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((item) => (
                  <tr key={item.id}>
                    <td>#{item.id}</td>
                    <td>
                      {kind === "routes" && (
                        <strong>
                          {item.origin} → {item.destination}
                        </strong>
                      )}
                      {kind === "vehicles" && (
                        <>
                          <strong>{item.vehicleCode}</strong>
                          <p>
                            {item.displayName} · {item.vehicleType}
                          </p>
                        </>
                      )}
                      {kind === "seats" && (
                        <>
                          <strong>{seatDescription(item)}</strong>
                          <p>{item.seatCode}</p>
                        </>
                      )}
                      {kind === "trips" && (
                        <>
                          <strong>
                            {item.origin} → {item.destination}
                          </strong>
                          <p>
                            {item.vehicleCode} · {money(item.basePrice)}
                          </p>
                          <p>
                            {date(item.departureAt)} {time(item.departureAt)} →{" "}
                            {date(item.arrivalAt)} {time(item.arrivalAt)}
                          </p>
                        </>
                      )}
                    </td>
                    <td>
                      {kind === "seats"
                        ? seatClassLabels[item.seatClass] || item.seatClass
                        : kind === "trips"
                          ? item.status
                          : item.active
                            ? "Hoạt động"
                            : "Ngừng hoạt động"}
                    </td>
                    <td>
                      <div className="admin-row-actions">
                        <button
                          className="text-button"
                          disabled={busy || fixedSeats}
                          onClick={() => open(item)}
                        >
                          Sửa
                        </button>
                        <button
                          className="text-button"
                          disabled={busy || fixedSeats}
                          onClick={() => {
                            setForm(null);
                            setError(null);
                            setDeleting(item);
                          }}
                        >
                          Xóa
                        </button>
                        {kind === "vehicles" && (
                          <Link
                            className="text-button"
                            to={"/admin/vehicles/" + item.id + "/seats"}
                          >
                            Quản lý ghế
                          </Link>
                        )}
                      </div>
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
