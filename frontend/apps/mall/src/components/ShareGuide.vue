<script setup lang="ts">
import { computed } from 'vue'
import type { ShareGuideMode } from '@/utils/share'

/**
 * 「点右上角」引导蒙层。
 *
 * <p>H5 没有调起微信原生分享的能力，微信内置浏览器又会拦截 QQ / QQ空间 / 微博 的分享页域名，
 * 这两类场景只能把用户引到浏览器右上角的菜单去 —— 这是 H5 分享的标准做法，
 * 不是「功能没做完」。</p>
 */
const props = defineProps<{ show: boolean; mode: ShareGuideMode }>()
const emit = defineEmits<{ 'update:show': [boolean] }>()

const TEXT: Record<ShareGuideMode, string[]> = {
  wechat: ['点击右上角「···」', '选择「发送给朋友」'],
  moments: ['点击右上角「···」', '选择「分享到朋友圈」'],
  external: ['点击右上角「···」', '选择「在浏览器中打开」后再分享'],
}

const lines = computed(() => TEXT[props.mode])
</script>

<template>
  <!-- z-index 给到 3000：Vant 的 popup 全局自增从 2000 起，蒙层必须压在所有弹层之上 -->
  <van-overlay
    :show="show"
    :z-index="3000"
    class="share-guide"
    @click="emit('update:show', false)"
  >
    <div class="share-guide__inner">
      <van-icon class="share-guide__arrow" name="arrow-up" size="30" color="#fff" />
      <p v-for="line in lines" :key="line" class="share-guide__line">{{ line }}</p>
    </div>
  </van-overlay>
</template>

<style scoped>
/* 整屏可点关闭（含文案区域），点哪都退 */
.share-guide {
  display: flex;
  justify-content: flex-end;
  padding: 14px 20px 0;
}
.share-guide__inner {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}
/* arrow-up 旋转 45° → 指向右上角 */
.share-guide__arrow {
  transform: rotate(45deg);
}
.share-guide__line {
  margin: 6px 0 0;
  color: #fff;
  font-size: 16px;
  line-height: 26px;
  text-align: right;
}
</style>
