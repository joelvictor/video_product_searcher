export function Logo({ compact = false }: { compact?: boolean }) {
  return (
    <div className={`flex items-center gap-3 ${compact ? "justify-center" : ""}`}>
      <span aria-hidden="true" className="grid h-10 w-10 place-items-center rounded-xl bg-brand font-display text-lg font-bold text-brand-foreground">V</span>
      {!compact && (
        <div className="min-w-0">
          <div className="flex items-center gap-2 font-display text-[17px] font-bold tracking-[-0.075em] text-foreground">
            <span>VideoShop</span>
            <span aria-hidden="true" className="h-4 w-[3px] -skew-x-12 bg-brand" />
          </div>
          <div className="font-display text-[10px] font-semibold uppercase tracking-[0.18em] text-muted-foreground">
            Busca <span className="text-brand">/</span> por vídeo
          </div>
        </div>
      )}
    </div>
  );
}
