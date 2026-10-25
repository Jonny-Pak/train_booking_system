import { statusLabels } from "../lib/format";

export default function StatusBadge({ status }) {
  return (
    <span className={"status-badge status-" + status.toLowerCase()}>
      <i />
      {statusLabels[status] || status}
    </span>
  );
}
