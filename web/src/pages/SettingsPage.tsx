import type { SettingSource, SettingView, SettingsChanges } from "@vps/shared";
import { useEffect, useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { api } from "@/lib/api";
import { cn } from "@/lib/utils";

type FieldDef = { id: string; label: string; placeholder?: string; help?: string };
type Section = { title: string; description: string; provider: string; providerLabel: string; options: Record<string, string>; groups: Record<string, FieldDef[]> };

const sections: Section[] = [
  {
    title: "IA que analisa o vídeo",
    description: "Lê os quadros (e a fala, quando há) para identificar os produtos.",
    provider: "ai.provider",
    providerLabel: "Provedor",
    options: { gemini: "Google Gemini", deepseek: "DeepSeek (ou compatível)" },
    groups: {
      gemini: [
        { id: "ai.gemini.api-key", label: "Chave da API do Gemini" },
        { id: "ai.gemini.model", label: "Modelo", placeholder: "gemini-2.5-flash" },
      ],
      deepseek: [
        { id: "ai.deepseek.api-key", label: "Chave da API", help: "Em servidor próprio (Ollama, vLLM) a chave pode ficar vazia." },
        { id: "ai.deepseek.base-url", label: "URL base", placeholder: "https://api.deepseek.com", help: "Qualquer API compatível com a da OpenAI: a parte da URL antes de /chat/completions (OpenRouter, Together, Azure, Ollama...)." },
        { id: "ai.deepseek.model", label: "Modelo", placeholder: "deepseek-flash", help: "Precisa aceitar imagens." },
      ],
    },
  },
  {
    title: "Busca nas lojas",
    description: "Acha a página de compra de cada produto nas suas lojas.",
    provider: "search.provider",
    providerLabel: "Provedor",
    options: { serpapi: "SerpAPI (Google e Google Lens)", brave: "Brave Search", deepseek: "DeepSeek (busca na web)" },
    groups: {
      serpapi: [{ id: "search.serpapi.key", label: "Chave da SerpAPI" }],
      brave: [{ id: "search.brave.key", label: "Chave do Brave Search" }],
      deepseek: [
        { id: "search.deepseek.api-key", label: "Chave da API", help: "Vazio usa a chave da IA, desde que a URL seja do mesmo servidor." },
        { id: "search.deepseek.base-url", label: "URL base", placeholder: "vazio = a mesma da IA", help: "A busca usa a ferramenta deepseek_web_search, que só existe na API oficial do DeepSeek." },
        { id: "search.deepseek.model", label: "Modelo", placeholder: "vazio = o mesmo da IA" },
      ],
    },
  },
];

const sourceLabel: Record<SettingSource, string> = { PANEL: "definido aqui", ENV: "padrão ou .env", NONE: "não definido" };

/** Escolha do provedor e das chaves de IA e busca, sem mexer no .env. As chaves ficam cifradas e nunca voltam para a tela. */
export function SettingsPage() {
  const [settings, setSettings] = useState<Record<string, SettingView> | null>(null);
  const [draft, setDraft] = useState<SettingsChanges>({});
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = (list: SettingView[]) => setSettings(Object.fromEntries(list.map((s) => [s.id, s])));

  useEffect(() => {
    api.settings().then(load).catch((e: Error) => setError(e.message));
  }, []);

  if (!settings) return <p className={cn("mx-auto max-w-3xl", error ? "text-destructive" : "text-muted-foreground")}>{error ?? "Carregando…"}</p>;

  const set = (id: string, value: string | null) =>
    setDraft((d) => {
      const next = { ...d, [id]: value };
      // chave em branco significa "manter a atual", não "apagar": só vai para a API o que foi digitado
      if (value === "" && settings[id]?.secret) delete next[id];
      return next;
    });
  const dirty = Object.keys(draft).length > 0;

  const save = async () => {
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      load(await api.saveSettings(draft));
      setDraft({});
      setMessage("Configurações salvas. Valem a partir da próxima busca.");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto grid max-w-3xl gap-4">
      <div>
        <h1 className="font-display text-2xl font-semibold tracking-[-0.04em]">Configurações</h1>
        <p className="text-sm text-muted-foreground">
          Escolha qual provedor e qual chave usar. O que for salvo aqui vale no lugar do <code>.env</code>; remover volta ao valor do ambiente.
        </p>
      </div>

      {sections.map((section) => {
        const providerSetting = settings[section.provider];
        const chosen = draft[section.provider] ?? providerSetting.value ?? Object.keys(section.options)[0];
        return (
          <Card key={section.provider}>
            <CardHeader>
              <CardTitle>{section.title}</CardTitle>
              <CardDescription>{section.description}</CardDescription>
            </CardHeader>
            <CardContent className="grid gap-4">
              <div className="grid gap-1.5">
                <Label htmlFor={section.provider}>{section.providerLabel}</Label>
                <select
                  id={section.provider}
                  value={chosen}
                  onChange={(e) => set(section.provider, e.target.value)}
                  className="h-9 w-full rounded-xl border bg-card px-3 text-sm shadow-xs outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
                >
                  {Object.entries(section.options).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                </select>
              </div>
              {section.groups[chosen]?.map((f) => (
                <SettingField key={f.id} def={f} setting={settings[f.id]} draft={draft[f.id]} onChange={(v) => set(f.id, v)} />
              ))}
            </CardContent>
          </Card>
        );
      })}

      {error && <p className="text-sm text-destructive" role="alert">{error}</p>}
      <div className="flex flex-wrap items-center gap-3">
        <Button onClick={save} disabled={busy || !dirty}>Salvar</Button>
        {dirty && <Button variant="ghost" onClick={() => setDraft({})} disabled={busy}>Descartar</Button>}
        {message && <span className="text-sm text-muted-foreground">{message}</span>}
      </div>
    </div>
  );
}

function SettingField({ def, setting, draft, onChange }: { def: FieldDef; setting: SettingView; draft: string | null | undefined; onChange: (v: string | null) => void }) {
  const removing = draft === null;
  const typed = typeof draft === "string" ? draft : null;
  const shown = typed ?? (setting.secret ? "" : (setting.value ?? ""));
  const canRemove = setting.source === "PANEL" && !removing;

  return (
    <div className="grid gap-1.5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <Label htmlFor={def.id}>{def.label}</Label>
        <Badge tone={setting.configured ? "brand" : "saffron"}>
          {removing ? "volta ao ambiente ao salvar" : `${setting.configured ? (setting.secret ? `${setting.hint} · ` : "") : ""}${sourceLabel[setting.source]}`}
        </Badge>
      </div>
      <div className="flex gap-2">
        <Input
          id={def.id}
          type={setting.secret ? "password" : "text"}
          autoComplete="off"
          spellCheck={false}
          disabled={removing}
          value={removing ? "" : shown}
          placeholder={setting.secret ? (setting.configured ? "Deixe vazio para manter a atual" : "Cole a chave") : def.placeholder}
          onChange={(e) => onChange(e.target.value)}
        />
        {canRemove && <Button type="button" variant="outline" size="sm" onClick={() => onChange(null)}>Remover</Button>}
        {removing && <Button type="button" variant="outline" size="sm" onClick={() => onChange(typed ?? "")}>Desfazer</Button>}
      </div>
      {def.help && <p className="text-xs text-muted-foreground">{def.help}</p>}
    </div>
  );
}
