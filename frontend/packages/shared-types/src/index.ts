/** 统一响应体，与后端 com.tickcart.common.api.R 对齐。 */
export interface R<T = unknown> {
  code: number
  message: string
  data: T
}

/** 分页结果，与后端 PageResult 对齐。 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
  pages: number
}

/** 令牌信息。 */
export interface TokenResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
}

/** 用户信息。 */
export interface UserInfo {
  id: number
  username: string
  phone: string | null
  nickname: string | null
  avatar: string | null
  gender: number
  role: number
}

/** 商品分类。 */
export interface Category {
  id: number
  parentId: number
  name: string
  icon?: string | null
  level: number
  sort: number
  status: number
  /**
   * 库存预警线；`null` / 不传 = 继承父分类、最终继承全局配置。
   *
   * <p>刻意是「可空」而不是给默认值 10：null 表示「没配过」，
   * 一旦填了 10 就表示「就是 10」，两者在全局默认值变化时行为不同。</p>
   */
  stockWarnThreshold?: number | null
}

/** 商品列表项。 */
export interface ProductListItem {
  id: number
  categoryId: number
  name: string
  subtitle?: string | null
  mainImage?: string | null
  price: number
  stock: number
  sales: number
  status: number
  isSeckill: number
  /**
   * 库存是否低于预警线。
   *
   * <p>由后端算好下发，前端**不持有阈值**：阈值按分类差异化（`product_category.stock_warn_threshold`）
   * 且会沿父分类继承，前端自己算必然和服务端两套口径。前端只负责把 true 渲染成红标。</p>
   */
  stockLow?: boolean
  createdAt?: string
}

/**
 * 浏览记录列表项 = 商品列表项 + 最近一次浏览时间。
 *
 * 刻意做成**扁平结构**（不是嵌套 `product` 对象），这样可以直接交给 `ProductCard` 渲染，
 * 浏览记录页不必单独维护一套商品卡片。
 */
export interface ViewHistoryItem extends ProductListItem {
  /**
   * 最近一次浏览时间（ISO 8601 本地时间串，形如 `2026-10-16T22:18:56`）。
   * 列表按它倒序；前端取前 10 位（日期部分）做「今天 / 昨天 / 日期」分组，
   * ★ 不要用 `new Date()` 解析后再取日期，时区换算会把「今天」算成「昨天」。
   */
  viewTime: string
}

/** 商品 SKU。 */
export interface ProductSku {
  id: number
  productId: number
  specName?: string | null
  specValue?: string | null
  skuImage?: string | null
  price: number
  stock: number
}

/** 商品图集。 */
export interface ProductImage {
  id: number
  productId: number
  url: string
  sort: number
}

/** 商品详情。 */
export interface ProductDetail {
  id: number
  categoryId: number
  categoryName?: string | null
  name: string
  subtitle?: string | null
  /** 搜索补充关键词（英文逗号分隔），用户会搜但商品名里没有的品类词/别名。 */
  searchKeywords?: string | null
  mainImage?: string | null
  detail?: string | null
  price: number
  stock: number
  sales: number
  status: number
  isSeckill: number
  skus: ProductSku[]
  images: ProductImage[]
}

/** 商品列表查询条件。 */
export interface ProductQuery {
  page?: number
  size?: number
  keyword?: string
  categoryId?: number | null
  status?: number | null
  sortBy?: string
  order?: string
  /**
   * 随机种子。传入后忽略 `sortBy` / `order`，按随机顺序分页：
   * 同一个种子下顺序稳定，翻页不重复也不漏项；换种子即换一批随机结果。
   */
  seed?: number | null
}

/** 收货地址。 */
export interface UserAddress {
  id: number
  userId: number
  receiver: string
  phone: string
  province: string
  city: string
  district: string
  detail: string
  /** 是否默认 0否 1是 */
  isDefault: number
  createdAt?: string
  updatedAt?: string
}

