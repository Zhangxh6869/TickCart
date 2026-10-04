# TickCart 秒杀商城 · 详细设计文档（架构与模块划分）

> 项目代号：**TickCart** | 全栈方向 · 带「限时秒杀」的完整电商交易链路
> 本文档依据《秒杀商城开发计划.md》编制，覆盖**整体架构与模块划分**，为详尽实战级设计，可直接指导后续编码。
> 范围说明：数据库表结构、接口清单等未在本篇展开（另篇专述），文中会给出模块边界与数据归属，以保证架构落地的连贯性。

---

## 1. 设计目标与约束

### 1.1 设计目标

| 编号 | 目标 | 评量口径 |
|---|---|---|
| G1 | 完整电商闭环：浏览 → 加购 → 下单 → 支付 → 订单履约 | MVP 全链路可跑通 |
| G2 | 秒杀高并发：防超卖、防重复、限购、削峰 | 压测下不超卖、不重复下单 |
| G3 | 前后端分离 + monorepo 工程化 | pnpm workspace 一套仓库管理 |
| G4 | 面试可讲清楚：架构、技术选型、模块边界均有明确依据 | 每个关键决策有 ADR 记录 |
| G5 | 单服务器可部署 | Docker Compose 一键拉起 |

### 1.2 约束与取舍

- **单一后端进程**：MVP 阶段不拆分微服务，Spring Boot 单体 + 模块化包结构（后续可平滑拆出 seckill / order 模块）。
- **模拟支付**：微信/支付宝占位，允许设计「支付回调」入口以保持真实链路形态。
- **单机中间件**：MySQL/Redis/RabbitMQ 均单实例，通过参数与设计弥补 HA 缺口（面试叙述口径：生产用集群）。

---

## 2. 总体架构

### 2.1 分层架构总览

```
┌────────────────────────────────────────────────────────────────────┐
│                          Client 端                                  │
│   前台（移动优先响应式，Vant）        后台管理（PC，Element Plus）      │
└──────────────────────────────┬─────────────────────────────────────┘
                               │ HTTPS / REST(JSON) + JWT
┌──────────────────────────────▼─────────────────────────────────────┐
│                         接入层  Nginx                                │
│  静态资源托管 · /api 反向代理 · gzip · 限流(可选 limit_req)           │
└──────────────────────────────┬─────────────────────────────────────┘
                               │
┌──────────────────────────────▼─────────────────────────────────────┐
│                     应用层 Spring Boot（单体）                        │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐        │
│  │ auth       │ │ product    │ │ order      │ │ seckill    │        │
│  │ 认证/用户   │ │ 商品/分类   │ │ 购物车/订单 │ │ 秒杀核心    │        │
│  └────────────┘ └────────────┘ └────────────┘ └────────────┘        │
│  ┌────────────┐ ┌────────────┐ ┌──────────────────────────┐        │
│  │ member     │ │ coupon     │ │ admin(后台接口/看板/权限)  │        │
│  └────────────┘ └────────────┘ └──────────────────────────┘        │
│  横切：gateway-filter(鉴权) · 统一异常 · 统一响应 · 日志 · 缓存抽象    │
└───┬───────────────┬──────────────────┬──────────────────────────────┘
    │               │                  │
┌───▼─────────┐ ┌───▼─────────────┐ ┌──▼─────────────────────────────┐
│ MySQL        │ │ Redis           │ │ RabbitMQ                        │
│ 用户/商品/    │ │ 缓存 · 秒杀库存  │ │ 秒杀下单队列 · 订单异步处理        │
│ 订单/活动等   │ │ 分布式锁 · 去重   │ │ （惰性队列+死信处理超时订单）      │
│ 业务数据      │ │ 购物车(可选)     │ │                                │
└─────────────┘ └─────────────────┘ └────────────────────────────────┘
```

### 2.2 分层职责与依赖规则

| 层 | 职责 | 依赖方向 |
|---|---|---|
| Client 前端 | 交互、渲染、状态管理；只调 `/api` | → 仅接入层 |
| 接入层 Nginx | 静态资源、反代、请求体大小限制 | → 仅应用层 |
| Controller 层 | 参数校验（JSR-303）、响应封装、路由 | → Service 层 |
| Service 层 | 业务编排、事务边界、缓存/MQ 调用 | → Repository + 中间件 |
| Repository 层 | 数据访问（JPA/MyBatis-Plus）、Redis/Lua | → 数据源 |
| 中间件层 | 数据存储 / 缓存 / 消息 | 无 |

