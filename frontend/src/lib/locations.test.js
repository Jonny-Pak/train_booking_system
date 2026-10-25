import { expect, it } from "vitest";
import { canonicalLocation, locationOptions } from "./locations";

const routes = [
  { origin: "Hà Nội", destination: "Hải Phòng" },
  { origin: "Hà Nội", destination: "Đà Nẵng" },
  { origin: "Hà Nội", destination: "Hải Phòng" },
  { origin: "Hồ Chí Minh", destination: "Phan Thiết" },
];

it("filters Vietnamese locations without accents and preserves canonical names", () => {
  expect(locationOptions(routes, "destination", "Hà Nội", "da nang")).toEqual([
    "Đà Nẵng",
  ]);
  expect(canonicalLocation(routes, "origin", " HA NOI ")).toBe("Hà Nội");
  expect(canonicalLocation(routes, "origin", " Nơi chưa có tuyến ")).toBe(
    "Nơi chưa có tuyến",
  );
});

it("deduplicates places and limits choices to the selected opposite stop", () => {
  expect(locationOptions(routes, "origin", "", "")).toEqual([
    "Hà Nội",
    "Hồ Chí Minh",
  ]);
  expect(locationOptions(routes, "destination", "Hà Nội", "")).toEqual([
    "Đà Nẵng",
    "Hải Phòng",
  ]);
  expect(locationOptions(routes, "origin", "Phan Thiết", "")).toEqual([
    "Hồ Chí Minh",
  ]);
  expect(locationOptions([], "origin", "", "")).toEqual([]);
});
