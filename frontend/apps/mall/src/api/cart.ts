import request from '@/utils/request'
import type { CartSummary, CartAddRequest } from '@tickcart/shared-types'

/** 购物车列表（含失效标记与勾选合计）。 */
export function fetchCart(): Promise<CartSummary> {
  return request.get('/cart')
}

/** 购物车项数（角标用）。 */
export function fetchCartCount(): Promise<number> {
  return request.get('/cart/count')
}

/** 加入购物车（同商品同规格自动合并）。 */
export function addToCart(payload: CartAddRequest): Promise<void> {
  return request.post('/cart', payload)
}

/** 修改数量；传 0 表示删除该项。 */
export function updateCartQuantity(id: number, quantity: number): Promise<void> {
  return request.put(`/cart/${id}`, null, { params: { quantity } })
}

/** 勾选/取消勾选单项。 */
export function updateCartChecked(id: number, checked: number): Promise<void> {
  return request.put(`/cart/${id}/checked`, null, { params: { checked } })
}

/** 全选/取消全选。 */
export function checkAllCart(checked: number): Promise<void> {
  return request.put('/cart/checked', null, { params: { checked } })
}

/** 删除购物车项。 */
export function removeCartItem(id: number): Promise<void> {
  return request.delete(`/cart/${id}`)
}

/** 删除已勾选项（下单成功后清理）。 */
export function removeCheckedCartItems(): Promise<void> {
  return request.delete('/cart/checked')
}
