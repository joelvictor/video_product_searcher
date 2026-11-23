import type { SearchItem } from "@vps/shared";
import { ExternalLink, ImageOff } from "lucide-react";
import { useState } from "react";

import { CopyButton } from "@/components/CopyButton";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { clock } from "@/lib/format";

/** Card de um produto encontrado: recorte do vídeo, momentos em que aparece, oferta principal com link e lojas alternativas. */
export function ItemCard({
  item,
  onSeek,
  onSwitch,
}: {
  item: SearchItem;
  onSeek?: (time: number) => void;
  onSwitch: (index: number) => Promise<void>;
}) {
  const [busy, setBusy] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const found = item.status === "FOUND";

  const choose = async (i: number) => {
    setBusy(i);
    setError(null);
    try {
      await onSwitch(i);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(null);
    }
  };

  return (
    <article className="app-card grid gap-4 p-4 sm:grid-cols-[132px_1fr]">
      <div className="grid grid-cols-[96px_1fr] gap-3 sm:grid-cols-1">
        <div className="relative grid aspect-square place-items-center overflow-hidden rounded-xl bg-muted">
          {item.cropUrl ? <img src={item.cropUrl} alt={item.label} className="h-full w-full object-cover" loading="lazy" /> : <ImageOff className="h-5 w-5 text-muted-foreground" />}
          <span className="absolute left-1.5 top-1.5 grid h-6 min-w-6 place-items-center rounded-full bg-brand px-1.5 text-xs font-bold text-brand-foreground">{item.itemNumber}</span>
        </div>
        <div className="flex flex-wrap content-start gap-1.5 sm:hidden">
          <Meta item={item} />
        </div>
      </div>

      <div className="grid min-w-0 content-start gap-3">
        <div className="grid gap-1">
          <h3 className="font-display text-lg font-semibold leading-tight tracking-[-0.02em]">
            {item.brand && <span className="text-brand">{item.brand} </span>}
            {item.label}
          </h3>
          <div className="hidden flex-wrap gap-1.5 sm:flex"><Meta item={item} /></div>
        </div>

        {item.timestamps.length > 0 && (
          <div className="flex flex-wrap items-center gap-1.5 text-xs">
            <span className="text-muted-foreground">Aparece em</span>
            {item.timestamps.slice(0, 8).map((t) => (
              <button
                key={t}
                type="button"
                disabled={!onSeek}
                onClick={() => onSeek?.(t)}
                className="rounded-md border px-1.5 py-0.5 font-mono hover:bg-muted disabled:cursor-default disabled:hover:bg-transparent"
              >
                {clock(t)}
              </button>
            ))}
            {item.timestamps.length > 8 && <span className="text-muted-foreground">+{item.timestamps.length - 8}</span>}
          </div>
        )}

        {found ? (
          <div className="grid gap-2 rounded-xl border border-brand/25 bg-brand/5 p-3">
            <div className="flex items-start gap-3">
              {item.productImageUrl && <img src={item.productImageUrl} alt="" className="h-14 w-14 shrink-0 rounded-lg bg-card object-contain" loading="lazy" referrerPolicy="no-referrer" />}
              <div className="min-w-0">
                <div className="app-label">{item.platformName}</div>
                <div className="line-clamp-2 text-sm font-medium">{item.productTitle}</div>
                {item.priceDisplay && <div className="font-display text-base font-semibold">{item.priceDisplay}</div>}
              </div>
            </div>
            {item.shortUrl && (
              <div className="flex flex-wrap items-center gap-2">
                <a href={item.shortUrl} target="_blank" rel="noreferrer" className="min-w-0 flex-1 truncate text-sm font-semibold text-brand underline-offset-4 hover:underline">
                  {item.shortUrl}
                </a>
                <CopyButton value={item.shortUrl} label="Copiar link" />
                <Button asChild size="sm"><a href={item.shortUrl} target="_blank" rel="noreferrer"><ExternalLink className="h-3.5 w-3.5" />Abrir</a></Button>
              </div>
            )}
          </div>
        ) : (
          <p className="rounded-xl border border-dashed p-3 text-sm text-muted-foreground">
            {item.note ?? "Não encontramos esse produto nas lojas ativadas."} <span className="block text-xs">Consulta usada: “{item.searchQuery}”</span>
          </p>
        )}

        {item.alternatives.length > 0 && (
          <details className="group text-sm">
            <summary className="cursor-pointer text-muted-foreground">{found ? "Outras lojas" : "Ofertas encontradas"} ({item.alternatives.length})</summary>
            <ul className="mt-2 grid gap-2">
              {item.alternatives.map((o, i) => (
                <li key={o.url} className="flex items-center gap-2 rounded-lg border p-2">
                  <div className="min-w-0 flex-1">
                    <div className="app-label">{o.platformName}{o.price ? ` · ${o.price}` : ""}</div>
                    <a href={o.url} target="_blank" rel="noreferrer" className="line-clamp-1 hover:underline">{o.title || o.url}</a>
                  </div>
                  <Button size="sm" variant="outline" disabled={busy !== null} onClick={() => choose(i)}>{busy === i ? "…" : "Usar esta"}</Button>
                </li>
              ))}
            </ul>
          </details>
        )}
        {error && <p className="text-sm text-destructive">{error}</p>}
      </div>
    </article>
  );
}

function Meta({ item }: { item: SearchItem }) {
  return (
    <>
      {item.relevanceLayer === "PRIMARY_FOCUS" ? <Badge tone="brand">Foco do vídeo</Badge> : <Badge>Na cena</Badge>}
      <Badge>{item.detectionType === "VISUAL_FASHION_ITEM" ? "Busca por imagem" : "Busca por texto"}</Badge>
      {item.origin === "MANUAL" && <Badge tone="saffron">Escolha manual</Badge>}
      <Badge tone={item.confidence < 0.65 ? "saffron" : "neutral"}>{Math.round(item.confidence * 100)}%</Badge>
    </>
  );
}