规则约定：
1. **单向依赖**：Controller → Service → Repository，禁止反向与跨层跳过。
2. **DTO 出站**：Entity 不跨层出 Controller（面试可讲：领域模型与应用模型分离）。
3. **一切外部副作用（Redis/MQ/HTTP）走 Service**，Repository 只碰自己的数据源。
4. **秒杀模块例外声明**：SeckillController → SeckillService（含 Lua 扣减 + MQ 投递）→ SeckillConsumer（异步建单）→ OrderService（只读接口），跨模块调用走 Service 接口而非直接库表。

---

## 3. 技术选型细化（在开发计划基础上的落地口径）

### 3.1 前端

| 项 | 选型 | 版本口径 | 选定理由 / 落地说明 |
|---|---|---|---|
| 包管理 | pnpm | 9.x | workspace 原生支持，软链避免重复安装，节省磁盘 |
| 构建 | Vite | 5.x | 秒级 HMR，工程化更现代 |
| 框架 | Vue 3 | 3.4+ | 组合式 API，`<script setup>` |
| 语言 | TypeScript | 5.x | 与后端 DTO 对齐的类型共享 |
| 状态 | Pinia | 2.x | 购物车、用户态、秒杀结果的客户端缓存 |
| 路由 | Vue Router | 4.x | 路由守卫做登录态与后台权限拦截 |
| UI 前台 | Vant 4 | 4.x | 移动优先，一套代码响应式适配 PC |
| UI 后台 | Element Plus | 2.x | 表格/表单/弹窗为主的 PC 后台 |
| 请求 | Axios | 1.x | 拦截器统一携带 JWT、统一错误提示 |
| 代码质量 | ESLint + Prettier | 9.x + 3.x | 统一风格，pre-commit 挂钩（可选） |

### 3.2 后端

| 项 | 选型 | 版本口径 | 说明 |
|---|---|---|---|
| 语言/框架 | Java 17 / Spring Boot | 3.2.x | LTS；Spring Boot 3 内置 Jakarta |
| 鉴权 | spring-security + JWT | 6.x | 无状态会话；access + refresh 双令牌 |
| 密码 | BCrypt | spring-security-crypto | 不可逆加盐 |
| ORM | MyBatis-Plus（推荐） | 3.5.x | 秒杀库存更新需手写 SQL 控制，MP 更直观；备选 Spring Data JPA |
| 迁移 | Flyway | 9.x | 建表/种子数据可版本化管理，SQL 直观 |
| 参数校验 | spring-boot-starter-validation | 3.x | JSR-303，统一异常转 400 |
| 接口文档 | springdoc-openapi | 2.x | Swagger UI 自动生成 |
| 缓存客户端 | spring-data-redis + Lettuce | 3.2.x | 序列化用 GenericJackson2JsonRedisSerializer |
| MQ | spring-boot-starter-amqp | 3.2.x | RabbitMQ；秒杀队列 + 死信队列 |
| 分布式锁 | Redisson | 3.x | 看门狗续期，秒杀仅做兜底（主链路走 Lua） |

### 3.3 中间件选型口径

| 组件 | 版本 | 用途 | 关键设计点 |
|---|---|---|---|
| MySQL | 8.0.x | 主库 | utf8mb4；innodb；秒杀订单表与活动库存表需行锁/乐观锁兜底 |
| Redis | 7.x | 缓存/库存/去重/锁 | Key 设计规范见 §6.1 |
| RabbitMQ | 3.13.x | 削峰/异步 | 队列 + TTL + 死信队列处理超时未支付订单 |
| Docker | 24.x + compose v2 | 研发环境 | 见 §8 |

---

## 4. 前端模块划分（Monorepo）

### 4.1 仓库顶层结构（pnpm workspace）