/** 收货地址新增/编辑请求。 */
export interface AddressSaveRequest {
  receiver: string
  phone: string
  province: string
  city: string
  district: string
  detail: string
  /** 是否设为默认 0否 1是；编辑时传 1 才生效 */
  isDefault?: number
}

/** 购物车项（购物车记录 + 商品/SKU 实时信息）。 */
export interface CartItem {
  id: number
  productId: number
  skuId: number
  quantity: number
  /** 是否勾选 0否 1是 */
  checked: number
  productName: string
  productImage?: string | null
  specValue?: string | null
  price: number
  stock: number
  subtotal: number
  /** 商品下架 / 规格删除 / 库存不足时为 false */
  valid: boolean
  invalidReason?: string | null
}

/** 购物车汇总。 */
export interface CartSummary {
  items: CartItem[]
  /** 勾选且有效的总件数 */
  checkedCount: number
  /** 勾选且有效的合计金额 */
  checkedAmount: number
  /** 购物车项总条数 */
  totalCount: number
}

/** 加入购物车请求。 */
export interface CartAddRequest {
  productId: number
  /** 无规格商品传 0 或省略 */
  skuId?: number
  quantity: number
}

/** 订单状态码：0待付款 1待发货 2待收货 3已完成 4已取消 5已超时关闭 6退款中 7已退款 */
export type OrderStatusValue = 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7

/**
 * 退款单状态：0待审核 1已退款 2已拒绝 3已撤销 4待寄回 5待收货
 *
 * ★ 4/5 不是终态：退货退款同意后还要用户寄货、商家验收，这期间退款没结束。
 */
export type RefundStatusValue = 0 | 1 | 2 | 3 | 4 | 5

/** 退款单（订单详情里挂的最新一条）。 */
export interface RefundSummary {
  refundNo: string
  orderNo: string
  /** 1未发货退款 2仅退款 3退货退款 */
  type: number
  typeLabel: string
  /** 退款金额（快照，后端按支付成功流水算，前端只展示） */
  amount: number
  reasonCode: number
  reasonLabel: string
  reason?: string | null
  status: RefundStatusValue
  statusLabel: string
  /** 审核意见，拒绝时必有 */
  auditRemark?: string | null
  createdAt: string
  auditTime?: string | null
  refundTime?: string | null
  /** 退货收货地址；仅「待寄回」时后端下发，用户按此寄回 */
  returnAddress?: string | null
  /** 退货快递公司 */
  returnCompany?: string | null
  /** 退货物流单号 */
  returnTrackingNo?: string | null
  /** 用户寄出时间 */
  returnTime?: string | null
  /** 商家确认收到退货的时间 */
  receiveTime?: string | null
  /** 验收备注 */
  receiveRemark?: string | null
}

/** 订单明细项（下单时的快照）。 */
export interface OrderItem {
  productId: number
  skuId: number
  productName: string
  productImage?: string | null
  specValue?: string | null
  price: number
  quantity: number
  subtotal: number
}

/** 订单列表项。 */
export interface OrderSummary {
  orderNo: string
  status: OrderStatusValue
  statusLabel: string
  totalAmount: number
  payAmount: number
  totalQuantity: number
  createdAt: string
  items: OrderItem[]
}

/** 后台订单列表项：比前台多带买家信息（后台需知道「是谁下的单」）。 */
export interface AdminOrderSummary extends OrderSummary {
  userId: number
  receiver: string
  receiverPhone: string
  /** 收货地址（快照），列表直接展示摘要，客服不用点开详情 */
  receiverAddr?: string | null
  /** 最新一笔支付流水号；用户尚未发起过支付时为 null */
  payNo?: string | null
  /** 支付渠道 0模拟 1微信 2支付宝；无支付流水时为 null */
  channel?: number | null
  /** 支付时间；未支付时为 null。后台用它算「已付款等待时长」 */
  payTime?: string | null
  /** 发货时间；未发货时为 null */
  shipTime?: string | null
  /** 取消时间；未取消时为 null */
  cancelTime?: string | null
  /** 是否秒杀订单（订单来源渠道，与支付渠道 channel 无关） */
  seckill?: boolean
}

