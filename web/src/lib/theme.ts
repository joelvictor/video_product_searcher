import { useCallback, useEffect, useState } from "react";

export type ThemeMode = "light" | "dark" | "contrast";
const KEY = "vps-theme";

function read(): ThemeMode {
  try {
    const v = localStorage.getItem(KEY);
    if (v === "light" || v === "dark" || v === "contrast") return v;
  } catch { /* storage indisponível */ }
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function apply(mode: ThemeMode) {
  const root = document.documentElement;
  root.classList.toggle("dark", mode === "dark" || mode === "contrast");
  root.classList.toggle("contrast", mode === "contrast");
}

export function useTheme() {
  const [mode, setModeState] = useState<ThemeMode>(read);
  useEffect(() => apply(mode), [mode]);
  const setMode = useCallback((m: ThemeMode) => {
    setModeState(m);
    try { localStorage.setItem(KEY, m); } catch { /* ignora */ }
  }, []);
  return { mode, setMode };
}
