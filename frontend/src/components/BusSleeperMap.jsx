import { CircleGauge } from "lucide-react";

export default function BusSleeperMap({ seats, renderSeat }) {
  return (
    <div
      className="bus-sleeper-layout"
      role="group"
      aria-label="Sơ đồ xe giường nằm 34 chỗ"
    >
      <p className="vehicle-front">XE GIƯỜNG NẰM · 34 CHỖ · 2 TẦNG</p>
      <div className="bus-driver">
        <CircleGauge size={26} />
        <span>Tài xế · Đầu xe</span>
      </div>
      {[1, 2].map((deck) => {
        const beds = seats
          .filter((seat) => seat.deckNumber === deck)
          .sort((a, b) => a.positionNumber - b.positionNumber);
        return (
          <section
            className="bus-sleeper-deck"
            key={deck}
            role="group"
            aria-label={"Tầng " + deck + ", " + beds.length + " giường"}
          >
            <h3>Tầng {deck}</h3>
            <p>
              {deck === 1 ? "Tầng dưới" : "Tầng trên"} · {beds.length} giường
            </p>
            {Array.from({ length: Math.ceil(beds.length / 2) }, (_, row) => (
              <div className="sleeper-bus-row" key={row}>
                {renderSeat(beds[row * 2])}
                <span className="bus-aisle" aria-hidden="true">
                  {row === 4 ? "LỐI ĐI" : ""}
                </span>
                {renderSeat(beds[row * 2 + 1])}
              </div>
            ))}
          </section>
        );
      })}
    </div>
  );
}
