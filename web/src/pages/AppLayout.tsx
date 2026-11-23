import { ApiError, type AppConfig } from "@vps/shared";
import { LogOut, Menu, Moon, PanelLeft, PanelLeftClose, Sun, SunMoon, X } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Outlet, useLocation } from "react-router";

import { Logo } from "@/components/Logo";
import { Sidebar } from "@/components/Sidebar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { api, AuthContext, readPassword, writePassword } from "@/lib/api";
import { useTheme, type ThemeMode } from "@/lib/theme";

const titles: Record<string, string> = {
  "/": "Nova busca",
  "/buscas": "Histórico",
  "/gerar-link": "Gerar link",
  "/lojas": "Lojas",
};

const nextMode: Record<ThemeMode, ThemeMode> = { light: "dark", dark: "contrast", contrast: "light" };
const modeLabel: Record<ThemeMode, string> = { light: "Claro", dark: "Escuro", contrast: "Alto contraste" };

type Gate = { state: "loading" } | { state: "locked"; error?: string } | { state: "open"; config: AppConfig };

export function AppLayout() {
  const { mode, setMode } = useTheme();
  const { pathname } = useLocation();
  const [gate, setGate] = useState<Gate>({ state: "loading" });
  const [mobileOpen, setMobileOpen] = useState(false);
  const [collapsed, setCollapsed] = useState(() => {
    try { return localStorage.getItem("vps-sidebar") === "icon"; } catch { return false; }
  });

  const check = useCallback(async () => {
    try {
      setGate({ state: "open", config: await api.config() });
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) setGate({ state: "locked", error: readPassword() ? "Senha inválida." : undefined });
      else setGate({ state: "locked", error: `Não foi possível falar com o servidor: ${(e as Error).message}` });
    }
  }, []);

  useEffect(() => { void check(); }, [check]);
  useEffect(() => setMobileOpen(false), [pathname]);
  useEffect(() => {
    try { localStorage.setItem("vps-sidebar", collapsed ? "icon" : "default"); } catch { /* ignora */ }
  }, [collapsed]);

  const auth = useMemo(() => ({ onUnauthorized: () => setGate({ state: "locked", error: "Sessão expirada. Entre de novo." }) }), []);

  if (gate.state === "loading") return <div className="p-8 text-muted-foreground">Carregando…</div>;
  if (gate.state === "locked") return <PasswordScreen error={gate.error} onSubmit={(p) => { writePassword(p); void check(); }} />;

  const ThemeIcon = mode === "light" ? Sun : mode === "dark" ? Moon : SunMoon;
  const title = pathname.startsWith("/buscas/") ? "Resultado" : titles[pathname] ?? "Workspace";
  const footer = `IA ${gate.config.aiProvider} · busca ${gate.config.searchProvider}`;

  return (
    <AuthContext.Provider value={auth}>
      <div className="min-h-svh bg-background text-foreground">
        <a href="#main-content" className="app-skip-link">Pular para o conteúdo</a>
        <div className="flex min-h-svh">
          <div className="sticky top-0 hidden h-svh lg:flex">
            <Sidebar collapsed={collapsed} footer={footer} />
          </div>

          {mobileOpen && (
            <div className="fixed inset-0 z-50 lg:hidden">
              <button aria-label="Fechar navegação" className="absolute inset-0 bg-black/50" onClick={() => setMobileOpen(false)} />
              <div className="relative h-full w-[300px] max-w-[85vw] bg-sidebar shadow-xl">
                <Button variant="ghost" size="icon" className="absolute right-2 top-3 z-10" onClick={() => setMobileOpen(false)} aria-label="Fechar navegação">
                  <X className="h-5 w-5" />
                </Button>
                <Sidebar onNavigate={() => setMobileOpen(false)} footer={footer} />
              </div>
            </div>
          )}

          <div className="min-w-0 flex-1">
            <header className="sticky top-0 z-30 flex min-h-[64px] items-center justify-between gap-4 border-b border-border bg-background/90 px-4 backdrop-blur-xl sm:min-h-[76px] sm:px-6 lg:px-10" aria-label="Barra superior">
              <div className="flex min-w-0 items-center gap-3">
                <Button variant="ghost" size="icon" className="lg:hidden" onClick={() => setMobileOpen(true)} aria-label="Abrir navegação">
                  <Menu className="h-5 w-5" />
                </Button>
                <Button
                  variant="ghost"
                  size="icon"
                  className="hidden rounded-xl lg:inline-flex"
                  onClick={() => setCollapsed((c) => !c)}
                  aria-label={collapsed ? "Expandir menu" : "Recolher menu"}
                >
                  {collapsed ? <PanelLeft className="h-4 w-4" /> : <PanelLeftClose className="h-4 w-4" />}
                </Button>
                <div className="flex items-center gap-2 text-xs text-muted-foreground">
                  <span className="hidden sm:inline">VideoShop</span>
                  <span aria-hidden="true" className="hidden sm:inline">/</span>
                  <span className="font-display text-sm font-semibold text-foreground sm:font-sans sm:text-xs">{title}</span>
                </div>
              </div>
              <div className="flex items-center gap-2 sm:gap-3">
                <Button
                  variant="ghost"
                  size="icon"
                  className="rounded-xl"
                  onClick={() => setMode(nextMode[mode])}
                  aria-label={`Tema: ${modeLabel[mode]}. Alternar`}
                  title={`Tema: ${modeLabel[mode]}`}
                >
                  <ThemeIcon className="h-4 w-4" />
                </Button>
                {readPassword() && (
                  <Button variant="outline" size="sm" className="rounded-xl" onClick={() => { writePassword(null); void check(); }}>
                    <LogOut className="h-3.5 w-3.5" />
                    <span className="hidden sm:inline">Sair</span>
                  </Button>
                )}
              </div>
            </header>
            <main id="main-content" className="app-content" tabIndex={-1}>
              <Outlet context={gate.config} />
            </main>
          </div>
        </div>
      </div>
    </AuthContext.Provider>
  );
}

function PasswordScreen({ error, onSubmit }: { error?: string; onSubmit: (password: string) => void }) {
  const [value, setValue] = useState("");
  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-6 p-4">
      <Logo />
      <form
        className="app-card grid w-full max-w-sm gap-4 p-6"
        onSubmit={(e) => { e.preventDefault(); if (value) onSubmit(value); }}
      >
        <p className="text-sm text-muted-foreground">Encontre e compre os produtos que aparecem em qualquer vídeo.</p>
        <div className="grid gap-1.5">
          <Label htmlFor="password">Senha do painel</Label>
          <Input id="password" type="password" autoComplete="current-password" value={value} onChange={(e) => setValue(e.target.value)} autoFocus />
        </div>
        {error && <p className="text-sm text-destructive">{error}</p>}
        <Button type="submit" disabled={!value}>Entrar</Button>
      </form>
    </div>
  );
}
