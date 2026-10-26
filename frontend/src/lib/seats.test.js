import { describe, expect, it } from "vitest";
import { coachList, seatedColumn, seatDescription } from "./seats";

describe("seat positions", () => {
  it("matches seated coach zigzag numbering in the reference", () => {
    expect(seatedColumn(0)).toEqual([1, 2, 3, 4]);
    expect(seatedColumn(1)).toEqual([8, 7, 6, 5]);
    expect(seatedColumn(13)).toEqual([56, 55, 54, 53]);
    expect(
      new Set(
        Array.from({ length: 14 }, (_, column) => seatedColumn(column)).flat(),
      ).size,
    ).toBe(56);
  });
  it("sorts coaches numerically and keeps legacy seats readable", () => {
    expect(
      coachList([
        { coachNumber: 11, seatClass: "SLEEPER_6" },
        { coachNumber: 1, seatClass: "SEATED" },
        { coachNumber: 11 },
      ]),
    ).toEqual([
      { number: 1, seatClass: "SEATED" },
      { number: 11, seatClass: "SLEEPER_6" },
    ]);
    expect(seatDescription({ seatCode: "A1" })).toBe("A1");
    expect(seatDescription({ coachNumber: 0, positionNumber: 16 })).toBe(
      "Ghế 16",
    );
    expect(seatDescription({ coachNumber: 0, positionNumber: 34 })).toBe(
      "Ghế 34",
    );
    expect(
      seatDescription({
        coachNumber: 0,
        positionNumber: 34,
        deckNumber: 2,
        seatClass: "BUS_SLEEPER",
      }),
    ).toBe("Giường 34 · Tầng 2");
    expect(
      seatDescription({
        coachNumber: 7,
        compartmentNumber: 7,
        deckNumber: 3,
        positionNumber: 42,
      }),
    ).toBe("Giường 42 · Toa 7 · Khoang 7 · Tầng 3");
  });
});
