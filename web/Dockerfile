# Contexto de build: raiz do monorepo (precisa do packages/shared)
FROM node:22-alpine AS build
RUN corepack enable
WORKDIR /repo
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY web/package.json web/
COPY packages/shared/package.json packages/shared/
RUN pnpm install --frozen-lockfile --filter @vps/web...
COPY packages packages
COPY web web
RUN pnpm --filter @vps/web build

FROM nginx:1.27-alpine
COPY web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /repo/web/dist /usr/share/nginx/html
EXPOSE 80
