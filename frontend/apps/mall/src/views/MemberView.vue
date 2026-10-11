<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog } from 'vant'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const isLoggedIn = computed(() => userStore.isLoggedIn)
const avatarUrl = computed(() => userStore.userInfo?.avatar || '')

/** 头部主标题：未登录时这一行本身就是登录入口的文案 */
const displayName = computed(() =>
  isLoggedIn.value ? userStore.userInfo?.nickname || userStore.userInfo?.username || '' : '点击登录'
)

/** 头部副标题：未登录时说明登录之后能得到什么，已登录时就是手机号 */
const subText = computed(() =>
  isLoggedIn.value ? userStore.userInfo?.phone || '' : '登录后查看订单与收货地址'
)

/**
 * 未登录时整个头部就是登录入口。
 * 登录界面走**独立的 /login 全屏层**（不是在本页内嵌一张表单）：
 * 与购物车的「去登录」、商品详情的「加入购物车」用的是同一个页面、同一套上滑动效，
 * 登录成功后由 redirect 带回本页。
 */
function onHeaderClick() {
  if (isLoggedIn.value) return
  router.push({ name: 'login', query: { redirect: '/member' } })
}

async function onLogout() {
  await showConfirmDialog({ title: '提示', message: '确认退出登录？' })
  await userStore.logout()
  // 不跳转：登录态一变，头部自动回到「点击登录」，页面其余部分保持不变
}
</script>

<template>
  <!-- 根节点恒为同一个元素：App.vue 的 <Transition> 把钩子挂在页面根 vnode 上，
       根元素类型一旦变化会被它当成「换页」并套上当前页面过渡的类名
       （踩过的坑：用 Fragment 根时退出登录会把内容停在 left:100%，整页看起来是空白）。 -->
  <div class="member">
    <!-- 头部：登录与否只换里面的内容，**外面这层结构和下面菜单完全一致** ——
         未登录时「我的」页和已登录看到的是同一套页面，只是头像/昵称那块是登录入口。 -->
    <div
      class="member__header"
      :class="{ 'member__header--tappable': !isLoggedIn }"
      @click="onHeaderClick"
    >
      <div class="member__avatar">
        <van-image v-if="isLoggedIn" round width="60" height="60" :src="avatarUrl" />
        <van-icon v-else name="user-o" size="30" />
      </div>

      <div class="member__info">
        <div class="member__name">{{ displayName }}</div>
        <div class="member__sub">{{ subText }}</div>
      </div>

      <van-icon v-if="!isLoggedIn" name="arrow" class="member__arrow" />
    </div>

    <!-- 菜单与登录态无关，始终展示；未登录时点进去由路由守卫拦到登录页并带 redirect -->
    <van-cell-group inset class="member__cells">
      <van-cell title="我的订单" is-link @click="router.push({ name: 'orders' })" />
      <van-cell title="收货地址" is-link @click="router.push({ name: 'address' })" />
      <van-cell title="我的收藏" is-link @click="router.push({ name: 'favorites' })" />
      <van-cell title="个人信息" is-link @click="router.push({ name: 'profile' })" />
    </van-cell-group>

    <div v-if="isLoggedIn" class="member__logout">
      <van-button round block type="danger" @click="onLogout">退出登录</van-button>
    </div>
  </div>
</template>

<style scoped>
/* 页面根：必须「不透明 + 至少一屏高」。
   tab 互切时旧页是 fixed 留在视口里让位的（见 App.vue 的 .page-slide-tab-*），
   本页原先既没有背景色、高度又只有内容那么高（实测 364px / 视口 844px），
   于是切进「我的」的那 300ms 里，首页/购物的商品会从菜单下面透出来。
   高度按 tabbar 的 50px 占位算（.layout--tabbar 的 padding-bottom，box-sizing: border-box
   下 .layout 的内容盒正好是 100vh - 50px），正好一屏、不产生多余滚动条。 */
.member {
  min-height: calc(100vh - 50px);
  background: var(--van-background, #fff);
}

.member__header {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 24px 20px;
  /* 头部底色：同族蓝色由深到浅的三段渐变（左深右浅），
     左端 rgb(46,107,138) 是比品牌色更深的一档，右端提亮到 rgb(184,212,232)。 */
  background: linear-gradient(
    to right,
    rgb(46, 107, 138) 0%,
    rgb(107, 163, 200) 50%,
    rgb(184, 212, 232) 100%
  );
  color: #fff;
}
.member__header--tappable {
  cursor: pointer;
}

/* 头像位：登录后放真头像，未登录时放一个占位图标。
   两种情况尺寸一致（60×60 圆形），头部高度不会因登录态变化而跳。 */
.member__avatar {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.28);
}

.member__info {
  flex: 1;
  min-width: 0;
}
.member__name {
  font-size: 18px;
  font-weight: 600;
}
.member__sub {
  margin-top: 2px;
  font-size: 13px;
  opacity: 0.9;
}
/* 未登录时的「可点」提示箭头 */
.member__arrow {
  flex-shrink: 0;
  font-size: 16px;
  opacity: 0.9;
}

.member__cells {
  margin-top: 12px;
}
.member__logout {
  margin: 24px 16px;
  /* 「退出登录」按钮底色：与购物车底部「结算」按钮**同色** ——
     就是品牌浅蓝 rgb(107,163,200)，与首页搜索框描边/搜索按钮、本页头部渐变的中点同源。
     按钮仍保持 type="danger"（语义不变，只改配色），所以覆盖的是 Vant danger 的两个变量
     （Vant 4.10 里 .van-button--danger 取 --van-button-danger-background / -border-color，
     默认落到 --van-danger-color = #ee0a24 红）。文字色不用动，danger 默认就是白。
     注意：这不是 tabbar 的选中色 rgb(59,130,246)，两者不是同一个蓝，别混。 */
  --van-button-danger-background: rgb(107, 163, 200);
  --van-button-danger-border-color: rgb(107, 163, 200);
}
/* 文字也要与「结算」按钮一致：那一个（.van-submit-bar__button）带
   `font-weight: var(--van-font-bold)`，量出来是 600；普通 van-button 默认只有 400。
   其余文字属性（字体栈 / 字号 14px / 行高 16.8px / 颜色白 / 对齐居中）两边本来就相同，
   实测逐项比对过，只有这一项有差，所以只补这一条。
   用 Vant 自己的变量而不是写死 600 —— 以后改 --van-font-bold 两处会一起变。 */
.member__logout :deep(.van-button) {
  font-weight: var(--van-font-bold, 600);
}
</style>
