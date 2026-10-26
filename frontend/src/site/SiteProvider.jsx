import { useEffect, useState } from "react";
import { api } from "../lib/api";
import { SiteContext } from "./SiteContext";

export default function SiteProvider({ children }) {
  const [settings, setSettings] = useState(null);
  const [error, setError] = useState(null);
  const [reload, setReload] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    api("/site/settings", { signal: controller.signal })
      .then((data) => {
        setSettings(data);
        setError(null);
      })
      .catch((failure) => {
        if (failure.name !== "AbortError") setError(failure);
      });
    return () => controller.abort();
  }, [reload]);
  return (
    <SiteContext.Provider
      value={{
        settings,
        setSettings,
        error,
        refresh: () => setReload((value) => value + 1),
      }}
    >
      {children}
    </SiteContext.Provider>
  );
}