/** 订单详情。 */
export interface OrderDetail {
  orderNo: string
  status: OrderStatusValue
  statusLabel: string
  totalAmount: number
  payAmount: number
  receiver: string
  receiverPhone: string
  receiverAddr: string
  remark?: string | null
  createdAt: string
  payTime?: string | null
  shipTime?: string | null
  finishTime?: string | null
  cancelTime?: string | null
  items: OrderItem[]
  /** 最新一条退款单；从未申请过退款时为 null（前端据此决定显示申请入口还是退款进度） */
  refund?: RefundSummary | null
}

/**
 * 后台订单详情：在前台共用的 {@link OrderDetail} 之上补「支付语义」。
 *
 * ★ 判断「有没有收到钱」只看 `payStatus`，不要看 `payNo` 是否为空 ——
 * 已取消的订单也可能挂着一条「待支付」流水。
 */
export interface AdminOrderDetail extends OrderDetail {
  userId: number
  /** 支付状态 0待支付 1支付成功 2支付失败 3已关闭；从未发起支付时为 null */
  payStatus?: number | null
  payStatusLabel?: string | null
  /** 支付渠道 0模拟 1微信 2支付宝 */
  payChannel?: number | null
  payNo?: string | null
}

/** 后台订单概览（页头 KPI 与状态筛选计数）。 */
export interface OrderOverview {
  total: number
  pendingPayment: number
  pendingShipment: number
  pendingReceipt: number
  completed: number
  cancelled: number
  timeoutClosed: number
  /** 退款中（待审核） */
  refunding: number
  /** 已退款 */
  refunded: number
  /** 待发货中最早一笔支付时间；无待发货订单时为 null */
  oldestPendingShipmentTime?: string | null
  /** 已付款订单数（待发货 + 待收货 + 已完成） */
  paidOrderCount: number
  paidAmount: number
}

/** 批量发货结果：允许部分成功，失败项带原因。 */
export interface BatchShipResult {
  success: string[]
  failed: { orderNo: string; reason: string }[]
}

/** 创建订单请求（结算购物车中已勾选的商品）。 */
export interface OrderCreateRequest {
  addressId: number
  remark?: string
}

/**
 * 立即购买请求：商品详情页直达下单，不经过购物车。
 * 商品与规格由客户端指定，服务端据此重新校验价格与库存。
 */
export interface OrderBuyNowRequest {
  addressId: number
  productId: number
  /** 无规格商品传 0 或省略 */
  skuId?: number
  quantity: number
  remark?: string
}

/**
 * 确认订单页展示的待结算商品。
 *
 * 购物车结算与立即购买两种来源都归一成这个结构，页面渲染与金额计算只认它，
 * 不再各自拼一遍。
 */
export interface CheckoutItem {
  productId: number
  skuId: number
  productName: string
  productImage?: string | null
  specValue?: string | null
  price: number
  quantity: number
  subtotal: number
}

/** 下单结果。 */
export interface OrderCreateResult {
  orderNo: string
  status: OrderStatusValue
  totalAmount: number
  payAmount: number
  createdAt: string
}

/** 订单列表查询条件。 */
export interface OrderQuery {
  status?: number | null
  /**
   * 多状态筛选，优先级高于 status。
   *
   * 「退款/售后」页签要同时覆盖退款中(6) 与已退款(7)，单值 status 表达不了。
   * ★ 传数组，由 api 层拼成逗号分隔字符串（`statuses=6,7`）再发 ——
   * axios 默认会把数组序列化成 `statuses[]=6&statuses[]=7`，Spring 绑不上。
   */
  statuses?: number[]
  page?: number
  size?: number
}

