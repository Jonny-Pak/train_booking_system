import { createContext, useContext } from "react";

export const SiteContext = createContext(null);
export function useSite() {
  return useContext(SiteContext);
}