```
TickCart/
├── apps/
│   ├── mall/            # 前台商城（移动优先响应式，Vant）
│   │   ├── src/
│   │   │   ├── api/             # 按业务域划分的接口封装
│   │   │   ├── assets/          # 图片等静态资源
│   │   │   ├── components/      # 全局通用组件（Vant 封装）
│   │   │   ├── composables/     # 组合式函数（倒计时 useCountdown 等）
│   │   │   ├── layouts/         # 首页/登录/订单等布局
│   │   │   ├── router/          # 路由表 + 全局守卫
│   │   │   ├── stores/          # Pinia：user / cart / seckill
│   │   │   ├── types/           # 领域类型（引用共享包）
│   │   │   ├── utils/           # 请求工具、格式化工具
│   │   │   └── views/           # 页面级组件
│   │   └── package.json
│   └── admin/           # 后台管理（PC，Element Plus）
│       ├── src/
│       │   ├── api/  assets/  components/  router/  stores/
│       │   ├── layout/         # 左侧导航 + 内容区框架
│       │   └── views/          # 商品/订单/秒杀/看板页面
│       └── package.json
├── packages/
│   ├── shared-types/    # 前后端共享的 DTO 类型定义（TS 接口）
│   └── shared-utils/    # 价格格式化、时间倒计时、请求封装等
├── pnpm-workspace.yaml
├── package.json         # 根：eslint/prettier 脚本
└── README.md
```

### 4.2 前台商城模块划分

| 模块 | 页面 | 关键组件/逻辑 | 状态存储 |
|---|---|---|---|
| 认证 | 登录/注册 | 手机验证码（模拟）/账密；JWT 双令牌存储 | `stores/user` |
| 首页 | 首页 | 轮播、分类导航、今日秒杀入口、商品流 | 请求即取 |
| 商品 | 列表/详情雨 | 分类筛选、排序、分页；SKU 规格选择、库存、收藏 | 收藏走接口 |
| 购物车 | 购物车 | 增删改查、勾选合计、失效提示 | `stores/cart`（入库，可离线缓存） |
| 交易 | 结算/提交订单/支付/订单中心 | 地址选择、订单确认、模拟支付、状态 tab | 请求即取 |
| 秒杀 | 秒杀专场/秒杀详情 | 倒计时横幅、抢购按钮、进度条、结果轮询 | `stores/seckill` |
| 个人中心 | 我的/地址/收藏 | 入口聚合 | 请求即取 |

路由守卫规则：
- 前台：`/order/*`、`/cart`、`/member/*` 需登录，未登录跳 `/login` 并记录 redirect。
- 后台：`/admin` 整棵子树需 **admin 角色** 校验，否则 403。

### 4.3 后台管理模块划分

| 模块 | 页面 | 说明 |
|---|---|---|
| 登录 | 后台登录 | 与前台共用用户表 + `role=ADMIN` 判定 |
| 商品管理 | 分类列表、商品表格 + 新建/编辑弹窗 | 上下架、改库存 |
| 订单管理 | 订单列表、详情、发货操作 | 状态流转按钮按状态控制 |
| 秒杀管理 | 活动表格、创建活动表单 | 开始/结束时间、秒杀库存、限购数 |
| 数据看板 | 今日/本周订单量、销售额、销量 Top | 图表卡片，内置简单图表情符 |

---

## 5. 后端模块划分

### 5.1 包结构（单体 + 模块化包）

```
com.tickcart
├── TickCartApplication.java        # 启动类
├── common/                         # 横切通用
│   ├── api/          # R<T> 统一响应、ResultCode 枚举
│   ├── exception/    # 全局异常处理器、BizException
│   ├── security/     # JWT 过滤器、UserContext、密码工具
│   ├── config/       # Redis/MyBatis/OpenAPI/CORS 等配置
│   ├── redis/        # RedisKey 常量、RedisService 封装
│   └── mq/           # 队列/交换机/死信声明、MessageConverter
├── auth/             # 认证与用户
│   ├── controller/ service/ repository/ entity/ dto/ mapper/
├── product/          # 分类、商品、SKU、收藏
│   ├── ...（同上结构）
├── member/           # 收货地址、个人信息
├── cart/             # 购物车（Redis 或 DB，见 §6.2 决策）
├── trade/            # 订单 + 支付回调入口
├── seckill/          # 秒杀活动、Lua 扣减、MQ 投递、消费者
├── coupon/           # 优惠券（加分项，结构预留）
└── admin/            # 后台管理接口聚合（只做编排，数据归属各域）
```

### 5.2 各模块职责与核心实体

