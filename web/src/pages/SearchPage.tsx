import type { AppConfig, ManualFrame, SearchDetail } from "@vps/shared";
import { ApiError } from "@vps/shared";
import { AlertTriangle, ChevronDown, Loader2, MousePointerClick, Trash2 } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate, useOutletContext, useParams } from "react-router";

import { ItemCard } from "@/components/ItemCard";
import { ManualPicker } from "@/components/ManualPicker";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { api, useAuthContext } from "@/lib/api";
import { clock, dateTime, isRunning, statusLabel } from "@/lib/format";

export function SearchPage() {
  const { id } = useParams<{ id: string }>();
  const config = useOutletContext<AppConfig>();
  const navigate = useNavigate();
  const { onUnauthorized } = useAuthContext();
  const [search, setSearch] = useState<SearchDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showPicker, setShowPicker] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const videoRef = useRef<HTMLVideoElement>(null);

  const load = useCallback(async () => {
    try {
      setSearch(await api.search(id!));
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) onUnauthorized();
      setError((e as Error).message);
    }
  }, [id, onUnauthorized]);

  useEffect(() => { void load(); }, [load]);

  const running = search ? isRunning(search.status) : true;
  useEffect(() => {
    if (!running) return;
    const t = setInterval(load, 2000);
    return () => clearInterval(t);
  }, [running, load]);

  if (error && !search) return <p className="text-destructive">{error}</p>;
  if (!search) return <p className="text-muted-foreground">Carregando…</p>;

  const manualOpen = search.videoUrl && (search.status === "READY" || showPicker);
  const seek = search.videoUrl && manualOpen
    ? (t: number) => {
        const v = videoRef.current;
        if (!v) return;
        v.currentTime = t;
        v.scrollIntoView({ behavior: "smooth", block: "center" });
      }
    : undefined;

  const submitFrames = async (frames: ManualFrame[]) => {
    setSubmitting(true);
    try {
      await api.analyzeFrames(search.id, frames);
      setShowPicker(false);
      await load();
    } finally {
      setSubmitting(false);
    }
  };

  const remove = async () => {
    if (!window.confirm("Apagar esta busca e os arquivos dela?")) return;
    try {
      await api.deleteSearch(search.id);
      navigate("/buscas");
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const found = search.items.filter((i) => i.status === "FOUND").length;

  return (
    <div className="grid gap-6">
      <header className="grid gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <Badge tone={search.status === "FAILED" ? "danger" : running ? "saffron" : "brand"}>{statusLabel[search.status]}</Badge>
          <Badge>{search.mode === "AUTO" ? "Automático" : "Manual"}</Badge>
          <span className="text-xs text-muted-foreground">{dateTime(search.createdAt)}</span>
        </div>
        <h1 className="break-words font-display text-2xl font-semibold tracking-[-0.04em]">{search.title || "Vídeo enviado"}</h1>
        {search.sourceUrl && (
          <a href={search.sourceUrl} target="_blank" rel="noreferrer" className="truncate text-sm text-muted-foreground hover:underline">{search.sourceUrl}</a>
        )}
        <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
          {search.durationSeconds != null && <span>Duração {clock(search.durationSeconds)}</span>}
          {search.framesSampled != null && <span>{search.framesSampled} quadros lidos</span>}
          {search.framesAnalyzed != null && <span>{search.framesAnalyzed} analisados pela IA</span>}
          {search.items.length > 0 && <span>{found} de {search.items.length} produtos com link</span>}
        </div>
      </header>

      {running && (
        <div className="app-card flex items-center gap-3 p-4" role="status">
          <Loader2 className="h-5 w-5 animate-spin text-brand" />
          <span className="text-sm">{search.progress ?? statusLabel[search.status]}…</span>
        </div>
      )}
      {search.status === "FAILED" && (
        <div className="app-card flex items-start gap-3 border-destructive/40 p-4 text-sm" role="alert">
          <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-destructive" />
          <span>{search.errorMessage ?? "A busca falhou."}</span>
        </div>
      )}
      {search.status === "DONE" && search.progress && <p className="text-sm text-muted-foreground">{search.progress}.</p>}

      {search.videoUrl && !running && (
        <section className="grid gap-3">
          {search.status !== "READY" && (
            <Button variant="outline" className="justify-self-start" onClick={() => setShowPicker((v) => !v)}>
              <MousePointerClick className="h-4 w-4" />
              {showPicker ? "Fechar escolha manual" : "Escolher quadros manualmente"}
            </Button>
          )}
          {manualOpen && (
            <div className="app-card grid gap-3 p-4">
              <p className="text-sm text-muted-foreground">
                Pause no momento em que o produto aparece, marque a área dele (opcional) e capture. Depois busque os quadros escolhidos.
              </p>
              <ManualPicker videoUrl={search.videoUrl} videoRef={videoRef} maxFrames={config.maxManualFrames} busy={submitting} onSubmit={submitFrames} />
            </div>
          )}
        </section>
      )}
      {search.mediaDeleted && <p className="text-xs text-muted-foreground">O vídeo original já foi apagado pela retenção; os resultados continuam aqui.</p>}

      {search.items.length > 0 && (
        <section className="grid gap-3">
          <h2 className="font-display text-lg font-semibold">Produtos</h2>
          <div className="grid gap-3 xl:grid-cols-2">
            {search.items.map((item) => (
              <ItemCard
                key={item.id}
                item={item}
                onSeek={seek}
                onSwitch={async (index) => {
                  const updated = await api.switchOffer(search.id, item.id, index);
                  setSearch((s) => s && { ...s, items: s.items.map((i) => (i.id === updated.id ? updated : i)) });
                }}
              />
            ))}
          </div>
        </section>
      )}

      {search.transcript && (
        <details className="app-card group p-4 text-sm">
          <summary className="flex cursor-pointer items-center gap-2 font-semibold">
            <ChevronDown className="h-4 w-4 transition-transform group-open:rotate-180" />Transcrição da fala
          </summary>
          <pre className="mt-3 whitespace-pre-wrap font-sans text-muted-foreground">{search.transcript}</pre>
        </details>
      )}

      {!running && (
        <Button variant="ghost" className="justify-self-start text-destructive" onClick={remove}><Trash2 className="h-4 w-4" />Apagar busca</Button>
      )}
    </div>
  );
}
