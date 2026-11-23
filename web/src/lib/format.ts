/** 75.4 → "01:15" (ou "1:02:03" acima de uma hora). */
export function clock(seconds: number): string {
  const s = Math.max(0, Math.floor(seconds));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  const mm = String(m).padStart(2, "0");
  const ss = String(sec).padStart(2, "0");
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
}

export function dateTime(iso: string): string {
  return new Date(iso).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
}

export const statusLabel: Record<string, string> = {
  RECEIVED: "Na fila",
  DOWNLOADING: "Baixando",
  READY: "Pronto para escolher quadros",
  PROCESSING: "Analisando",
  DONE: "Concluída",
  FAILED: "Falhou",
};

export const isRunning = (status: string) => status === "RECEIVED" || status === "DOWNLOADING" || status === "PROCESSING";
