import type { AppConfig, SearchMode } from "@vps/shared";
import { FileVideo, Link2, MousePointerClick, ScanSearch, Upload } from "lucide-react";
import { useRef, useState } from "react";
import { useNavigate, useOutletContext } from "react-router";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { api } from "@/lib/api";
import { clock } from "@/lib/format";
import { cn } from "@/lib/utils";

type Source = "url" | "upload";

export function NewSearchPage() {
  const config = useOutletContext<AppConfig>();
  const navigate = useNavigate();
  const [source, setSource] = useState<Source>("url");
  const [mode, setMode] = useState<SearchMode>("AUTO");
  const [url, setUrl] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  const ready = source === "url" ? url.trim().length > 0 : file !== null;

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      const { id } = source === "url" ? await api.searchFromUrl(url.trim(), mode) : await api.searchFromUpload(file!, mode);
      navigate(`/buscas/${id}`);
    } catch (e) {
      setError((e as Error).message);
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto grid max-w-3xl gap-6">
      <section className="app-hero app-card grid gap-2 p-6 sm:p-8">
        <span className="app-kicker brand-kicker">Busca por vídeo</span>
        <h1 className="font-display text-2xl font-semibold tracking-[-0.04em] sm:text-3xl">Que produto apareceu nesse vídeo?</h1>
        <p className="text-sm text-muted-foreground">
          Cole o link de um vídeo do TikTok, YouTube, Instagram e outros, ou envie o arquivo. Analisamos o vídeo inteiro, encontramos os
          produtos nas suas lojas e geramos os links de afiliado.
        </p>
      </section>

      <Card>
        <CardHeader>
          <CardTitle>1. O vídeo</CardTitle>
          <CardDescription>Até {clock(config.maxDurationSeconds)} de duração.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4">
          <div className="grid grid-cols-2 gap-2" role="tablist">
            <Choice active={source === "url"} onClick={() => setSource("url")} icon={<Link2 className="h-4 w-4" />} title="Link" />
            <Choice active={source === "upload"} onClick={() => setSource("upload")} icon={<Upload className="h-4 w-4" />} title="Arquivo" />
          </div>
          {source === "url" ? (
            <div className="grid gap-1.5">
              <Label htmlFor="video-url">Link do vídeo</Label>
              <Input id="video-url" value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://www.tiktok.com/@perfil/video/..." inputMode="url" autoComplete="off" />
              <p className="text-xs text-muted-foreground">Sites aceitos: {config.socialDomains.join(", ")} e links diretos para arquivos de vídeo.</p>
            </div>
          ) : (
            <button
              type="button"
              onClick={() => fileInput.current?.click()}
              className="flex flex-col items-center justify-center gap-2 rounded-xl border border-dashed bg-muted/30 p-6 text-center text-sm hover:bg-muted/60"
            >
              <FileVideo className="h-6 w-6 text-brand" />
              {file ? <span className="font-semibold">{file.name} ({(file.size / 1_048_576).toFixed(1)} MB)</span> : <span>Toque para escolher um vídeo</span>}
              <input ref={fileInput} type="file" accept="video/*" className="hidden" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
            </button>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>2. Como encontrar os produtos</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-2 sm:grid-cols-2">
          <ModeCard
            active={mode === "AUTO"}
            onClick={() => setMode("AUTO")}
            icon={<ScanSearch className="h-5 w-5" />}
            title="Automático"
            text="Varre o vídeo inteiro, escolhe os melhores quadros de cada cena e acha todos os produtos."
          />
          <ModeCard
            active={mode === "MANUAL"}
            onClick={() => setMode("MANUAL")}
            icon={<MousePointerClick className="h-5 w-5" />}
            title="Manual"
            text="Você pausa o vídeo, escolhe os quadros e, se quiser, marca a área do produto."
          />
        </CardContent>
      </Card>

      {error && <p className="text-sm text-destructive">{error}</p>}
      <Button size="lg" className="w-full sm:w-auto sm:justify-self-end" disabled={!ready || busy} onClick={submit}>
        {busy ? "Enviando…" : "Buscar produtos"}
      </Button>
    </div>
  );
}

function Choice({ active, onClick, icon, title }: { active: boolean; onClick: () => void; icon: React.ReactNode; title: string }) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      onClick={onClick}
      className={cn("flex items-center justify-center gap-2 rounded-xl border px-3 py-2.5 text-sm font-semibold transition-colors", active ? "border-brand bg-brand/10 text-brand" : "hover:bg-muted")}
    >
      {icon}
      {title}
    </button>
  );
}

function ModeCard({ active, onClick, icon, title, text }: { active: boolean; onClick: () => void; icon: React.ReactNode; title: string; text: string }) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
      className={cn("grid gap-1.5 rounded-xl border p-4 text-left transition-colors", active ? "border-brand bg-brand/10" : "hover:bg-muted")}
    >
      <span className={cn("flex items-center gap-2 font-display font-semibold", active && "text-brand")}>{icon}{title}</span>
      <span className="text-sm text-muted-foreground">{text}</span>
    </button>
  );
}
