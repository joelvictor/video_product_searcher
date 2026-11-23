import type { CreatedLink } from "@vps/shared";
import { useState } from "react";

import { CopyButton } from "@/components/CopyButton";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { api } from "@/lib/api";

/** Cola um link de produto de uma loja cadastrada e recebe o link afiliado curto. */
export function LinkGeneratorPage() {
  const [url, setUrl] = useState("");
  const [link, setLink] = useState<CreatedLink | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const generate = async () => {
    setBusy(true);
    setError(null);
    setLink(null);
    try {
      setLink(await api.createLink(url));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto grid max-w-2xl gap-4">
      <h1 className="font-display text-2xl font-semibold tracking-[-0.04em]">Gerar link</h1>
      <Card>
        <CardHeader>
          <CardTitle>Link de produto</CardTitle>
          <CardDescription>Cole o link de um produto de uma das lojas cadastradas.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-3">
          <form className="flex flex-col gap-2 sm:flex-row" onSubmit={(e) => { e.preventDefault(); void generate(); }}>
            <Input value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://www.amazon.com.br/dp/..." inputMode="url" />
            <Button type="submit" disabled={busy || !url}>Gerar</Button>
          </form>
          {error && <p className="text-sm text-destructive">{error}</p>}
          {link && (
            <div className="grid gap-2 rounded-xl border bg-muted/40 p-3 text-sm">
              <span className="app-label">{link.platform}</span>
              <div className="flex items-center gap-2">
                <a className="min-w-0 flex-1 truncate font-semibold text-brand underline-offset-4 hover:underline" href={link.shortUrl} target="_blank" rel="noreferrer">{link.shortUrl}</a>
                <CopyButton value={link.shortUrl} />
              </div>
              <p className="break-all text-xs text-muted-foreground">{link.affiliateUrl}</p>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
