import { History, Link as LinkIcon, ScanSearch, Settings, Store, type LucideIcon } from "lucide-react";
import { NavLink } from "react-router";

import { Logo } from "@/components/Logo";

type Item = { label: string; to: string; icon: LucideIcon; end?: boolean };

const workspace: Item[] = [
  { label: "Nova busca", to: "/", icon: ScanSearch, end: true },
  { label: "Histórico", to: "/buscas", icon: History },
  { label: "Gerar link", to: "/gerar-link", icon: LinkIcon },
];
const settings: Item[] = [
  { label: "Lojas", to: "/lojas", icon: Store },
  { label: "Configurações", to: "/configuracoes", icon: Settings },
];

function NavGroup({ label, items, onNavigate, collapsed }: { label: string; items: Item[]; onNavigate?: () => void; collapsed: boolean }) {
  return (
    <div className="space-y-2">
      <div className={`px-3 text-[10px] font-bold uppercase tracking-[0.2em] text-muted-foreground ${collapsed ? "sr-only" : ""}`}>{label}</div>
      <div className="space-y-1">
        {items.map(({ to, label: text, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            onClick={onNavigate}
            title={collapsed ? text : undefined}
            className={({ isActive }) =>
              `group flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-all duration-200 ${collapsed ? "justify-center" : ""} ${
                isActive ? "bg-brand/10 text-brand shadow-sm" : "text-muted-foreground hover:bg-muted hover:text-foreground"
              }`
            }
          >
            {({ isActive }) => (
              <>
                <span className={`h-5 w-1 rounded-full transition-colors ${isActive ? "bg-brand" : "bg-transparent group-hover:bg-border"}`} aria-hidden="true" />
                <Icon aria-hidden="true" className="h-4 w-4 shrink-0" />
                <span className={`min-w-0 flex-1 truncate ${collapsed ? "sr-only" : ""}`}>{text}</span>
              </>
            )}
          </NavLink>
        ))}
      </div>
    </div>
  );
}

export function Sidebar({ onNavigate, collapsed = false, footer }: { onNavigate?: () => void; collapsed?: boolean; footer?: string }) {
  return (
    <aside
      className={`flex h-full shrink-0 flex-col border-r border-border bg-sidebar px-4 py-5 transition-[width] duration-200 ${collapsed ? "w-[76px]" : "w-[248px]"}`}
      aria-label="Navegação principal"
    >
      <div className={`mb-10 px-3 ${collapsed ? "flex justify-center" : ""}`}>
        <Logo compact={collapsed} />
      </div>
      <div className="space-y-8">
        <NavGroup label="Workspace" items={workspace} onNavigate={onNavigate} collapsed={collapsed} />
        <NavGroup label="Configuração" items={settings} onNavigate={onNavigate} collapsed={collapsed} />
      </div>
      {footer && (
        <div className="mt-auto pt-10">
          <div className={`border border-brand/20 bg-brand/10 p-4 ${collapsed ? "rounded-xl px-2" : "rounded-2xl"}`}>
            <div className={`flex items-center justify-between gap-2 ${collapsed ? "justify-center" : ""}`}>
              <span className={`text-[10px] font-bold uppercase tracking-[0.16em] text-brand ${collapsed ? "sr-only" : ""}`}>{footer}</span>
              <span className="h-2 w-2 shrink-0 rounded-full bg-brand" aria-label="Operacional" />
            </div>
          </div>
        </div>
      )}
    </aside>
  );
}