| 模块 | 职责 | 核心实体/概念 |
|---|---|---|
| `auth` | 注册/登录、令牌签发与刷新、密码哈希 | `User(gender/role)`、`RefreshToken` |
| `product` | 分类 CRUD、商品 CRUD/上下架/库存、SKU、搜索排序、收藏 | `Category`、`Product`、`Sku`、`Favorite`、`ProductImage` |
| `member` | 收货地址 CRUD、个人信息 | `Address` |
| `cart` | 购物车项增删改查、勾选、失效校验 | `CartItem`（Redis 雪藏缓存 或 DB） |
| `trade` | 创建订单、支付回调、订单状态机、取消/确认收货 | `Order`、`OrderItem`、`OrderStatus` 状态机、`PaymentRecord` |
| `seckill` | 活动管理、库存预加载、Lua 原子扣减、限购/去重、MQ 投递、异步建房 | `SeckillActivity`、`SeckillStock`(Redis)、`SeckillOrder` |
| `coupon` | 领取/使用、下单抵扣（预留） | `Coupon`、`UserCoupon` |
| `admin` | 后台接口编排、看板聚合查询 | 复用各域 Service |

### 5.3 模块依赖图

```
         auth ◄────── 所有需要登录的模块（通过 security 过滤链，不硬依赖）
         │
         ▼
   product ──► member ──► cart ──► trade ◄── seckill
      ▲                              │            │
      └──────────────────────────────┘            │
                     （trade 通知 product 扣库存）    │
                                    seckill ──► trade（只读 OrderService，异步建秒杀订单）
```

依赖原则：`seckill ──依赖──> trade`（秒杀最终落库为订单），`trade ──依赖──> product`（下单扣/回库存）。为避免循环，扣库存统一由 **trade 模块** 声明库存变更接口（`InventoryService`），seckill 在异步建房后交由 trade 完成最终扣减。

另维护一个**秒杀链路的事件驱动视角**，见 §7.3。

---

## 6. 中间件与基础设施关键设计（架构关联部分）

### 6.1 Redis Key 设计规范

| Key 模式 | 示例 | 类型 | 用途 |
|---|---|---|---|
| `cart:user:{uid}` | `cart:user:1001` | Hash | 购物车项（若采用 Redis 方案） |
| `seckill:stock:{activityId}` | `seckill:stock:5` | String(Int) | 秒杀剩余库存（预加载） |
| `seckill:user:{uid}:{activityId}` | `seckill:user:1001:5` | SET/Flag | 去重标记，防重复下单 |
| `seckill:order:{uid}:{activityId}` | `seckill:order:1001:5` | String | 抢购成功临时凭据（供轮询） |
| `lock:seckill:{stockKey}` | `lock:seckill:5` | Redisson 锁 | 兜底锁（主链路 Lua 已完成） |

TTL 策略：活动相关 Key 设 `TTL = 活动结束时刻 - now + 缓冲(1h)`，避免缓存残留。

### 6.2 购物车存储决策（ADR-S1）

- **初版（推荐）**：购物车存 MySQL `cart_item` 表，行为简单、与订单强一致，易讲。
- **加分版**：Redis Hash `cart:user:{uid}`，需处理「勾选/合计强一致」与失效商品扫描，复杂度高。
- **决策**：MVP 用 MySQL，后续按开发节奏升级 Redis 方案并在 README 记录对比。

### 6.3 RabbitMQ 队列设计

| 队列 | 交换机 | 路由键 | 作用 | 特性 |
|---|---|---|---|---|
| `seckill.order.create` | `seckill.exchange`(direct) | `seckill.order` | 秒杀成功 → 异步建单 | 持久化、手动 ack |
| `trade.order.timeout` | `trade.exchange`(direct) | `order.timeout` | 待付款订单超时自动取消 | TTL + 死信队列 |
| `trade.order.dlx` | `trade.dlx`(direct) | `order.timeout.dead` | 死信消费 → 关单/回库存 | 死信交换 |

消费约定：手动 ack；消费失败重试 3 次后进死信；幂等（订单存在则跳过）；无消费者时限流（prefetch=10）。

### 6.4 MySQL 库设计约定（架构口径）

