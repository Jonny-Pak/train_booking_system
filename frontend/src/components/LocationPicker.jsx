import { useEffect, useId, useRef, useState } from "react";
import { MapPin } from "lucide-react";

export default function LocationPicker({
  name,
  value,
  onChange,
  onSelect,
  options,
  open,
  onOpen,
  loading,
  error,
  retry,
  inputRef,
}) {
  const wrapperRef = useRef(null);
  const listRef = useRef(null);
  const listId = useId();
  const [highlight, setHighlight] = useState(-1);
  const active = Math.min(highlight, options.length - 1);
  const origin = name === "origin";

  useEffect(() => {
    if (!open) return;
    function outside(event) {
      if (!wrapperRef.current?.contains(event.target)) onOpen(null);
    }
    document.addEventListener("pointerdown", outside);
    return () => document.removeEventListener("pointerdown", outside);
  }, [open, onOpen]);

  useEffect(() => {
    if (open && active >= 0)
      listRef.current?.children[active]?.scrollIntoView?.({ block: "nearest" });
  }, [open, active]);

  function choose(option) {
    setHighlight(-1);
    onSelect(option);
  }
  function keyDown(event) {
    if (event.key === "Escape") {
      event.preventDefault();
      event.stopPropagation();
      onOpen(null);
    }
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      onOpen(name);
      if (options.length)
        setHighlight(
          event.key === "ArrowDown"
            ? (active + 1) % options.length
            : active <= 0
              ? options.length - 1
              : active - 1,
        );
    }
    if (
      event.key === "Enter" &&
      open &&
      (active >= 0 || options.length === 1)
    ) {
      event.preventDefault();
      choose(options[active >= 0 ? active : 0]);
    }
  }

  return (
    <div
      className={
        "location-field " + (origin ? "origin-picker" : "destination-picker")
      }
      ref={wrapperRef}
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) onOpen(null);
      }}
    >
      <label className={origin ? "departure-field" : "destination-field"}>
        {origin ? (
          <span className="field-symbol">
            <i />
          </span>
        ) : (
          <MapPin size={28} />
        )}
        <span className="field-content">
          <span>{origin ? "Nơi xuất phát" : "Nơi đến"}</span>
          <input
            ref={inputRef}
            name={name}
            aria-label={origin ? "Điểm đi" : "Điểm đến"}
            role="combobox"
            aria-autocomplete="list"
            aria-expanded={open}
            aria-controls={open ? listId : undefined}
            aria-activedescendant={
              open && active >= 0 ? listId + "-" + active : undefined
            }
            autoComplete="off"
            maxLength={160}
            placeholder={origin ? "Chọn điểm đi" : "Chọn điểm đến"}
            value={value}
            onClick={() => onOpen(name)}
            onFocus={() => {
              setHighlight(-1);
              onOpen(name);
            }}
            onChange={(event) => {
              setHighlight(-1);
              onChange(event.target.value);
              onOpen(name);
            }}
            onKeyDown={keyDown}
          />
        </span>
      </label>
      {open && (
        <div className="location-popup">
          <p className="location-popup-title">
            {value.trim() ? "Địa điểm phù hợp" : "Địa điểm có chuyến"}
          </p>
          <div
            ref={listRef}
            id={listId}
            role="listbox"
            aria-label={origin ? "Danh sách điểm đi" : "Danh sách điểm đến"}
            aria-busy={loading}
          >
            {!loading &&
              !error &&
              options.map((option, index) => (
                <button
                  key={option}
                  id={listId + "-" + index}
                  type="button"
                  role="option"
                  tabIndex={-1}
                  aria-selected={
                    active === index || (active < 0 && value === option)
                  }
                  className={active === index ? "highlighted" : ""}
                  onMouseDown={(event) => event.preventDefault()}
                  onClick={() => choose(option)}
                >
                  <MapPin size={20} />
                  <span>{option}</span>
                </button>
              ))}
          </div>
          {loading ? (
            <p className="location-popup-message" role="status">
              Đang tải địa điểm…
            </p>
          ) : error ? (
            <div className="location-popup-message" role="status">
              Chưa tải được địa điểm.
              <button type="button" onClick={retry}>
                Thử lại địa điểm
              </button>
            </div>
          ) : (
            options.length === 0 && (
              <p className="location-popup-message" role="status">
                Không có địa điểm phù hợp với tuyến, ngày hoặc phương tiện đã
                chọn.
              </p>
            )
          )}
        </div>
      )}
    </div>
  );
}
