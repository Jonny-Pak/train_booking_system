import { ArrowLeft, ArrowRight } from "lucide-react";

export default function Pagination({ page, totalPages, onChange }) {
  if (totalPages <= 1) return null;
  return (
    <nav className="pagination" aria-label="Phân trang">
      <button
        className="button secondary small"
        disabled={page <= 0}
        onClick={() => onChange(page - 1)}
      >
        <ArrowLeft size={16} /> Trang trước
      </button>
      <span>
        Trang {page + 1} / {totalPages}
      </span>
      <button
        className="button secondary small"
        disabled={page + 1 >= totalPages}
        onClick={() => onChange(page + 1)}
      >
        Trang sau <ArrowRight size={16} />
      </button>
    </nav>
  );
}
