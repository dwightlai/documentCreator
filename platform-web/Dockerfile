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

FROM nginx:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s --retries=5 CMD wget -qO- http://127.0.0.1/healthz || exit 1
