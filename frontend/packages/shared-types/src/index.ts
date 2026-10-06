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

/** 创建订单请求。 */
export interface OrderCreateRequest {
  addressId: number
  remark?: string
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
  /** 0待支付 1成功 2失败 */
  status: number
  payTime?: string | null
}
