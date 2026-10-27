<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Goods, Menu, Tickets, Search } from '@element-plus/icons-vue'
import { useAdminStore } from '@/stores/admin'

const route = useRoute()
const router = useRouter()
const adminStore = useAdminStore()

const activeMenu = computed(() => route.path)

async function onLogout() {
  await ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' })
  await adminStore.logout()
  router.replace({ name: 'login' })
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="210px" class="layout__aside">
      <div class="layout__logo">TickCart 管理后台</div>
      <el-menu :default-active="activeMenu" router class="layout__menu">
        <el-menu-item index="/dashboard">
          <el-icon><DataBoard /></el-icon>
          <span>数据看板</span>
        </el-menu-item>
        <el-menu-item index="/search-analytics">
          <el-icon><Search /></el-icon>
          <span>搜索分析</span>
        </el-menu-item>
        <el-menu-item index="/products">
          <el-icon><Goods /></el-icon>
          <span>商品管理</span>
        </el-menu-item>
        <el-menu-item index="/categories">
          <el-icon><Menu /></el-icon>
          <span>分类管理</span>
        </el-menu-item>
        <el-menu-item index="/orders">
          <el-icon><Tickets /></el-icon>
          <span>订单管理</span>
        </el-menu-item>
        <el-menu-item index="/seckill">
          <el-icon><AlarmClock /></el-icon>
          <span>秒杀管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="layout__header">
        <div class="layout__title">{{ route.meta.title }}</div>
        <div class="layout__user">
          <span>{{ adminStore.userInfo?.nickname || adminStore.userInfo?.username }}</span>
          <el-button link type="primary" @click="onLogout">退出</el-button>
        </div>
      </el-header>

      <el-main class="layout__main">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}
.layout__aside {
  background: #001529;
  color: #fff;
}
.layout__logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  color: #fff;
  border-bottom: 1px solid #1f2d3d;
}
.layout__menu {
  border-right: none;
  background: #001529;
}
.layout__menu :deep(.el-menu-item) {
  color: #b7c0cd;
}
.layout__menu :deep(.el-menu-item.is-active) {
  color: #fff;
  background: #1890ff;
}
.layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}
.layout__title {
  font-size: 16px;
  font-weight: 600;
}
.layout__user {
  display: flex;
  align-items: center;
  gap: 12px;
}
.layout__main {
  background: #f0f2f5;
}
</style>
