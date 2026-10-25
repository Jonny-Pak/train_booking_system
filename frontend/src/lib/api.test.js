import { afterEach, describe, expect, it, vi } from "vitest";
import { api, tripQuery } from "./api";

afterEach(() => vi.unstubAllGlobals());

describe("API client", () => {
  it("sends Bearer headers and the exact booking body without cookies", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue({
        ok: true,
        status: 201,
        json: async () => ({ id: 9 }),
      });
    vi.stubGlobal("fetch", fetchMock);
    await api("/bookings", {
      method: "POST",
      token: "test-token",
      body: { tripId: 1, seatIds: [2] },
    });
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/v1/bookings");
    expect(options.credentials).toBe("omit");
    expect(options.headers.Authorization).toBe("Bearer test-token");
    expect(JSON.parse(options.body)).toEqual({ tripId: 1, seatIds: [2] });
  });
  it("does not attach a token to public or login requests", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue({ ok: true, status: 200, json: async () => ({}) });
    vi.stubGlobal("fetch", fetchMock);
    await api("/trips");
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined();
  });
  it("translates seat conflict and does not automatically retry POST", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 409,
      json: async () => ({ code: "SEAT_UNAVAILABLE" }),
    });
    vi.stubGlobal("fetch", fetchMock);
    await expect(
      api("/bookings", { method: "POST", body: {} }),
    ).rejects.toMatchObject({ status: 409, code: "SEAT_UNAVAILABLE" });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
  it("expires a protected session on 401 with the token that failed", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue({ ok: false, status: 401, json: async () => ({}) }),
    );
    const onExpired = vi.fn();
    window.addEventListener("session-expired", onExpired);
    try {
      await expect(
        api("/bookings/my", { token: "expired-token" }),
      ).rejects.toMatchObject({ status: 401 });
      expect(onExpired.mock.calls[0][0].detail).toBe("expired-token");
    } finally {
      window.removeEventListener("session-expired", onExpired);
    }
  });
  it("preserves AbortError so cancelled requests do not show network errors", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockRejectedValue(new DOMException("cancelled", "AbortError")),
    );
    await expect(api("/trips")).rejects.toMatchObject({ name: "AbortError" });
  });
  it("reports offline and invalid JSON responses clearly", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("offline")));
    await expect(api("/trips")).rejects.toMatchObject({
      code: "NETWORK_ERROR",
    });
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => {
          throw new SyntaxError();
        },
      }),
    );
    await expect(api("/trips")).rejects.toThrow("dữ liệu không hợp lệ");
  });
  it("omits blank filters and encodes Vietnamese endpoint names", () => {
    const query = tripQuery(
      { origin: " Hà Nội ", destination: "", date: "", vehicleType: "BUS" },
      2,
    );
    const params = new URLSearchParams(query.split("?")[1]);
    expect(params.get("origin")).toBe("Hà Nội");
    expect(params.has("date")).toBe(false);
    expect(params.has("destination")).toBe(false);
    expect(params.get("page")).toBe("2");
  });
});
