import type { CredentialField, Store, StoreInput } from "@vps/shared";
import { ChevronDown, Plus } from "lucide-react";
import { useEffect, useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { api } from "@/lib/api";

/** Lojas onde os produtos são procurados e as credenciais de afiliado de cada uma (mesmo modelo do StoryShop). */
export function StoresPage() {
  const [stores, setStores] = useState<Store[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    api.stores().then(setStores).catch((e: Error) => setError(e.message));
  }, []);

  const replace = (s: Store) => setStores((all) => (all.some((x) => x.id === s.id) ? all.map((x) => (x.id === s.id ? s : x)) : [...all, s]));

  return (
    <div className="mx-auto grid max-w-3xl gap-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="font-display text-2xl font-semibold tracking-[-0.04em]">Lojas</h1>
          <p className="text-sm text-muted-foreground">Os produtos são procurados nas lojas ativadas, na ordem de prioridade (menor primeiro).</p>
        </div>
        <Button variant="outline" size="sm" onClick={() => setCreating((c) => !c)}><Plus className="h-4 w-4" />Nova loja</Button>
      </div>
      {error && <p className="text-destructive">{error}</p>}
      {creating && (
        <Card>
          <CardHeader><CardTitle>Nova loja</CardTitle></CardHeader>
          <CardContent>
            <PlatformForm onSaved={(s) => { replace(s); setCreating(false); }} />
          </CardContent>
        </Card>
      )}
      {stores.map((s) => <StoreCard key={s.id} store={s} onSaved={replace} />)}
    </div>
  );
}

