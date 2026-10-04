<script setup lang="ts">
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <div class="not-found">
    <van-empty description="页面不存在" />
    <div class="not-found__btn">
      <van-button round type="danger" @click="router.replace('/')">返回首页</van-button>
    </div>
  </div>
</template>

<style scoped>
.not-found__btn {
  text-align: center;
  margin-top: 12px;
}
</style>
