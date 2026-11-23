import type { SearchSummary } from "@vps/shared";
import { Film } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router";

import { Badge } from "@/components/ui/badge";
import { api } from "@/lib/api";
import { dateTime, isRunning, statusLabel } from "@/lib/format";

export function HistoryPage() {
  const [items, setItems] = useState<SearchSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    const load = () => api.searches().then((s) => alive && setItems(s)).catch((e: Error) => alive && setError(e.message));
    void load();
    const t = setInterval(load, 5000);
    return () => { alive = false; clearInterval(t); };
  }, []);

  return (
    <div className="grid gap-4">
      <h1 className="font-display text-2xl font-semibold tracking-[-0.04em]">Histórico</h1>
      {error && <p className="text-destructive">{error}</p>}
      {items?.length === 0 && <p className="text-muted-foreground">Nenhuma busca ainda.</p>}
      <div className="grid gap-3">
        {items?.map((s) => (
          <Link key={s.id} to={`/buscas/${s.id}`} className="app-card app-table-row flex items-center gap-3 p-3 sm:gap-4 sm:p-4">
            <div className="grid h-14 w-14 shrink-0 place-items-center overflow-hidden rounded-xl bg-muted">
              {s.thumbnailUrl ? <img src={s.thumbnailUrl} alt="" className="h-full w-full object-cover" /> : <Film className="h-5 w-5 text-muted-foreground" />}
            </div>
            <div className="min-w-0 flex-1">
              <div className="truncate font-semibold">{s.title || s.sourceUrl || "Vídeo enviado"}</div>
              <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted-foreground">
                <span>{dateTime(s.createdAt)}</span>
                <span>·</span>
                <span>{s.mode === "AUTO" ? "Automático" : "Manual"}</span>
                {s.itemCount > 0 && <><span>·</span><span>{s.foundCount} de {s.itemCount} com link</span></>}
              </div>
            </div>
            <Badge tone={s.status === "FAILED" ? "danger" : isRunning(s.status) ? "saffron" : "brand"}>{statusLabel[s.status]}</Badge>
          </Link>
        ))}
      </div>
    </div>
  );
}