function StoreCard({ store, onSaved }: { store: Store; onSaved: (s: Store) => void }) {
  const [values, setValues] = useState(store.values);
  const [enabled, setEnabled] = useState(store.enabled);
  const [priority, setPriority] = useState(store.priority);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);

  const save = async () => {
    setBusy(true);
    setMessage(null);
    try {
      onSaved(await api.saveCredential(store.id, { values, enabled, priority }));
      setMessage("Salvo");
    } catch (e) {
      setMessage((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <CardTitle>{store.displayName}</CardTitle>
          <div className="flex gap-1.5">
            {!store.isActive && <Badge>Inativa</Badge>}
            <Badge tone={store.enabled && store.configured ? "brand" : "saffron"}>
              {store.enabled ? (store.configured ? "Ativa" : "Falta credencial") : "Desligada"}
            </Badge>
          </div>
        </div>
        <CardDescription>{store.searchDomain ? `Busca em ${store.searchDomain}` : "Sem domínio de busca (só busca por imagem e links colados)"}</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3">
        {store.requiredCredentialFields.map((f) => (
          <div key={f.field} className="grid gap-1.5">
            <Label htmlFor={`${store.id}-${f.field}`}>{f.label}</Label>
            <Input id={`${store.id}-${f.field}`} value={values[f.field] ?? ""} onChange={(e) => setValues({ ...values, [f.field]: e.target.value })} />
          </div>
        ))}
        <div className="flex flex-wrap items-center gap-4">
          <label className="flex items-center gap-2 text-sm font-medium">
            <input type="checkbox" className="h-4 w-4 accent-[var(--brand)]" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
            Procurar nesta loja
          </label>
          <label className="flex items-center gap-2 text-sm">
            Prioridade
            <Input type="number" className="w-20" value={priority} onChange={(e) => setPriority(Number(e.target.value) || 0)} />
          </label>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <Button size="sm" onClick={save} disabled={busy}>Salvar</Button>
          <Button size="sm" variant="ghost" onClick={() => setEditing((v) => !v)}>
            <ChevronDown className={`h-4 w-4 transition-transform ${editing ? "rotate-180" : ""}`} />Configuração da loja
          </Button>
          {message && <span className="text-sm text-muted-foreground">{message}</span>}
        </div>
        {editing && <PlatformForm store={store} onSaved={(s) => { onSaved(s); setEditing(false); }} />}
      </CardContent>
    </Card>
  );
}

function toInput(s?: Store): StoreInput {
  return {
    slug: s?.slug ?? "",
    displayName: s?.displayName ?? "",
    domainRegex: s?.domainRegex ?? "",
    searchDomain: s?.searchDomain ?? "",
    affiliateUrlTemplate: s?.affiliateUrlTemplate ?? "",
    requiredCredentialFields: s?.requiredCredentialFields ?? [{ field: "affiliate_id", label: "Seu ID de afiliado", required: true }],
    extraParams: s?.extraParams ?? {},
    androidIntentPackage: s?.androidIntentPackage ?? "",
    isActive: s?.isActive ?? true,
  };
}

/** Cadastro da loja: domínio, template do link com macros ({original_url}, {encoded_original_url}, {asin}, {influencer.campo}…). */
function PlatformForm({ store, onSaved }: { store?: Store; onSaved: (s: Store) => void }) {
  const [form, setForm] = useState<StoreInput>(() => toInput(store));
  const [fields, setFields] = useState(() => JSON.stringify(form.requiredCredentialFields, null, 2));
  const [params, setParams] = useState(() => JSON.stringify(form.extraParams, null, 2));
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const set = (k: keyof StoreInput) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value });

  const save = async () => {
    setError(null);
    let parsedFields: CredentialField[];
    let parsedParams: Record<string, string>;
    try {
      parsedFields = JSON.parse(fields);
      parsedParams = JSON.parse(params);
    } catch {
      setError("Campos e parâmetros precisam ser JSON válido.");
      return;
    }
    setBusy(true);
    try {
      const input = { ...form, requiredCredentialFields: parsedFields, extraParams: parsedParams };
      onSaved(store ? await api.updateStore(store.id, input) : await api.createStore(input));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="grid gap-3 rounded-xl border bg-muted/30 p-4">
      <div className="grid gap-3 sm:grid-cols-2">
        <Field id="slug" label="Identificador" value={form.slug} onChange={set("slug")} placeholder="minha-loja" />
        <Field id="name" label="Nome" value={form.displayName} onChange={set("displayName")} placeholder="Minha Loja" />
        <Field id="regex" label="Regex do domínio" value={form.domainRegex} onChange={set("domainRegex")} placeholder="(minhaloja\.com\.br)" />
        <Field id="search" label="Domínio de busca" value={form.searchDomain ?? ""} onChange={set("searchDomain")} placeholder="minhaloja.com.br" />
      </div>
      <Field id="tpl" label="Template do link afiliado" value={form.affiliateUrlTemplate} onChange={set("affiliateUrlTemplate")} placeholder="https://loja.com/r?url={encoded_original_url}&id={influencer.affiliate_id}" />
      <Field id="pkg" label="Pacote do app Android (opcional)" value={form.androidIntentPackage ?? ""} onChange={set("androidIntentPackage")} />
      <div className="grid gap-1.5">
        <Label htmlFor="fields">Campos de credencial (JSON)</Label>
        <Textarea id="fields" className="font-mono text-xs" rows={4} value={fields} onChange={(e) => setFields(e.target.value)} />
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="params">Parâmetros fixos (JSON)</Label>
        <Textarea id="params" className="font-mono text-xs" rows={2} value={params} onChange={(e) => setParams(e.target.value)} />
      </div>
      <label className="flex items-center gap-2 text-sm font-medium">
        <input type="checkbox" className="h-4 w-4" checked={form.isActive} onChange={(e) => setForm({ ...form, isActive: e.target.checked })} />
        Loja ativa
      </label>
      {error && <p className="text-sm text-destructive">{error}</p>}
      <Button size="sm" className="justify-self-start" onClick={save} disabled={busy}>Salvar loja</Button>
    </div>
  );
}

function Field({ id, label, ...props }: { id: string; label: string } & React.ComponentProps<"input">) {
  return (
    <div className="grid gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} {...props} />
    </div>
  );
}
