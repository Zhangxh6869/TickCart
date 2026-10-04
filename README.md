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

## 文档

- `docs/` 下为详细设计与阶段划分（见《TickCart详细设计-架构与模块.md》《TickCart项目阶段划分.md》）

## 启动（待补）

- 见各阶段 checklist（《TickCart项目阶段划分.md》）
