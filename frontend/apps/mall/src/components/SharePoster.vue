<script setup lang="ts">
import { ref, watch } from 'vue'
import { buildProductShareUrl, type ShareProduct } from '@/utils/share'
import { renderPoster } from '@/utils/poster'

/**
 * 商品分享海报弹层：把「商品图 + 标题 + 价格 + 二维码」画成一张图，供用户长按保存。
 *
 * <p>二维码由前端 {@code qrcode-generator} 生成、海报由 Canvas 绘制，**不请求后端**。
 * 商品图与二维码都同源 / 同画布，canvas 不会被污染，{@code toDataURL()} 安全。</p>
 */
const props = defineProps<{ show: boolean; product: ShareProduct }>()
const emit = defineEmits<{ 'update:show': [boolean] }>()

const posterUrl = ref('')
const generating = ref(false)

watch(
  () => props.show,
  async (show) => {
    if (!show) return
    generating.value = true
    posterUrl.value = ''
    try {
      posterUrl.value = await renderPoster({
        title: props.product.name,
        price: props.product.price,
        imageUrl: props.product.mainImage,
        shareUrl: buildProductShareUrl(props.product.id),
      })
    } catch {
      // 失败就停在「生成失败」文案上，不把异常抛给上层
      posterUrl.value = ''
    } finally {
      generating.value = false
    }
  }
)
</script>

<template>
  <van-popup
    :show="show"
    round
    position="center"
    class="poster"
    :style="{ width: '78%' }"
    @update:show="(value) => emit('update:show', value)"
  >
    <div class="poster__body">
      <van-loading v-if="generating" class="poster__loading" size="24">正在生成海报…</van-loading>
      <template v-else-if="posterUrl">
        <img class="poster__img" :src="posterUrl" alt="商品分享海报" />
        <p class="poster__tip">长按图片保存到相册</p>
        <a class="poster__save" :href="posterUrl" :download="`tickcart-${product.id}.png`">
          保存海报
        </a>
      </template>
      <p v-else class="poster__tip">海报生成失败，请稍后重试</p>
    </div>
    <button class="poster__close" type="button" aria-label="关闭" @click="emit('update:show', false)">
      <van-icon name="cross" size="16" />
    </button>
  </van-popup>
</template>

<style scoped>
.poster__body {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 16px 20px;
}
.poster__loading {
  padding: 80px 0;
  color: var(--van-text-color-2);
}
.poster__img {
  display: block;
  width: 100%;
  border-radius: 8px;
}
.poster__tip {
  margin: 12px 0 0;
  color: var(--van-text-color-2);
  font-size: 13px;
}
.poster__save {
  margin-top: 12px;
  padding: 8px 28px;
  border-radius: 999px;
  background: var(--van-primary-color);
  color: #fff;
  font-size: 14px;
  text-decoration: none;
}
.poster__close {
  position: absolute;
  top: 10px;
  right: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: var(--van-gray-2);
  color: var(--van-text-color-2);
  cursor: pointer;
}
</style>