/**
 * 各状态订单数量，「我的」页订单宫格用它渲染角标。
 *
 * 只含宫格上的四个状态；已取消 / 已超时关闭是终态、不做提醒，后端不返回。
 * 数量恒为数字（没有订单时是 0），前端不需要为 null 兜底。
 */
export interface OrderStatusCount {
  pendingPayment: number
  pendingShipment: number
  pendingReceipt: number
  completed: number
}

/** 支付渠道：1微信 2支付宝（0为模拟，前端不直接使用） */
export type PaymentChannel = 1 | 2

/** 发起支付请求。 */
export interface PaymentRequest {
  channel: PaymentChannel
}

/** 支付结果。 */
export interface PaymentResult {
  payNo: string
  orderNo: string
  amount: number
  channel: number
  /** 0待支付 1成功 2失败 3已关闭；发起支付时恒为 0 */
  status: number
  payTime?: string | null
}

/** 支付流水状态码：0待支付 1成功 2失败 3已关闭 */
export type PaymentStatusValue = 0 | 1 | 2 | 3

/**
 * 支付状态查询结果（收银台轮询用）。
 *
 * 同时带出订单状态与最新一笔流水状态，前端据此判断结果：
 * orderStatus=1 即成功；orderStatus=0 且 payStatus=0 表示仍在支付中；
 * payStatus=2/3 表示失败/取消，订单仍是待付款，可以重新发起支付。
 */
export interface PaymentStatusResult {
  orderNo: string
  orderStatus: OrderStatusValue
  orderStatusLabel: string
  /** 尚未发起过支付时为 null */
  payNo?: string | null
  payStatus?: PaymentStatusValue | null
  payStatusLabel?: string | null
  channel?: number | null
  payTime?: string | null
}

/**
 * 模拟渠道提交的支付结果。
 *
 * 真实渠道只回调「成功」，失败与取消由商户主动查单得到；模拟环境为了让前端
 * 被迫处理「结果不确定」的分支，把三种结果都做成可主动触发的。
 */
export type MockChannelResult = 'success' | 'fail' | 'cancel'

// ---------------------------------------------------------------------------
// 秒杀活动（B1）
// 状态由后端按 (enabled, startTime, endTime) 推导，前端只读不写。
// ---------------------------------------------------------------------------

/** 秒杀活动状态码：0已下线 1即将开始 2进行中 3已结束 */
export type SeckillStatus = 0 | 1 | 2 | 3

export interface SeckillActivity {
  id: number
  title: string
  productId: number
  productName?: string | null
  productImage?: string | null
  skuId: number
  specValue?: string | null
  /** 商品原价（有规格时取规格价） */
  originalPrice?: number | null
  seckillPrice: number
  seckillStock: number
  soldCount: number
  /** DB 口径剩余库存；B2 之后前台展示以 Redis 为准 */
  remainingStock: number
  limitPerUser: number
  startTime: string
  endTime: string
  enabled: number
  status: SeckillStatus
  statusLabel: string
  /** 秒杀成交额（仅已支付订单） */
  paidAmount: number
  /** 秒杀成交单数（仅已支付订单） */
  paidOrderCount: number
  /** 是否已预热到 Redis（已结束的活动按设计不预热） */
  preloaded: boolean
}

/** 秒杀活动概览：页头 KPI 与状态 chips 的计数 */
export interface SeckillOverview {
  total: number
  ongoing: number
  pending: number
  ended: number
  disabled: number
  /** 已结束但仍为上线状态的数量（不影响前台，只是数据没收尾） */
  endedButEnabled: number
  paidAmount: number
  paidOrderCount: number
  /** 已售件数（活动 sold_count 之和，含待付款） */
  soldQuantity: number
  nextStartTime?: string | null
  ongoingEndTime?: string | null
}

export interface SeckillActivitySaveRequest {
  title: string
  productId: number
  skuId?: number | null
  seckillPrice: number
  seckillStock: number
  limitPerUser: number
  startTime: string
  endTime: string
}

