import { expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { AuthContext } from "./AuthContext";
import AuthPage from "../pages/AuthPage";

function Destination() {
  const location = useLocation();
  return (
    <output data-testid="selection">
      {JSON.stringify(location.state?.seatIds)}
    </output>
  );
}

it("preserves selected seats when an authenticated login page redirects back", async () => {
  render(
    <MemoryRouter
      initialEntries={[
        { pathname: "/login", state: { from: "/trips/12", seatIds: [3, 4] } },
      ]}
    >
      <AuthContext.Provider value={{ user: { id: 1 }, notice: "" }}>
        <Routes>
          <Route path="/login" element={<AuthPage />} />
          <Route path="/trips/12" element={<Destination />} />
        </Routes>
      </AuthContext.Provider>
    </MemoryRouter>,
  );
  expect(await screen.findByTestId("selection")).toHaveTextContent("[3,4]");
});
