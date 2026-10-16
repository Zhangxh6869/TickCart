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

/** 订单状态码：0待付款 1待发货 2待收货 3已完成 4已取消 5已超时关闭 */
export type OrderStatusValue = 0 | 1 | 2 | 3 | 4 | 5

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
  /** 最新一笔支付流水号；用户尚未发起过支付时为 null */
  payNo?: string | null
  /** 支付渠道 0模拟 1微信 2支付宝；无支付流水时为 null */
  channel?: number | null
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
