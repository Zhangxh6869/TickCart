# TickCart 前端镜像：pnpm 构建两个 Vite 应用 -> 单个 nginx 静态服务
# 刻意不写 `# syntax=docker/dockerfile:1`：本项目没用到 BuildKit 专属语法，
# 去掉后无需额外拉取 dockerfile 前端镜像，在只配了 registry-mirrors 的环境也能构建。
#
# 两个 app 都用 createWebHistory 且没配 base，不能挂在同一域名的子路径下
# （挂 /admin/ 需要改 vite base + router base + 资源路径，属于动业务代码）。
# 这里用两个 listen 端口区分，前端源码零改动：
#   80   -> mall（商城）
#   8081 -> admin（后台）

# ---------- 阶段 1：构建 ----------
# REGISTRY 为空时用官方镜像名；直连 docker.io 超时（未配 registry-mirrors）时，
# 可在 .env 里设 REGISTRY=docker.m.daocloud.io/library/ 走加速器
ARG REGISTRY=
FROM ${REGISTRY}node:20-alpine AS build

# package.json 的 packageManager 钉的是 pnpm@12.9.1，corepack 按它下载。
# 若该版本不可用，改下面的 ARG 即可，不要动 package.json（会影响本地开发）。
ARG PNPM_VERSION=12.9.1
RUN corepack enable && corepack prepare pnpm@${PNPM_VERSION} --activate

WORKDIR /app

# 先拷 workspace 清单与各包 package.json，依赖未变时可复用缓存层
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY apps/mall/package.json ./apps/mall/
COPY apps/admin/package.json ./apps/admin/
COPY packages/shared-types/package.json ./packages/shared-types/
COPY packages/shared-utils/package.json ./packages/shared-utils/
RUN pnpm install --frozen-lockfile

COPY . .
# build 脚本含 vue-tsc --noEmit，类型错误会让构建失败（与本地一致）
RUN pnpm --filter mall build && pnpm --filter admin build

# ---------- 阶段 2：运行 ----------
ARG REGISTRY=
FROM ${REGISTRY}nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/apps/mall/dist /usr/share/nginx/html/mall
COPY --from=build /app/apps/admin/dist /usr/share/nginx/html/admin
EXPOSE 80 8081
