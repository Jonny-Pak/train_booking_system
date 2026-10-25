import { describe, expect, it } from "vitest";
import { readSession, writeSession } from "./session";

describe("Per-tab session", () => {
  it("discards expired and malformed storage", () => {
    sessionStorage.setItem("len-duong-session", "{bad-json");
    expect(readSession()).toBeNull();
    writeSession({ accessToken: "expired", expiresAt: "2000-01-01T00:00:00Z" });
    expect(readSession()).toBeNull();
    expect(sessionStorage.getItem("len-duong-session")).toBeNull();
  });
  it("keeps a valid session and clears it on logout", () => {
    const session = {
      accessToken: "test-token",
      expiresAt: "2099-01-01T00:00:00Z",
      user: { id: 1 },
    };
    writeSession(session);
    expect(readSession()).toEqual(session);
    writeSession(null);
    expect(readSession()).toBeNull();
  });
});