- 库名 `tickcart`，字符集 utf8mb4。
- 主键统一 `BIGINT`，秒杀订单与普通订单同表或分表（MVP 同表，订单号带前缀/位扩展区分渠道）。
- 秒杀活动库存字段独立于商品总库存（`seckill_stock`），防止秒杀与普通售卖互相影响。
- 所有时间字段 `datetime`，状态字段用 `tinyint` + 枚举注释。

---

## 7. 核心流程（模块协作视角）

### 7.1 普通下单流程

```
前台 → Cart/Checkout → trade/OrderController(create)
  → OrderService.create()
      ├─ 1 校验地址/购物车项/价格（快照）
      ├─ 2 事务内：扣商品库存 → 生成 Order + OrderItem
      ├─ 3 状态置 待付款，发布 order.timeout 延迟队列(15min)
      └─ 返回订单号
用户支付 → trade/PaymentController(pay, 模拟)
  → PaymentService：写 PaymentRecord，事务内 Order → 待发货
     (模拟真实形态：预留「支付回调」webhook 入口)
超时 → DLX 消费 → 订单取消 + 库存回补
```

### 7.2 秒杀流程（高并发核心） 

```
秒杀开始前（管理端创建/开启活动时）：
  admin → seckill/SeckillService.loadStockIntoRedis()
      Lua 校验 + 初始化 seckill:stock 与活动缓存

用户点击抢购：
  前台 → seckill/SeckillController(buy)
      1 登录校验（JWT）→ 2 限购/活动时间/去重标记预查（快速失败）
      3 REDIS LUA:    校验活动存在&进行中 → 校验去重(seckill:user)
                       → 校验剩余库存>0 → DECR 库存 → 打去重标记
                      （单条脚本原子执行，杜绝超卖）
      4 成功 → 投递 seckill.order.create 到 MQ → 立即返回「排队中」
        失败 → 返回「库存不足/已抢购/未开始」

SeckillConsumer 异步：
      1 幂等查重（DB 已存在则跳过）
      2 调 trade/OrderService 建秒杀订单（待付款状态）
      3 写 seckill:order 凭据(Value) 供前端轮询
      4 失败(DB异常等) → 重试3次 → 进死信 → 告警+人工对账

前端轮询：
  前台 seckillController(result) → 读取 seckill:order 凭据
      → 命中：返回订单号 → 跳支付
      → 未命中且仍排队：继续轮询（指数退避）
      → 超时(10s)：提示稍后查询「我的订单」
```

### 7.3 秒杀链路数据流（事件驱动视图）

```
用户抢购 ──HTTP──> SeckillController
   │ ① 校验/限购/时间（Redis 快速失败）
   ▼
Lua 原子扣减(Redis)  ──成功──> MQ(seckill.order.create)
   │                                  │
   │ 失败：瞬时返回「库存不足」          ▼
   └───────────────┬────────  SeckillConsumer(DB 建单，幂等)
                   │                   │
                   │                   ├─ Order(待付款) + 状态机
                   │                   └─ 凭据写回 Redis → 前端轮询命中
                   ▼
             前端轮询 → 订单号 → 支付(模拟) → 待发货
```

### 7.4 幂等与防重复策略汇总

| 场景 | 手段 | 落点 |
|---|---|---|
| 秒杀重复抢购 | Redis `seckill:user` 标记 + Lua 内校验 | 秒杀链路 |
| 秒杀限购 N 件 | 按活动校验已购数量，Lua 计数 + DB 复核 | 秒杀链路 |
| MQ 重复消费 | 消费前查 `seckill_order` 唯一键（uid+activityId） | 消费者 |
| 表单/下单重复提交 | 前端按钮禁用 + 后端 `idempotencyKey`（Redis SETNX） | trade/handler |

---

## 8. 部署与运行环境

### 8.1 Docker Compose 拓扑

```
┌──────────── docker-compose ├─────────────── service 清单 ────┐
│ nginx:         80 → 静态资源(已构建前end) + /api→mall:8080     │
│ mall(backend): Java17/Spring Boot, 8080                      │
│ mysql:         3306  volume:tickcart-mysql-data              │
│ redis:         6379  volume:tickcart-redis-data              │
│ rabbitmq:      5672/15672  volume:tickcart-rabbit-data       │
└──────────────────────────────────────────────────────────────┘
```

