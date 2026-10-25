import { describe, expect, it } from "vitest";
import {
  date,
  displayStatus,
  duration,
  remaining,
  safeReturnPath,
  time,
  validPassword,
} from "./format";

describe("Booking deadline and Vietnam time display", () => {
  const end = "2030-01-01T00:10:00Z";
  it("shows pending strictly before the deadline", () => {
    expect(
      displayStatus(
        { status: "PENDING", holdExpiresAt: end },
        Date.parse(end) - 1,
      ),
    ).toBe("PENDING");
  });
  it("shows expiry exactly at the deadline even before scheduler updates status", () => {
    expect(
      displayStatus({ status: "PENDING", holdExpiresAt: end }, Date.parse(end)),
    ).toBe("EXPIRED");
  });
  it("honors server expiry and never marks paid bookings expired", () => {
    expect(
      displayStatus(
        { status: "PENDING", holdExpired: true, holdExpiresAt: end },
        0,
      ),
    ).toBe("EXPIRED");
    expect(
      displayStatus(
        { status: "PAID", holdExpiresAt: end },
        Date.parse(end) + 1000,
      ),
    ).toBe("PAID");
  });
  it("countdown never becomes negative", () => {
    expect(remaining(end, Date.parse(end) + 1000)).toBe("00:00");
    expect(remaining(end, Date.parse(end) - 61000)).toBe("01:01");
  });
  it("formats the correct Vietnam calendar day across UTC midnight boundaries", () => {
    expect(date("2030-01-01T18:00:00Z")).toBe("02/01/2030");
    expect(time("2030-01-01T18:00:00Z")).toBe("01:00");
    expect(duration("2030-01-01T18:00:00Z", "2030-01-01T20:30:00Z")).toBe(
      "2 giờ 30 phút",
    );
  });
});

describe("Form and redirect safety", () => {
  it("does not accept external return URLs", () => {
    expect(safeReturnPath("https://example.com")).toBe("/");
    expect(safeReturnPath("//example.com")).toBe("/");
    expect(safeReturnPath("/trips/12")).toBe("/trips/12");
  });
  it("uses UTF-8 bytes for BCrypt length and preserves whitespace", () => {
    expect(validPassword("abc12345")).toBe(true);
    expect(validPassword("1234567")).toBe(false);
    expect(validPassword("ắ".repeat(25))).toBe(false);
    expect(validPassword(" ".repeat(8))).toBe(false);
    expect(validPassword("1234567 ")).toBe(true);
  });
});
