FROM node:24-alpine AS build
WORKDIR /app
ARG VITE_OIDC_URL=http://localhost:8180
ARG VITE_OIDC_REALM=document-platform
ARG VITE_OIDC_CLIENT_ID=document-platform-web
ENV VITE_OIDC_URL=$VITE_OIDC_URL VITE_OIDC_REALM=$VITE_OIDC_REALM VITE_OIDC_CLIENT_ID=$VITE_OIDC_CLIENT_ID
RUN corepack enable
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

# 非 root 运行：nginx 官方维护的 unprivileged 镜像（uid 101），临时文件与 pid 写入 /tmp，兼容只读根文件系统
FROM nginxinc/nginx-unprivileged:1.30-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
USER 101
EXPOSE 8088
HEALTHCHECK --interval=15s --timeout=3s --retries=5 CMD wget -qO- http://127.0.0.1:8088/healthz || exit 1