- 前端构建产物经 CI/本地 `pnpm build` 后挂载进 nginx。
- 环境变量：`SPRING_DATASOURCE_URL` / `SPRING_DATA_REDIS_HOST` / `SPRING_RABBITMQ_HOST` 由 compose env 注入。
- 依赖顺序：`mysql/redis/rabbitmq → backend → nginx`，后端启动重试等中间件就绪。

### 8.2 配置管理

- 多环境 profile：`dev`（本地直连）、`docker`（compose）。
- 敏感配置不入库：`application-{profile}.yml` + 环境变量覆盖。
- 统一日志格式（JSON，进 stdout），nginx 访问日志 + Spring 业务日志分离。

---

## 9. 横切关注点设计

| 关注点 | 设计 |
|---|---|
| 统一响应 | `R<T>{ code, message, data }`，`ResultCode` 枚举；Controller 无感知封装，异常由 `GlobalExceptionHandler` 统一转 JSON |
| 鉴权 | `JwtAuthenticationFilter`（OncePerRequest）校验 accessToken → `UserContext` 线程上下文；`/api/auth/refresh` 刷 token；后台接口 `@PreAuthorize("hasRole('ADMIN')")` |
| 异常 | `BizException` 业务异常（含 code/message）→ 400/404/403/429 语义；未知异常 → 500 + 日志告警 |
| 缓存抽象 | `RedisService` 封装 get/set/del + TTL 策略；缓存穿透加布隆过滤器（面试口径）；热点用本地缓存+双写一致性说明 |
| 接口文档 | springdoc-openapi 自动生成，分组：`/mall/**` 与 `/admin/**` |
| 安全 | BCrypt 密码、防越权（校验资源归属 owner）、CSRF 说明（无状态 JWT 天然免疫）、限流（秒杀接口 per-uid 限流，可选令牌桶） |

---

## 10. 关键设计决策记录（ADR 摘要）

| 编号 | 决策 | 备选 | 结论理由 |
|---|---|---|---|
| ADR-1 | 后端单体 + 模块化包 | 微服务 | 面试能讲清，复杂度可控；`seckill`/`trade` 模块边界已预留拆分位 |
| ADR-2 | MyBatis-Plus | JPA | 秒杀手写库存 SQL 直观，MP 分页/Wrapper 贴合后台 CRUD |
| ADR-3 | Lua 扣减为主、分布式锁兜底 | 纯乐观锁 / 纯SETNX | Lua 原子、一次 RTT，最贴合秒杀面试叙事 |
| ADR-4 | MQ 异步建单 + 前端轮询 | 同步建房 | 削峰核心体现；同步方案峰值时易拖垮 DB |
| ADR-5 | 移动优先响应式一套前台 | PC 独立版 | 开发计划已定；PC 精致版作后期加分项 |
| ADR-6 | 模拟支付 obfuscate 真实回调形态 | 对接沙箱 | 保链路完整骨架，后期可换沙箱 |

---

## 11. 与开发里程碑的对应

| 里程碑 | 本设计落地的关键交付 |
|---|---|
| 阶段1 基础 | monorepo 骨架、common/auth/product 模块、前端路由 + Vant 布局 |
| 阶段2 交易 | cart/member/trade 模块、订单状态机、支付回调入口、订单位延迟取消 |
| 阶段3 秒杀 | seckill 模块、Lua 脚本、MQ 队列与消费者、前端倒计时/轮询 |
| 阶段4 打磨 | admin 编排 + 看板聚合、Flyway 迁移整理、Docker Compose、README 架构图 |
| 阶段5 加分 | coupon 模块、真实沙箱、JMeter 压测留档 |

---

## 12. 附录：模块边界与后续演进

- **拆微服务路径**：`seckill` 与 `trade` 间已用 MQ 解耦投递建单，未来可先拆 `seckill-service`，`order` 独立时补「订单号全局发号」。
- **垂直扩展路径**：活动开始前 `loadStockIntoRedis` 预热 + 缓存只读化商品详情页（首页/详情接口加一层缓存），秒杀时段 DB 压力集中在单据写入。
- **面试叙事主线**：浏览商品（缓存）→ 下单（事务+状态机）→ 秒杀抢购（Redis Lua 原子扣减 + MQ 削峰 + 异步建单幂等 + 前端轮询）——一条完整的高并发故事线。