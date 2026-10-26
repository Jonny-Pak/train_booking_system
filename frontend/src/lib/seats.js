export const seatClassLabels = {
  STANDARD: "Ghế ngồi",
  BUS_SLEEPER: "Giường nằm xe khách",
  SEATED: "Ghế ngồi mềm",
  SLEEPER_4: "Giường nằm khoang 4",
  SLEEPER_6: "Giường nằm khoang 6",
};

export function seatDescription(seat) {
  if (seat.coachNumber == null) return seat.seatCode;
  if (seat.coachNumber === 0) {
    if (seat.seatClass === "BUS_SLEEPER")
      return "Giường " + seat.positionNumber + " · Tầng " + seat.deckNumber;
    return "Ghế " + seat.positionNumber;
  }
  let text =
    (seat.compartmentNumber ? "Giường " : "Ghế ") +
    seat.positionNumber +
    " · Toa " +
    seat.coachNumber;
  if (seat.compartmentNumber)
    text +=
      " · Khoang " + seat.compartmentNumber + " · Tầng " + seat.deckNumber;
  return text;
}

export function seatedColumn(column) {
  const first = column * 4 + 1;
  return column % 2 === 0
    ? [first, first + 1, first + 2, first + 3]
    : [first + 3, first + 2, first + 1, first];
}

export function coachList(seats) {
  const coaches = [];
  for (const seat of seats) {
    if (
      seat.coachNumber > 0 &&
      !coaches.some((coach) => coach.number === seat.coachNumber)
    ) {
      coaches.push({ number: seat.coachNumber, seatClass: seat.seatClass });
    }
  }
  return coaches.sort((a, b) => a.number - b.number);
}
