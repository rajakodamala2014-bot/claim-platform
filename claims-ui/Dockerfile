# syntax=docker/dockerfile:1.7
# Claimant portal: build with Node, serve the static bundle with nginx (SPA fallback). Reached via the gateway.
#   docker build -t claims-ui claims-ui
FROM node:22-alpine AS build
WORKDIR /ui
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /ui/dist /usr/share/nginx/html
