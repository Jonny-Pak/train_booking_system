import { AlertCircle, ArrowRight, LoaderCircle, SearchX } from "lucide-react";

export function Loading({ text = "Đang tải dữ liệu…" }) {
  return (
    <div className="feedback loading" role="status">
      <LoaderCircle size={24} className="spin" />
      <p>{text}</p>
    </div>
  );
}

export function ErrorNotice({ error, retry }) {
  if (!error) return null;
  return (
    <div className="error-notice" role="alert">
      <AlertCircle size={20} />
      <div>
        <strong>Chưa thể hoàn tất</strong>
        <p>{error.message || error}</p>
        {retry && (
          <button className="text-button" onClick={retry}>
            Thử lại <ArrowRight size={15} />
          </button>
        )}
      </div>
    </div>
  );
}

export function EmptyState({ title, children }) {
  return (
    <div className="feedback empty">
      <div className="empty-icon">
        <SearchX size={30} />
      </div>
      <h2>{title}</h2>
      <div>{children}</div>
    </div>
  );
}
