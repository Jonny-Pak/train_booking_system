import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { api, siteLogoUrl } from "../lib/api";
import { useSite } from "../site/SiteContext";
import GuideContent from "../site/GuideContent";
import GuideEditor from "./GuideEditor";
import { ErrorNotice, Loading } from "../components/Feedback";

function SettingsForm({ initial }) {
  const { token } = useAuth();
  const { settings, setSettings } = useSite();
  const [title, setTitle] = useState(initial.guideTitle);
  const [html, setHtml] = useState(initial.guideHtml);
  const [revision, setRevision] = useState(initial.revision);
  const [image, setImage] = useState("");
  const [dirty, setDirty] = useState(false);
  const [preview, setPreview] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState("");
  useEffect(() => {
    if (!dirty && !image) return;
    function warn(event) {
      event.preventDefault();
      event.returnValue = "";
    }
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty, image]);

  async function saveGuide(event) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setNotice("");
    try {
      const result = await api("/admin/site/guide", {
        method: "PUT",
        token,
        body: { title, html, revision },
      });
      setSettings(result);
      setRevision(result.revision);
      setDirty(false);
      setNotice("Đã lưu hướng dẫn. Khách hàng có thể xem nội dung mới ngay.");
    } catch (failure) {
      setError(failure);
    } finally {
      setBusy(false);
    }
  }

  async function chooseLogo(event) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    setError(null);
    setNotice("");
    if (
      !["image/png", "image/jpeg"].includes(file.type) ||
      file.size > 2 * 1024 * 1024
    ) {
      setError(new Error("Chọn ảnh PNG hoặc JPG tối đa 2 MB."));
      return;
    }
    try {
      const reader = new FileReader();
      const data = await new Promise((resolve, reject) => {
        reader.onload = () => resolve(reader.result);
        reader.onerror = reject;
        reader.readAsDataURL(file);
      });
      const picture = new Image();
      picture.src = data;
      await picture.decode();
      if (
        picture.naturalWidth < 16 ||
        picture.naturalHeight < 16 ||
        picture.naturalWidth > 4096 ||
        picture.naturalHeight > 4096
      )
        throw new Error("Chiều rộng và chiều cao ảnh phải từ 16 đến 4096 px.");
      setImage(data);
    } catch (failure) {
      setError(
        new Error(
          failure.message || "Không đọc được ảnh. Hãy chọn ảnh PNG/JPG khác.",
        ),
      );
    }
  }

  async function saveLogo(reset = false) {
    setBusy(true);
    setError(null);
    setNotice("");
    try {
      const result = await api(
        reset ? "/admin/site/logo?revision=" + revision : "/admin/site/logo",
        {
          method: reset ? "DELETE" : "PUT",
          token,
          body: reset
            ? undefined
            : { imageBase64: image.split(",")[1], revision },
        },
      );
      setSettings(result);
      setRevision(result.revision);
      setImage("");
      setNotice(
        reset
          ? "Đã khôi phục logo Vexesieure mặc định."
          : "Đã thay logo ở đầu trang và chân trang.",
      );
    } catch (failure) {
      setError(failure);
    } finally {
      setBusy(false);
    }
  }
  const logo =
    image ||
    (settings.customLogo
      ? siteLogoUrl(settings.revision)
      : "/images/vexesieure-logo-white.svg");
  return (
    <>
      <ErrorNotice error={error} />
      {error?.code === "SITE_CHANGED" && (
        <p>
          Có người vừa cập nhật nội dung. Sao chép phần đang soạn trước khi{" "}
          <button
            className="text-button"
            onClick={() => window.location.reload()}
          >
            tải lại trang
          </button>
          .
        </p>
      )}
      {notice && (
        <p className="site-save-notice" role="status">
          {notice}
        </p>
      )}
      <form className="site-settings-card" onSubmit={saveGuide}>
        <div className="site-card-heading">
          <div>
            <h2>Nội dung hướng dẫn đặt vé</h2>
            <p>
              Nội dung lưu thành công sẽ được công khai trên trang hướng dẫn.
            </p>
          </div>
          <Link to="/booking-guide" target="_blank" rel="noreferrer">
            Mở trang hướng dẫn ↗
          </Link>
        </div>
        <label className="site-field">
          Tiêu đề trang
          <input
            required
            maxLength={160}
            value={title}
            disabled={busy}
            onChange={(event) => {
              setTitle(event.target.value);
              setDirty(true);
              setNotice("");
            }}
          />
        </label>
        <label className="site-field-label">Nội dung</label>
        <GuideEditor
          initialHtml={initial.guideHtml}
          disabled={busy}
          onChange={(value) => {
            setHtml(value);
            setDirty(true);
            setNotice("");
          }}
        />
        <div className="site-form-actions">
          <button
            className="button"
            disabled={busy || !dirty || html.length > 100000}
          >
            {busy ? "Đang xử lý…" : "Lưu hướng dẫn"}
          </button>
          <button
            type="button"
            className="button outline"
            aria-expanded={preview}
            onClick={() => setPreview(!preview)}
          >
            {preview ? "Ẩn xem trước" : "Xem trước"}
          </button>
          <span>
            {html.length.toLocaleString("vi-VN")} / 100.000 ký tự HTML ·{" "}
            {dirty ? "Có thay đổi chưa lưu" : "Đã đồng bộ"}
          </span>
        </div>
        {preview && (
          <section
            className="site-guide-preview"
            aria-label="Xem trước hướng dẫn"
          >
            <h1>{title}</h1>
            <GuideContent html={html} />
          </section>
        )}
      </form>
      <section
        className="site-settings-card"
        aria-labelledby="logo-settings-title"
      >
        <h2 id="logo-settings-title">Logo website</h2>
        <p>
          PNG hoặc JPG tối đa 2 MB; mỗi chiều từ 16 đến 4096 px. Nên dùng ảnh
          ngang, nền trong suốt và tương phản tốt trên nền xanh lẫn nền trắng.
          Ảnh sẽ được giữ đúng tỷ lệ.
        </p>
        <div className="site-logo-previews">
          <div>
            <p>Trên nền xanh</p>
            <div className="site-logo-blue">
              <img src={logo} alt="Xem trước logo trên nền xanh" />
            </div>
          </div>
          <div>
            <p>Trên nền trắng</p>
            <div className="site-logo-white">
              <img
                src={
                  image ||
                  (settings.customLogo ? logo : "/images/vexesieure-logo.svg")
                }
                alt="Xem trước logo trên nền trắng"
              />
            </div>
          </div>
        </div>
        <div className="site-form-actions">
          <label className="button outline site-file-button">
            Chọn ảnh logo
            <input
              type="file"
              accept="image/png,image/jpeg"
              disabled={busy}
              onChange={chooseLogo}
            />
          </label>
          <button
            className="button"
            disabled={busy || !image}
            onClick={() => saveLogo()}
          >
            Lưu logo
          </button>
          {image && (
            <button
              className="button outline"
              disabled={busy}
              onClick={() => setImage("")}
            >
              Bỏ ảnh đã chọn
            </button>
          )}
          <button
            className="button outline"
            disabled={busy || !settings.customLogo}
            onClick={() => saveLogo(true)}
          >
            Khôi phục logo mặc định
          </button>
        </div>
        <p className="editor-help">
          Thay đổi logo hiển thị của website. Biểu tượng tab trình duyệt vẫn
          dùng biểu tượng Vexesieure.
        </p>
      </section>
    </>
  );
}

export default function AdminSiteSettings() {
  const { settings, error, refresh } = useSite();
  return (
    <>
      <header className="admin-heading">
        <p>QUẢN LÝ WEBSITE</p>
        <h1>Nội dung & logo</h1>
        <p>Biên tập hướng dẫn đặt vé và thay đổi nhận diện website.</p>
      </header>
      {!settings && <ErrorNotice error={error} retry={refresh} />}
      {!settings && !error && <Loading />}
      {settings && <SettingsForm initial={settings} />}
    </>
  );
}
