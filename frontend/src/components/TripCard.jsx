import { Link } from "react-router-dom";
import { ArrowRight, BusFront, TrainFront } from "lucide-react";
import { date, duration, money, time } from "../lib/format";

export default function TripCard({ trip }) {
  const VehicleIcon = trip.vehicleType === "TRAIN" ? TrainFront : BusFront;
  return (
    <article className="trip-card">
      <div className="vehicle-info">
        <span className="vehicle-icon">
          <VehicleIcon size={22} />
        </span>
        <div>
          <strong>{trip.vehicleName}</strong>
          <span>
            {trip.vehicleType === "TRAIN" ? "Tàu hỏa" : "Xe khách"} ·{" "}
            {trip.vehicleCode}
          </span>
        </div>
      </div>
      <div className="trip-route">
        <div>
          <strong className="trip-time">{time(trip.departureAt)}</strong>
          <span>{trip.origin}</span>
          <small>{date(trip.departureAt)}</small>
        </div>
        <div className="route-line">
          <small>{duration(trip.departureAt, trip.arrivalAt)}</small>
          <span>
            <i />
            <b />
            <ArrowRight size={16} />
          </span>
          <small>Giờ Việt Nam</small>
        </div>
        <div>
          <strong className="trip-time">{time(trip.arrivalAt)}</strong>
          <span>{trip.destination}</span>
          <small>{date(trip.arrivalAt)}</small>
        </div>
      </div>
      <div className="trip-price">
        <small>Giá mỗi ghế</small>
        <strong>{money(trip.basePrice)}</strong>
        <Link
          className="button"
          to={"/trips/" + trip.id}
          aria-label={"Chọn ghế chuyến " + trip.vehicleCode}
        >
          Chọn ghế <ArrowRight size={16} />
        </Link>
      </div>
    </article>
  );
}
