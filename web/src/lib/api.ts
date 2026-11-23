import { createApiClient } from "@vps/shared";
import { createContext, useContext } from "react";

const KEY = "vps-admin-password";

export function readPassword(): string | null {
  try {
    return localStorage.getItem(KEY);
  } catch {
    return null;
  }
}

export function writePassword(value: string | null) {
  try {
    if (value) localStorage.setItem(KEY, value);
    else localStorage.removeItem(KEY);
  } catch {
    /* storage indisponível: a senha vale só nesta aba */
  }
  memory = value;
}

let memory: string | null = readPassword();

export const api = createApiClient(import.meta.env.VITE_API_URL ?? "", () => memory);

/** Avisa o layout quando a API responde 401 (senha errada ou trocada). */
export const AuthContext = createContext<{ onUnauthorized: () => void }>({ onUnauthorized: () => {} });
export const useAuthContext = () => useContext(AuthContext);
