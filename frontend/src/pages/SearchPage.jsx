import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import {
  ArrowDownUp,
  ArrowRight,
  BusFront,
  CalendarDays,
  Search,
  ShieldCheck,
  CheckCircle2,
  Clock3,
  Headphones,
  MousePointer2,
  Ticket,
  TrainFront,
} from "lucide-react";
import { api, tripQuery } from "../lib/api";
import { canonicalLocation, locationOptions } from "../lib/locations";
import LocationPicker from "../components/LocationPicker";
import TripCard from "../components/TripCard";
import Pagination from "../components/Pagination";
import { EmptyState, ErrorNotice, Loading } from "../components/Feedback";

function filtersFrom(params) {
  return {
    origin: params.get("origin") || "",
    destination: params.get("destination") || "",
    date: params.get("date") || "",
    vehicleType: params.get("vehicleType") || "",
  };
}

export default function SearchPage() {
  const [params, setParams] = useSearchParams();
  const [filters, setFilters] = useState(() => filtersFrom(params));
  const [results, setResults] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [reload, setReload] = useState(0);
  const [locationRoutes, setLocationRoutes] = useState([]);
  const [locationLoading, setLocationLoading] = useState(true);
  const [locationError, setLocationError] = useState(null);
  const [locationReload, setLocationReload] = useState(0);
  const [activeLocation, setActiveLocation] = useState(null);
  const destinationRef = useRef(null);
  const queryString = params.toString();
  const pageNumber = Number(params.get("page") || 0);
  const page = Number.isInteger(pageNumber) && pageNumber >= 0 ? pageNumber : 0;

  useEffect(() => {
    const currentParams = new URLSearchParams(queryString);
    const currentFilters = filtersFrom(currentParams);
    setFilters(currentFilters);
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    api(tripQuery(currentFilters, page), { signal: controller.signal })
      .then(setResults)
      .catch((failure) => {
        if (failure.name !== "AbortError") setError(failure);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [queryString, page, reload]);

  useEffect(() => {
    const controller = new AbortController();
    const query = new URLSearchParams();
    if (filters.vehicleType) query.set("vehicleType", filters.vehicleType);
    if (filters.date) query.set("date", filters.date);
    setLocationLoading(true);
    setLocationError(null);
    setLocationRoutes([]);
    api("/trips/locations?" + query, { signal: controller.signal })
      .then((data) => {
        if (!controller.signal.aborted) setLocationRoutes(data);
      })
      .catch((failure) => {
        if (failure.name !== "AbortError") setLocationError(failure);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLocationLoading(false);
      });
    return () => controller.abort();
  }, [filters.vehicleType, filters.date, locationReload]);

  function update(key, value) {
    setFilters({ ...filters, [key]: value });
  }

  function search(event) {
    event.preventDefault();
    setActiveLocation(null);
    const next = new URLSearchParams();
    for (const [key, value] of Object.entries(filters)) {
      const text =
        key === "origin" || key === "destination"
          ? canonicalLocation(locationRoutes, key, value)
          : value.trim();
      if (text) next.set(key, text);
    }
    setParams(next);
    setReload((value) => value + 1);
  }

  function changePage(nextPage) {
    const next = new URLSearchParams(params);
    next.set("page", String(nextPage));
    setParams(next);
    document
      .getElementById("results")
      ?.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  function swap() {
    setActiveLocation(null);
    setFilters({
      ...filters,
      origin: filters.destination,
      destination: filters.origin,
    });
  }

  function chooseOrigin(value) {
    const compatible = locationRoutes.some(
      (route) =>
        route.origin === value && route.destination === filters.destination,
    );
    setFilters({
      ...filters,
      origin: value,
      destination: compatible ? filters.destination : "",
    });
    setActiveLocation("destination");
    destinationRef.current?.focus();
  }

  function chooseRoute(trip) {
    const next = new URLSearchParams(params);
    next.set("origin", trip.origin);
    next.set("destination", trip.destination);
    next.delete("page");
    setParams(next);
    setReload((value) => value + 1);
    document.getElementById("results")?.scrollIntoView({ behavior: "smooth" });
  }

  const isTrain = filters.vehicleType === "TRAIN";
  const suggestedRoutes = [];
  const seenRoutes = new Set();
  for (const trip of results?.content || []) {
    const key = trip.origin + " → " + trip.destination;
    if (!seenRoutes.has(key)) {
      seenRoutes.add(key);
      suggestedRoutes.push(trip);
    }
  }

  return (
    <>
      <section
        className={
          "reference-hero " + (isTrain ? "railway-hero" : "coach-hero")
        }
      >
        <div className="reference-hero-inner">
          <div className="reference-heading">
            {isTrain ? (
              <>
                <span>KHÁM PHÁ VIỆT NAM QUA Ô CỬA TÀU</span>
                <h1>Đặt vé tàu hỏa online</h1>
              </>
            ) : (
              <>
                <span>CÙNG BẠN TRÊN MỌI HÀNH TRÌNH</span>
                <h1>
                  Chuyến đi mới,
                  <br />
                  <em>bắt đầu cùng Lên Đường</em>
                </h1>
              </>
            )}
          </div>
          <section className="reference-search" aria-label="Tìm chuyến">
            <form onSubmit={search}>
              <div
                className="reference-tabs"
                role="group"
                aria-label="Loại phương tiện"
              >
                <button
                  type="button"
                  aria-pressed={filters.vehicleType === ""}
                  className={!filters.vehicleType ? "active" : ""}
                  onClick={() => update("vehicleType", "")}
                >
                  <Ticket size={23} />
                  Tất cả chuyến
                </button>
                <button
                  type="button"
                  aria-pressed={filters.vehicleType === "BUS"}
                  className={filters.vehicleType === "BUS" ? "active" : ""}
                  onClick={() => update("vehicleType", "BUS")}
                >
                  <BusFront size={25} />
                  Xe khách
                </button>
                <button
                  type="button"
                  aria-pressed={isTrain}
                  className={isTrain ? "active" : ""}
                  onClick={() => update("vehicleType", "TRAIN")}
                >
                  <TrainFront size={25} />
                  Tàu hỏa
                </button>
              </div>
              <div className="reference-fields">
                <LocationPicker
                  name="origin"
                  value={filters.origin}
                  onChange={(value) => update("origin", value)}
                  onSelect={chooseOrigin}
                  options={locationOptions(
                    locationRoutes,
                    "origin",
                    "",
                    filters.origin,
                  )}
                  open={activeLocation === "origin"}
                  onOpen={setActiveLocation}
                  loading={locationLoading}
                  error={locationError}
                  retry={() => setLocationReload(locationReload + 1)}
                />
                <button
                  type="button"
                  className="reference-swap"
                  onClick={swap}
                  aria-label="Đổi điểm đi và điểm đến"
                >
                  <ArrowDownUp size={19} />
                </button>
                <LocationPicker
                  name="destination"
                  value={filters.destination}
                  inputRef={destinationRef}
                  onChange={(value) => update("destination", value)}
                  onSelect={(value) => {
                    update("destination", value);
                    setActiveLocation(null);
                  }}
                  options={locationOptions(
                    locationRoutes,
                    "destination",
                    filters.origin,
                    filters.destination,
                  )}
                  open={activeLocation === "destination"}
                  onOpen={setActiveLocation}
                  loading={locationLoading}
                  error={locationError}
                  retry={() => setLocationReload(locationReload + 1)}
                />
                <label className="date-field">
                  <CalendarDays size={26} />
                  <span className="field-content">
                    <span>Ngày đi</span>
                    <input
                      aria-label="Ngày khởi hành"
                      type="date"
                      name="date"
                      value={filters.date}
                      onChange={(event) => update("date", event.target.value)}
                    />
                  </span>
                </label>
                <button type="submit" className="reference-submit">
                  <Search size={19} />
                  Tìm kiếm
                </button>
              </div>
              <div className="reference-search-note">
                <CheckCircle2 size={15} />
                <span>Vé một chiều · Chọn ghế ở bước tiếp theo</span>
                <small>Để trống ngày để xem tất cả chuyến sắp khởi hành</small>
              </div>
            </form>
          </section>
        </div>
      </section>

      <section className="reference-benefits" aria-label="Tiện ích đặt vé">
        <div>
          <ShieldCheck />
          <span>Giá vé rõ ràng</span>
        </div>
        <div>
          <MousePointer2 />
          <span>Chọn ghế trực tuyến</span>
        </div>
        <div>
          <Clock3 />
          <span>Giữ chỗ có thời hạn</span>
        </div>
        <Link to="/booking-guide">
          <Headphones />
          <span>Hướng dẫn đặt vé</span>
        </Link>
      </section>

      <div className="home-content">
        {isTrain && (
          <section className="railway-intro">
            <div className="railway-intro-heading">
              <div>
                <span>
                  <ShieldCheck size={18} />
                  CHỦ ĐỘNG TRÊN MỌI HÀNH TRÌNH
                </span>
                <h2>Đặt vé tàu hỏa an tâm cùng Lên Đường</h2>
              </div>
              <TrainFront size={47} />
            </div>
            <div className="railway-intro-items">
              <div>
                <strong>Chuyến đi phù hợp</strong>
                <p>Tra cứu tuyến và giờ khởi hành.</p>
              </div>
              <div>
                <strong>Chỗ ngồi bạn chọn</strong>
                <p>Xem trạng thái ghế trước khi đặt.</p>
              </div>
              <div>
                <strong>Đơn hàng trong tay</strong>
                <p>Theo dõi, quản lý đơn trực tuyến.</p>
              </div>
            </div>
          </section>
        )}
        {!loading && !error && suggestedRoutes.length > 0 && (
          <section
            className="suggested-routes"
            aria-labelledby="suggested-title"
          >
            <div className="reference-section-heading">
              <h2 id="suggested-title">Tuyến đường gợi ý</h2>
              <span>Khám phá các chuyến đang mở bán</span>
            </div>
            <div className="suggested-grid">
              {suggestedRoutes.map((trip) => (
                <button
                  type="button"
                  className="suggested-card"
                  key={trip.routeId}
                  onClick={() => chooseRoute(trip)}
                >
                  <div
                    className={
                      trip.vehicleType === "TRAIN"
                        ? "route-photo railway-photo"
                        : "route-photo coach-photo"
                    }
                  >
                    <span>
                      {trip.vehicleType === "TRAIN" ? "Tàu hỏa" : "Xe khách"}
                    </span>
                  </div>
                  <div className="suggested-card-body">
                    <strong>
                      {trip.origin} <ArrowRight size={16} /> {trip.destination}
                    </strong>
                    <span>
                      Xem chuyến đang mở bán <ArrowRight size={15} />
                    </span>
                  </div>
                </button>
              ))}
            </div>
          </section>
        )}

        <section
          className="results-section"
          id="results"
          aria-labelledby="results-title"
        >
          <div className="reference-section-heading">
            <div>
              <h2 id="results-title">Chuyến đi dành cho bạn</h2>
              <p>Nhập đầy đủ tên điểm đi, điểm đến để tìm chuyến phù hợp.</p>
            </div>
            {!loading && !error && (
              <span className="reference-result-count">
                {results?.totalElements || 0} chuyến phù hợp
              </span>
            )}
          </div>
          {loading ? (
            <Loading text="Đang tìm những hành trình phù hợp…" />
          ) : error ? (
            <ErrorNotice
              error={error}
              retry={() => setReload((value) => value + 1)}
            />
          ) : results?.content.length ? (
            <>
              <div className="trip-list">
                {results.content.map((trip) => (
                  <TripCard key={trip.id} trip={trip} />
                ))}
              </div>
              <Pagination
                page={page}
                totalPages={results.totalPages}
                onChange={changePage}
              />
            </>
          ) : (
            <EmptyState title="Chưa tìm thấy chuyến phù hợp">
              <p>
                Thử đổi ngày, loại phương tiện hoặc bỏ bớt điều kiện tìm kiếm.
              </p>
              <button
                className="button secondary small"
                onClick={() => {
                  setParams({});
                  setReload((value) => value + 1);
                }}
              >
                Xem tất cả chuyến
              </button>
            </EmptyState>
          )}
        </section>
        <section className="reference-guide" id="booking-guide">
          <h2>Đặt vé dễ dàng chỉ với 3 bước</h2>
          <Link className="guide-detail-link" to="/booking-guide">
            Xem hướng dẫn đầy đủ: đăng ký, chọn chỗ, thanh toán và nhận vé →
          </Link>
          <div className="reference-guide-grid">
            <div>
              <span>01</span>
              <h3>Tìm chuyến đi</h3>
              <p>Chọn điểm đi, điểm đến và ngày khởi hành phù hợp.</p>
            </div>
            <div>
              <span>02</span>
              <h3>Chọn ghế & giữ chỗ</h3>
              <p>Đăng nhập, chọn ghế còn trống và xác nhận đặt chỗ.</p>
            </div>
            <div>
              <span>03</span>
              <h3>Quản lý đơn hàng</h3>
              <p>Xem chi tiết, thời gian giữ ghế và hủy đơn khi cần.</p>
            </div>
          </div>
        </section>
      </div>
    </>
  );
}
