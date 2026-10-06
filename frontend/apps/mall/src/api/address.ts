import request from '@/utils/request'
import type { UserAddress, AddressSaveRequest } from '@tickcart/shared-types'

/** 当前用户的地址列表（默认地址排最前）。 */
export function fetchAddresses(): Promise<UserAddress[]> {
  return request.get('/addresses')
}

/** 地址详情。 */
export function fetchAddress(id: number): Promise<UserAddress> {
  return request.get(`/addresses/${id}`)
}

/** 新增地址，返回新地址 ID。 */
export function createAddress(payload: AddressSaveRequest): Promise<number> {
  return request.post('/addresses', payload)
}

/** 编辑地址。 */
export function updateAddress(id: number, payload: AddressSaveRequest): Promise<void> {
  return request.put(`/addresses/${id}`, payload)
}

/** 设为默认地址。 */
export function setDefaultAddress(id: number): Promise<void> {
  return request.put(`/addresses/${id}/default`)
}

/** 删除地址。 */
export function deleteAddress(id: number): Promise<void> {
  return request.delete(`/addresses/${id}`)
}