export interface SeckillActivityQuery {
  page?: number
  size?: number
  enabled?: number
  status?: SeckillStatus
  /** 活动标题或商品名 */
  keyword?: string
  /** 只看进行中 + 即将开始（与 status 互斥，status 优先） */
  activeOnly?: boolean
  /** 排序字段；不传按「进行中→即将开始→已结束→已下线」沉底 */
  sortBy?: 'startTime' | 'endTime' | 'id'
  order?: 'asc' | 'desc'
  /** 开始日期（含），格式 YYYY-MM-DD */
  beginDate?: string
  /** 结束日期（含），格式 YYYY-MM-DD */
  endDate?: string
}

// ---------------------------------------------------------------------------
// 秒杀抢购（B3/B4）
// ---------------------------------------------------------------------------

/** 抢购受理结果：拿到 requestId 表示已占名额，订单由消费者异步创建 */
export interface SeckillBuyResult {
  requestId: string
  /** 扣减后的剩余库存（Redis 口径） */
  remainingStock: number
  /** QUEUED 表示排队中 */
  status: string
}

/** 抢购结果（轮询用） */
export interface SeckillResult {
  requestId: string
  status: 'SUCCESS' | 'FAILED'
  orderNo?: string | null
  message?: string | null
}

// ---------------------------------------------------------------------------
// 补货待办（SKU 粒度）
// ---------------------------------------------------------------------------

/**
 * 补货待办的一行 = 一个告急的 SKU。
 *
 * 粒度是 SKU 而不是商品：总库存 120 但 43 码 0 件时，商品级的 `stockLow` 看不出来，
 * 用户却买不到那个尺码。所以列表没标红、这里出现，是刻意的口径差异，不是 bug。
 */
export interface LowStockItem {
  productId: number
  productName: string
  mainImage?: string | null
  categoryId: number
  categoryName?: string | null
  skuId: number
  specName?: string | null
  specValue?: string | null
  skuImage?: string | null
  stock: number
  /** 所属分类的有效预警线 */
  threshold: number
  /** 缺口 = threshold - stock */
  shortage: number
  /** 剩余比例 = stock / threshold，0 表示已断货 */
  remainRatio: number
}

/** 补货待办清单 */
export interface LowStockReport {
  /** 全库告急 SKU 总数，不受 limit 影响 */
  total: number
  /** 其中已断货（库存 0）的数量 */
  outOfStock: number
  truncated: boolean
  items: LowStockItem[]
}

// ---------------------------------------------------------------------------
// 分类总览（分类管理页）
// ---------------------------------------------------------------------------

/** 分类 + 它在商品侧的用量，供分类管理页判断「能不能删、要不要补货」 */
export interface CategoryOverview {
  id: number
  name: string
  parentId: number
  sort: number
  status: number
  /** 自己配的预警线；null 表示继承 */
  stockWarnThreshold?: number | null
  /** 生效的预警线（继承后的值），由后端解析下发 */
  effectiveThreshold?: number | null
  productCount: number
  lowStockCount: number
}

// ---------------------------------------------------------------------------
// 后台运营看板
// ---------------------------------------------------------------------------

/** 按日趋势数据点（近 N 天订单量 / 成交额）。后端已补齐无数据的天为 0。 */
export interface DailyPoint {
  /** yyyy-MM-dd */
  day: string
  /** 当日订单量 / 当日成交单数 */
  count: number
  /** 当日成交额（仅成交额趋势使用） */
  amount: number
}

/** 后台运营看板聚合响应：一次拉齐订单 / 秒杀 / 库存 / 趋势。 */
export interface DashboardVO {
  order: OrderOverview
  seckill: SeckillOverview
  /** 战报明细：按「进行中→即将开始→已结束→已下线」取前几场 */
  seckillActivities: SeckillActivity[]
  category: CategoryOverview[]
  /** 库存告急明细（SKU 级，带库存与预警线） */
  lowStock: LowStockReport
  orderTrend: DailyPoint[]
  amountTrend: DailyPoint[]
}
