import { describe, expect, it, vi } from "vitest";
import { catalogBody, catalogOptions, vietnamInput } from "./catalog";

describe("admin catalog", () => {
  it("converts Vietnam wall time to UTC without using the browser timezone", () => {
    expect(vietnamInput("2030-01-02T01:30:00Z")).toBe("2030-01-02T08:30");
    expect(
      catalogBody("trips", {
        routeId: "1",
        vehicleId: "2",
        basePrice: "150000.50",
        status: "SCHEDULED",
        departureAt: "2030-01-02T08:30",
        arrivalAt: "2030-01-02T10:30",
      }),
    ).toEqual({
      routeId: 1,
      vehicleId: 2,
      basePrice: 150000.5,
      status: "SCHEDULED",
      departureAt: "2030-01-02T01:30:00.000Z",
      arrivalAt: "2030-01-02T03:30:00.000Z",
    });
  });
  it("sends only allowed fields and retains inactive values", () => {
    expect(
      catalogBody("routes", {
        id: 10,
        origin: " Hà Nội ",
        destination: " Hải Phòng ",
        active: false,
      }),
    ).toEqual({ origin: "Hà Nội", destination: "Hải Phòng", active: false });
  });
  it("loads selectors beyond the first page", async () => {
    const api = vi
      .fn()
      .mockResolvedValueOnce({ content: [{ id: 1 }], totalPages: 2 })
      .mockResolvedValueOnce({ content: [{ id: 101 }], totalPages: 2 });
    expect(await catalogOptions(api, "/admin/routes", {})).toEqual([
      { id: 1 },
      { id: 101 },
    ]);
    expect(api).toHaveBeenLastCalledWith("/admin/routes?size=100&page=1", {});
  });
});
