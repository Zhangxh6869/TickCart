import request from '@/utils/request'

/** 单张图片大小上限（MB），与后端 `tickcart.upload.max-size-mb` 保持一致。 */
export const MAX_IMAGE_SIZE_MB = 5

/** 后端白名单支持的图片类型。 */
export const ACCEPT_IMAGE_TYPES = 'image/jpeg,image/png,image/gif,image/webp'

/**
 * 校验本地图片文件。
 *
 * <p>主图、图集、SKU 图、详情插图四处都要选图，校验规则必须只有一份 ——
 * 分散写就会出现「这里拦了 5MB、那里没拦」。</p>
 *
 * @returns 通过返回 {@code null}，否则返回可直接展示的提示文案
 */
export function validateImageFile(file: File): string | null {
  if (!file.type.startsWith('image/')) {
    return '请选择图片文件'
  }
  if (file.size > MAX_IMAGE_SIZE_MB * 1024 * 1024) {
    return `图片大小不能超过 ${MAX_IMAGE_SIZE_MB}MB`
  }
  return null
}

/**
 * 上传图片，返回可直接访问的相对 URL（形如 `/api/files/2026/10/07/xxx.jpg`）。
 *
 * <p>该 URL 可直接写入 `product.main_image`，前台 `<img src>` 与后台预览都能直接使用。</p>
 */
export function uploadImage(file: File): Promise<string> {
  const formData = new FormData()
  formData.append('file', file)
  // 不手动设置 Content-Type：交给 axios/浏览器带上 multipart boundary
  return request.post('/admin/upload', formData, { timeout: 30000 })
}
