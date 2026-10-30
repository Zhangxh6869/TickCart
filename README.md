# TickCart · 秒杀商城

> 全栈电商项目，核心亮点为「限时秒杀」的高并发处理。
> 前后端分离的工程化架构。

## 目录结构

```
TickCart/
├── frontend/          # 前端（pnpm monorepo）
│   ├── apps/mall/     # 前台商城（Vue3 + Vant，移动优先响应式）
│   ├── apps/admin/    # 后台管理（Vue3 + Element Plus，PC）
│   └── packages/      # 共享包
│       ├── shared-types/  # 前后端共享类型
│       └── shared-utils/  # 共享工具
├── backend/           # 后端（Java 17 + Spring Boot 3）
│   └── src/main/java/com/tickcart/  # 模块化包
├── docker/            # MySQL / Redis / RabbitMQ / Nginx 配置
├── docs/              # 设计文档
└── scripts/           # 辅助脚本
```

## 技术栈

- 前端：Vue 3 + TypeScript + Vite + Pinia + Vue Router + Vant（前台）/ Element Plus（后台）
- 后端：Java 17 + Spring Boot 3 + JWT + MyBatis-Plus + Flyway
- 数据与中间件：MySQL 8 + Redis 7 + RabbitMQ 3.13
- 部署：Docker + docker-compose

## 快速启动

### 0. 环境要求

| 依赖 | 版本 | 说明 |
|---|---|---|
| JDK | 17+ | 后端（已在 JDK 21 上验证通过） |
| Maven | 3.9+ | 后端构建 |
| Node | 18+ | 前端 |
| pnpm | 12.9.1 | 前端包管理（版本已由 `frontend/package.json` 的 `packageManager` 钉住） |
| Docker Desktop | 最新版 | 本地中间件（MySQL / Redis / RabbitMQ） |

### 1. 启动中间件

```bash
docker compose -f docker/docker-compose.dev.yml up -d
```

| 组件 | 宿主机端口 | 账号 |
|---|---|---|
| MySQL 8 | **3307** → 容器 3306 | root / root，库 `tickcart` |
| Redis 7 | 6379 | — |
| RabbitMQ 3.13 | 5672 / 15672 | guest / guest |

> ⚠️ **MySQL 为什么映射到 3307**：宿主机 3306 常被本机自带的 MySQL 服务（Windows 服务名 `MySQL80`）占用，容器改用 3307 规避冲突。**因此后端启动必须指定 `MYSQL_PORT=3307`**（见下一步）。

### 2. 启动后端

```bash
cd backend
```

```powershell
# PowerShell
$env:MYSQL_PORT="3307"; mvn spring-boot:run
```

```bash
# Git Bash / macOS / Linux
MYSQL_PORT=3307 mvn spring-boot:run
```

- 启动时 **Flyway 自动执行 `V1→V22` 迁移**（建表 + 种子数据），无需手动建表
- 服务地址：http://localhost:8080
- 健康探针：http://localhost:8080/api/health
- Swagger UI：http://localhost:8080/swagger-ui/index.html

> 若报「端口 xxx 被占用」，可显式指定端口（命令行参数优先级最高）：
> `mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8080"`

### 3. 启动前端

```bash
cd frontend
pnpm install        # 首次
pnpm dev:mall       # 前台 → http://localhost:5173
pnpm dev:admin      # 后台 → http://localhost:5174（另开一个终端）
```

两个 app 的 `vite.config.ts` 均已把 `/api` 反代到 `http://localhost:8080`，无需额外配跨域。

### 4. 访问地址与账号

| 入口 | 地址 | 账号 |
|---|---|---|
| 前台商城 | http://localhost:5173 | `demo` / `123456` |
| 后台管理 | http://localhost:5174 | `admin` / `admin123` |
| 短信验证码 | — | 开发环境 Mock，固定 `123456` |

### 5. 常见问题

- **Docker 报 `docker_engine` / `dockerDesktopLinuxEngine` 管道不存在**：Docker Desktop 未启动或引擎未就绪。重启 Docker Desktop，等鲸鱼图标变绿后再执行。
- **MySQL 容器起不来、提示 3306 端口被占用**：本机 MySQL 服务占用了 3306。本项目已改用 3307；若仍冲突，可停掉本机服务（管理员执行 `net stop MySQL80`）。
- **`ERR_PNPM_IGNORED_BUILDS`**：pnpm 新版默认拦截依赖的构建脚本。白名单已配置在 `frontend/pnpm-workspace.yaml` 的 `allowBuilds`（esbuild / vue-demi）。

---

## 一键部署（Docker Compose）

上面是本地开发方式（中间件容器化、应用在 IDE 里跑）。想把整套系统跑进容器：

```bash
cp .env.example .env            # 至少改掉 JWT_SECRET
docker compose up -d --build    # 首次约 5~8 分钟
docker compose ps               # 5 个服务全部 healthy 后即可访问
```

| 入口 | 地址 | 账号 |
|---|---|---|
| 商城 | http://localhost | demo / 123456 |
| 后台 | http://localhost:8081 | admin / admin123 |
| 接口文档 | http://localhost:8080/swagger-ui/index.html | — |
| RabbitMQ 管理台 | http://localhost:15672 | guest / guest |

**镜像拉取**：国内直连 `docker.io` 常超时，二选一

- 给 Docker Desktop 配 `registry-mirrors`（Settings → Docker Engine），一次配好所有镜像生效
- 或在 `.env` 里设 `REGISTRY=docker.m.daocloud.io/library/`（结尾必须带斜杠）

**几点说明**

- **数据库无需手动初始化**：backend 启动时 Flyway 自动执行 `V1→V22` 建表并灌种子数据，`start_period: 90s` 就是留给迁移的。
- 演示图片（仓库 `imgs/`）以只读挂载、上传目录以命名卷挂进 backend，不打进镜像，换图不必重建镜像。
- 商城与后台用 `80` / `8081` 两个端口，而不是 `/admin/` 子路径——两个前端都用 `createWebHistory` 且未配 `base`，挂子路径要改 `vite base` + `router base`，不值当。
- 与 `docker/docker-compose.dev.yml` **二选一**：两者都映射 `15672`，同时跑会端口冲突。
- 本地 IDE 若已在 `8080` 跑后端，先停掉再 `up`。

常用命令

```bash
docker compose logs -f backend                        # 跟踪后端日志
docker compose exec mysql mysql -uroot -proot tickcart # 进库
docker compose down                                   # 停止，保留数据
docker compose down -v                                # 停止并清空数据
```

---

## 详细设计

- 设计文档见根目录《TickCart详细设计-架构与模块.md》《TickCart项目阶段划分.md》
- 数据库口径见 `docs/数据库设计说明.md`
