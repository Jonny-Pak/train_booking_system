export default function AdminPagination({ data, page, setPage }) {
  return (
    <div className="admin-pagination">
      <button
        className="button secondary small"
        disabled={page === 0}
        onClick={() => setPage(page - 1)}
      >
        Trang trước
      </button>
      <span>
        Trang {data.totalPages === 0 ? 0 : page + 1} / {data.totalPages}
      </span>
      <button
        className="button secondary small"
        disabled={page + 1 >= data.totalPages}
        onClick={() => setPage(page + 1)}
      >
        Trang sau
      </button>
    </div>
  );
}
