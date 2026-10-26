import { useId, useState } from "react";
import { Armchair, BedSingle, CircleGauge, TrainFront } from "lucide-react";
import BusSleeperMap from "./BusSleeperMap";
import {
  coachList,
  seatedColumn,
  seatDescription,
  seatClassLabels,
} from "../lib/seats";

function SeatButton({ seat, selected, busy, onToggle }) {
  if (!seat) return <span className="missing-position" aria-hidden="true" />;
  const picked = selected.includes(seat.id);
  const label = { AVAILABLE: "còn trống", HELD: "đang giữ", BOOKED: "đã đặt" }[
    seat.status
  ];
  const description =
    seat.coachNumber == null ? "Ghế " + seat.seatCode : seatDescription(seat);
  const Icon =
    seat.compartmentNumber || seat.seatClass === "BUS_SLEEPER"
      ? BedSingle
      : Armchair;
  return (
    <button
      type="button"
      className={"seat " + (picked ? "selected" : seat.status.toLowerCase())}
      disabled={busy || seat.status !== "AVAILABLE"}
      aria-pressed={picked}
      aria-label={description + ", " + label}
      title={
        description +
        " · " +
        (seatClassLabels[seat.seatClass] || seat.seatClass)
      }
      onClick={() => onToggle(seat.id)}
    >
      <Icon size={22} />
      <span>{seat.positionNumber ?? seat.seatCode}</span>
    </button>
  );
}

export default function SeatMap({ seats, selected, busy, onToggle }) {
  const coaches = coachList(seats);
  const [coachNumber, setCoachNumber] = useState(
    () => seats.find((seat) => selected.includes(seat.id))?.coachNumber || 1,
  );
  const panelId = useId();
  function button(seat) {
    return (
      <SeatButton
        key={seat?.id}
        seat={seat}
        selected={selected}
        busy={busy}
        onToggle={onToggle}
      />
    );
  }
  if (seats[0]?.coachNumber === 0) {
    if (seats[0].seatClass === "BUS_SLEEPER")
      return <BusSleeperMap seats={seats} renderSeat={button} />;
    const positions = new Map(seats.map((seat) => [seat.positionNumber, seat]));
    return (
      <div
        className="bus-layout"
        aria-label={"Sơ đồ xe khách " + seats.length + " ghế"}
      >
        <p className="vehicle-front">ĐẦU XE · {seats.length} GHẾ HÀNH KHÁCH</p>
        <div className="bus-driver">
          <span>
            <CircleGauge size={30} />
            Tài xế
          </span>
        </div>
        {Array.from({ length: Math.ceil(seats.length / 4) }, (_, row) => (
          <div className="bus-row" key={row}>
            {button(positions.get(1 + row * 4))}
            {button(positions.get(2 + row * 4))}
            <span className="bus-aisle" aria-hidden="true">
              {row === 4 ? "LỐI ĐI" : ""}
            </span>
            {button(positions.get(3 + row * 4))}
            {button(positions.get(4 + row * 4))}
          </div>
        ))}
      </div>
    );
  }
  if (coaches.length === 0) {
    return (
      <div className="seat-cabin">
        <div className="cabin-front">
          <Armchair size={22} />
          <span>SƠ ĐỒ GHẾ HIỆN CÓ</span>
        </div>
        <div className="seat-grid" aria-label="Sơ đồ ghế">
          {seats.map(button)}
        </div>
        <p className="seat-footnote">
          Phương tiện cũ chưa có dữ liệu vị trí toa/khoang/tầng.
        </p>
      </div>
    );
  }
  const activeCoach =
    coaches.find((coach) => coach.number === coachNumber) || coaches[0];
  const current = seats.filter(
    (seat) => seat.coachNumber === activeCoach.number,
  );
  const positions = new Map(current.map((seat) => [seat.positionNumber, seat]));
  const sleeper = activeCoach.seatClass !== "SEATED";
  const decks = activeCoach.seatClass === "SLEEPER_6" ? [3, 2, 1] : [2, 1];
  const perRoom = activeCoach.seatClass === "SLEEPER_6" ? 6 : 4;
  return (
    <div className="train-layout">
      <div className="coach-selector" role="tablist" aria-label="Chọn toa tàu">
        {coaches.map((coach) => {
          const count = seats.filter(
            (seat) =>
              seat.coachNumber === coach.number && selected.includes(seat.id),
          ).length;
          return (
            <button
              type="button"
              role="tab"
              key={coach.number}
              aria-selected={coach.number === activeCoach.number}
              aria-controls={panelId}
              tabIndex={coach.number === activeCoach.number ? 0 : -1}
              disabled={busy}
              onClick={() => setCoachNumber(coach.number)}
              onKeyDown={(event) => {
                if (event.key !== "ArrowLeft" && event.key !== "ArrowRight")
                  return;
                event.preventDefault();
                const index = coaches.findIndex(
                  (item) => item.number === coach.number,
                );
                const next =
                  (index +
                    (event.key === "ArrowRight" ? 1 : -1) +
                    coaches.length) %
                  coaches.length;
                setCoachNumber(coaches[next].number);
                const tabs =
                  event.currentTarget.parentElement.querySelectorAll(
                    '[role="tab"]',
                  );
                tabs[next].focus();
              }}
            >
              <TrainFront size={22} />
              <strong>Toa {coach.number}</strong>
              <small>
                {coach.seatClass === "SEATED"
                  ? "Ghế ngồi"
                  : coach.seatClass === "SLEEPER_4"
                    ? "Giường 4"
                    : "Giường 6"}
              </small>
              {count > 0 && (
                <span className="coach-picked">{count} đã chọn</span>
              )}
            </button>
          );
        })}
      </div>
      <section
        role="tabpanel"
        id={panelId}
        aria-label={"Toa " + activeCoach.number}
      >
        <h3 className="coach-title">
          Toa {activeCoach.number} · {seatClassLabels[activeCoach.seatClass]} ·{" "}
          {current.length} chỗ
        </h3>
        <p className="seat-footnote">
          {sleeper
            ? "7 khoang · " +
              decks.length +
              " tầng · Hai giường mỗi tầng trong mỗi khoang."
            : "56 ghế ngồi · Lối đi giữa hai dãy ghế."}
        </p>
        <div
          className="seat-map-scroll"
          tabIndex={0}
          aria-label="Sơ đồ toa, cuộn ngang để xem đủ chỗ"
        >
          {sleeper ? (
            <div className="sleeper-coach">
              <div className="deck-labels">
                <span aria-hidden="true">&nbsp;</span>
                {decks.map((deck) => (
                  <strong key={deck}>Tầng {deck}</strong>
                ))}
              </div>
              {Array.from({ length: 7 }, (_, room) => (
                <div className="train-room" key={room}>
                  <p>Khoang {room + 1}</p>
                  {decks.map((deck) => (
                    <div className="berth-pair" key={deck}>
                      {button(
                        positions.get(room * perRoom + (deck - 1) * 2 + 1),
                      )}
                      {button(
                        positions.get(room * perRoom + (deck - 1) * 2 + 2),
                      )}
                    </div>
                  ))}
                </div>
              ))}
            </div>
          ) : (
            <div className="seated-coach">
              {Array.from({ length: 14 }, (_, column) => (
                <div className="train-seat-column" key={column}>
                  {seatedColumn(column).map((number, row) => (
                    <div
                      key={number}
                      className={row === 2 ? "after-train-aisle" : ""}
                    >
                      {button(positions.get(number))}
                    </div>
                  ))}
                </div>
              ))}
            </div>
          )}
        </div>
      </section>
    </div>
  );
}
