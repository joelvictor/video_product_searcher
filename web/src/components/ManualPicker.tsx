import type { ManualFrame } from "@vps/shared";
import { Camera, Crop, Trash2, X } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { clock } from "@/lib/format";

type Box = [number, number, number, number];
type Captured = { time: number; box: Box | null; thumb: string | null };
type Rect = { left: number; top: number; width: number; height: number };

/**
 * Modo manual: o usuário pausa o vídeo, captura o quadro e, se quiser, arrasta sobre o vídeo para marcar a área do produto.
 * A área vai normalizada em 0..1000 ([ymin, xmin, ymax, xmax]), igual às caixas que a IA devolve.
 */
export function ManualPicker({
  videoUrl,
  videoRef,
  maxFrames,
  busy,
  onSubmit,
}: {
  videoUrl: string;
  videoRef: React.RefObject<HTMLVideoElement | null>;
  maxFrames: number;
  busy: boolean;
  onSubmit: (frames: ManualFrame[]) => Promise<void>;
}) {
  const wrapper = useRef<HTMLDivElement>(null);
  const [content, setContent] = useState<Rect | null>(null);
  const [drawing, setDrawing] = useState(false);
  const [draft, setDraft] = useState<{ x0: number; y0: number; x1: number; y1: number } | null>(null);
  const [box, setBox] = useState<Box | null>(null);
  const [captured, setCaptured] = useState<Captured[]>([]);
  const [error, setError] = useState<string | null>(null);

  /** Área real da imagem dentro do <video> (descontando as faixas pretas do object-contain). */
  const measure = useCallback(() => {
    const v = videoRef.current;
    const w = wrapper.current;
    if (!v || !w || !v.videoWidth) return;
    const vr = v.getBoundingClientRect();
    const wr = w.getBoundingClientRect();
    const scale = Math.min(vr.width / v.videoWidth, vr.height / v.videoHeight);
    const width = v.videoWidth * scale;
    const height = v.videoHeight * scale;
    setContent({ left: vr.left - wr.left + (vr.width - width) / 2, top: vr.top - wr.top + (vr.height - height) / 2, width, height });
  }, [videoRef]);

  useEffect(() => {
    const v = videoRef.current;
    if (!v) return;
    v.addEventListener("loadedmetadata", measure);
    window.addEventListener("resize", measure);
    const ro = new ResizeObserver(measure);
    ro.observe(v);
    return () => { v.removeEventListener("loadedmetadata", measure); window.removeEventListener("resize", measure); ro.disconnect(); };
  }, [measure, videoRef]);

  const point = (e: React.PointerEvent) => {
    const r = (e.currentTarget as HTMLElement).getBoundingClientRect();
    return { x: Math.min(1, Math.max(0, (e.clientX - r.left) / r.width)), y: Math.min(1, Math.max(0, (e.clientY - r.top) / r.height)) };
  };

  const startDraw = () => {
    videoRef.current?.pause();
    measure();
    setBox(null);
    setDrawing(true);
  };

  const thumbnail = (b: Box | null): string | null => {
    const v = videoRef.current;
    if (!v || !v.videoWidth) return null;
    try {
      const sx = b ? (b[1] / 1000) * v.videoWidth : 0;
      const sy = b ? (b[0] / 1000) * v.videoHeight : 0;
      const sw = b ? ((b[3] - b[1]) / 1000) * v.videoWidth : v.videoWidth;
      const sh = b ? ((b[2] - b[0]) / 1000) * v.videoHeight : v.videoHeight;
      const canvas = document.createElement("canvas");
      const scale = 160 / Math.max(sw, sh);
      canvas.width = Math.max(1, Math.round(sw * scale));
      canvas.height = Math.max(1, Math.round(sh * scale));
      canvas.getContext("2d")!.drawImage(v, sx, sy, sw, sh, 0, 0, canvas.width, canvas.height);
      return canvas.toDataURL("image/jpeg", 0.7);
    } catch {
      return null;
    }
  };

  const capture = () => {
    const v = videoRef.current;
    if (!v) return;
    if (captured.length >= maxFrames) {
      setError(`No máximo ${maxFrames} quadros por vez.`);
      return;
    }
    v.pause();
    setError(null);
    setCaptured((c) => [...c, { time: v.currentTime, box, thumb: thumbnail(box) }]);
    setBox(null);
    setDrawing(false);
  };

  const submit = async () => {
    setError(null);
    try {
      await onSubmit(captured.map(({ time, box: b }) => ({ time, box: b })));
      setCaptured([]);
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const shown = draft
    ? { x0: Math.min(draft.x0, draft.x1), y0: Math.min(draft.y0, draft.y1), x1: Math.max(draft.x0, draft.x1), y1: Math.max(draft.y0, draft.y1) }
    : box
      ? { x0: box[1] / 1000, y0: box[0] / 1000, x1: box[3] / 1000, y1: box[2] / 1000 }
      : null;

  return (
    <div className="grid gap-3">
      <div ref={wrapper} className="relative overflow-hidden rounded-xl bg-black">
        <video
          ref={videoRef}
          src={videoUrl}
          controls={!drawing}
          playsInline
          preload="metadata"
          className="mx-auto block max-h-[70svh] w-full object-contain"
        />
        {content && (drawing || box) && (
          <div
            className={`absolute ${drawing ? "cursor-crosshair touch-none" : "pointer-events-none"}`}
            style={{ left: content.left, top: content.top, width: content.width, height: content.height }}
            onPointerDown={(e) => {
              if (!drawing) return;
              (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
              const p = point(e);
              setDraft({ x0: p.x, y0: p.y, x1: p.x, y1: p.y });
            }}
            onPointerMove={(e) => {
              if (!draft) return;
              const p = point(e);
              setDraft({ ...draft, x1: p.x, y1: p.y });
            }}
            onPointerUp={() => {
              if (!draft) return;
              const x0 = Math.min(draft.x0, draft.x1), x1 = Math.max(draft.x0, draft.x1);
              const y0 = Math.min(draft.y0, draft.y1), y1 = Math.max(draft.y0, draft.y1);
              setDraft(null);
              if (x1 - x0 < 0.03 || y1 - y0 < 0.03) return;
              setBox([Math.round(y0 * 1000), Math.round(x0 * 1000), Math.round(y1 * 1000), Math.round(x1 * 1000)]);
              setDrawing(false);
            }}
          >
            {drawing && !shown && <div className="absolute inset-x-0 top-2 mx-auto w-fit rounded-lg bg-black/70 px-3 py-1 text-xs text-white">Arraste sobre o produto</div>}
            {shown && (
              <div
                className="absolute rounded-md border-2 border-saffron bg-saffron/15 shadow-[0_0_0_9999px_rgba(0,0,0,0.35)]"
                style={{ left: `${shown.x0 * 100}%`, top: `${shown.y0 * 100}%`, width: `${(shown.x1 - shown.x0) * 100}%`, height: `${(shown.y1 - shown.y0) * 100}%` }}
              />
            )}
          </div>
        )}
      </div>

      <div className="flex flex-wrap gap-2">
        <Button onClick={capture}><Camera className="h-4 w-4" />Capturar quadro{box ? " com a área" : ""}</Button>
        {drawing ? (
          <Button variant="outline" onClick={() => { setDrawing(false); setDraft(null); }}><X className="h-4 w-4" />Cancelar área</Button>
        ) : (
          <Button variant="outline" onClick={startDraw}><Crop className="h-4 w-4" />{box ? "Marcar outra área" : "Marcar área do produto"}</Button>
        )}
        {box && !drawing && <Button variant="ghost" onClick={() => setBox(null)}>Limpar área</Button>}
      </div>

      {captured.length > 0 && (
        <div className="grid gap-3 rounded-xl border bg-muted/30 p-3">
          <div className="app-label">Quadros escolhidos ({captured.length}/{maxFrames})</div>
          <div className="flex gap-2 overflow-x-auto pb-1">
            {captured.map((c, i) => (
              <div key={i} className="relative shrink-0">
                <button
                  type="button"
                  className="grid h-24 w-20 place-items-center overflow-hidden rounded-lg border bg-card text-xs"
                  onClick={() => { if (videoRef.current) videoRef.current.currentTime = c.time; }}
                  title="Ir para este momento"
                >
                  {c.thumb ? <img src={c.thumb} alt="" className="h-full w-full object-cover" /> : clock(c.time)}
                </button>
                <span className="absolute bottom-1 left-1 rounded bg-black/70 px-1 text-[10px] text-white">{clock(c.time)}{c.box ? " ▣" : ""}</span>
                <button
                  type="button"
                  aria-label="Remover quadro"
                  className="absolute -right-1 -top-1 grid h-6 w-6 place-items-center rounded-full border bg-card"
                  onClick={() => setCaptured((all) => all.filter((_, j) => j !== i))}
                >
                  <Trash2 className="h-3 w-3" />
                </button>
              </div>
            ))}
          </div>
          <Button onClick={submit} disabled={busy} className="justify-self-start">Buscar produtos nesses quadros</Button>
        </div>
      )}
      {error && <p className="text-sm text-destructive">{error}</p>}
    </div>
  );
}
