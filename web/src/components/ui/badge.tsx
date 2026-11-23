import * as React from "react";

import { cn } from "@/lib/utils";

const tones = {
  neutral: "border-border bg-muted text-muted-foreground",
  brand: "border-brand/25 bg-brand/10 text-brand",
  saffron: "border-saffron/40 bg-saffron/15 text-foreground",
  danger: "border-destructive/30 bg-destructive/10 text-destructive",
};

function Badge({ className, tone = "neutral", ...props }: React.ComponentProps<"span"> & { tone?: keyof typeof tones }) {
  return (
    <span
      data-slot="badge"
      className={cn("inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[11px] font-semibold", tones[tone], className)}
      {...props}
    />
  );
}

export { Badge };
