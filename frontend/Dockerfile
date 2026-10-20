# frontend（Next.js）の実行イメージ。output: "standalone" の成果物だけを載せる
# ビルド: docker build -t knowledge-navigator/frontend frontend

FROM node:22-alpine AS deps
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

FROM node:22-alpine AS build
WORKDIR /app
RUN corepack enable
ENV NEXT_TELEMETRY_DISABLED=1
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN pnpm build

FROM node:22-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0
RUN addgroup -S -g 10001 knavi && adduser -S -u 10001 -G knavi knavi
COPY --from=build --chown=knavi:knavi /app/.next/standalone ./
COPY --from=build --chown=knavi:knavi /app/.next/static ./.next/static
COPY --from=build --chown=knavi:knavi /app/public ./public
USER knavi
EXPOSE 3000
CMD ["node", "server.js"]
