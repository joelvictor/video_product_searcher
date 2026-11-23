import path from "node:path";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

const backend = "http://localhost:8080";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: { alias: { "@": path.resolve(__dirname, "./src") } },
  // API, links curtos e mídia assinada vêm do backend (mesma origem, como no nginx de produção)
  server: { proxy: { "/api": backend, "/l": backend, "/m": backend } },
});
