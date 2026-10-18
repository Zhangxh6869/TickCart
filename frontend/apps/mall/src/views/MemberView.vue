<script setup lang="ts">
import { computed, onActivated, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog } from 'vant'
import type { OrderStatusCount } from '@tickcart/shared-types'
import { useUserStore } from '@/stores/user'
import { fetchOrderCount } from '@/api/order'
import OrderStatusIcon from '@/components/OrderStatusIcon.vue'

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

// ---------------- 订单宫格 ----------------

/**
 * 宫格上的四个状态，status 与订单列表页的 tab、后端 OrderStatus 一一对应。
 * 已取消 / 已超时关闭是终态、不需要提醒，不放进来。
 *
 * icon 取设计稿交付的 4 个 SVG（见 OrderStatusIcon），不再用 Vant 图标字体。
 *
 * badge 控制该状态是否显示数量红点：「已完成」是终态、不需要用户再做什么，
 * 红点没有行动指引意义，还会让「我的」页永远挂着一个去不掉的红点，所以关掉。
 * 数量本身仍然从接口拿（不额外判断），只是不渲染。
 */
const ORDER_ENTRIES = [
  { label: '待付款', status: 0, icon: 'pending-payment', badge: true },
  { label: '待发货', status: 1, icon: 'pending-shipment', badge: true },
  { label: '待收货', status: 2, icon: 'pending-receipt', badge: true },
  { label: '已完成', status: 3, icon: 'completed', badge: false },
] as const

/** 状态码 → OrderStatusCount 上的字段名，避免用下标去猜字段顺序 */
const COUNT_KEY: Record<number, keyof OrderStatusCount> = {
  0: 'pendingPayment',
  1: 'pendingShipment',
  2: 'pendingReceipt',
  3: 'completed',
}

const orderCount = ref<OrderStatusCount | null>(null)

/** 某状态的订单数；未登录或还没拉到数据时返回 0（0 不渲染角标） */
function countOf(status: number): number {
  const count = orderCount.value
  const key = COUNT_KEY[status]
  return count && key ? count[key] : 0
}

/** 角标文案：超过两位数统一收敛成 99+，否则角标会把图标顶宽 */
function badgeText(count: number): string {
  return count > 99 ? '99+' : String(count)
}

/**
 * 拉取订单宫格的四态数字。
 *
 * 失败时保持 null，界面上的对应角标就是不显示 ——
 * 少一个红点比整页报错好。
 */
async function loadOverview() {
  if (!isLoggedIn.value) {
    orderCount.value = null
    return
  }

  try {
    orderCount.value = await fetchOrderCount()
  } catch {
    // 错误提示由请求拦截器统一处理，这里只保证角标不显示
  }
}

/**
 * 进入本页时刷新数字。
 *
 * 用 onActivated 而不是 onMounted：本页在 App.vue 的 CACHED_VIEWS 里被 KeepAlive 缓存，
 * onMounted 一辈子只跑一次，那样「下完单回到我的」角标还是旧的。
 * （KeepAlive 内的组件首次挂载时 onActivated 同样会触发，所以首屏也有数据。）
 */
onActivated(loadOverview)

/** 登录态变化：登录成功补拉数字，退出登录立刻清空角标 */
watch(isLoggedIn, loadOverview)

/** 跳订单列表：带状态就直达对应 tab（见 OrderListView 的 initialTab） */
function goOrders(status?: number) {
  router.push({
    name: 'orders',
    query: status === undefined ? {} : { status: String(status) },
  })
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

      <!-- 未登录：整块可点，给一个「能进去」的箭头提示 -->
      <van-icon v-if="!isLoggedIn" name="arrow" class="member__arrow" />
      <!-- 已登录：右上角进个人信息。stop 是为了不触发头部那层的 onHeaderClick -->
      <van-icon
        v-else
        name="setting-o"
        class="member__setting"
        @click.stop="router.push({ name: 'profile' })"
      />
    </div>

    <!-- 订单中心卡：负 margin 上移叠压头部，把「我的订单」提到首屏
         （这是「我的」页访问量最大的一块，不该再点一层才看得到） -->
    <div class="member__body">
      <section class="member__card">
        <div class="member__card-head">
          <span class="member__card-title">我的订单</span>
          <button class="member__more" type="button" @click="goOrders()">
            全部订单<van-icon name="arrow" />
          </button>
        </div>

        <div class="member__grid">
          <button
            v-for="entry in ORDER_ENTRIES"
            :key="entry.label"
            class="member__entry"
            type="button"
            @click="goOrders(entry.status)"
          >
            <span class="member__entry-icon">
              <OrderStatusIcon :name="entry.icon" />
              <span v-if="entry.badge && countOf(entry.status) > 0" class="member__badge">
                {{ badgeText(countOf(entry.status)) }}
              </span>
            </span>
            <span class="member__entry-label">{{ entry.label }}</span>
          </button>
        </div>
      </section>

      <!-- 菜单与登录态无关，始终展示；未登录时点进去由路由守卫拦到登录页并带 redirect。 -->
      <van-cell-group inset class="member__cells">
        <van-cell title="我的收藏" is-link @click="router.push({ name: 'favorites' })" />
        <van-cell title="浏览记录" is-link @click="router.push({ name: 'view-history' })" />
        <van-cell title="收货地址" is-link @click="router.push({ name: 'address' })" />
        <van-cell title="个人信息" is-link @click="router.push({ name: 'profile' })" />
      </van-cell-group>

      <!-- 退出登录：低频且不可逆，但保留整行按钮形态（不做成文字行，
           否则在浅灰底上会看起来像一行说明文字，不像可点的操作）。
           配色与购物车空态「去逛逛」(.cart__empty-btn--brand) 保持一致。
           类挂在 <van-button> 本身（而不是外层 div）—— 这样 .member__logout
           始终是「那个按钮」，验证脚本点它就能触发 onLogout。 -->
      <van-button
        v-if="isLoggedIn"
        round
        block
        type="primary"
        class="member__logout"
        @click="onLogout"
      >
        退出登录
      </van-button>
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
  /* 浅灰底衬托白卡（与「个人信息」页同色），卡片叠压头部才看得出层次 */
  background: #f7f8fa;
}

.member__header {
  display: flex;
  align-items: center;
  gap: 14px;
  /* 底部留 44px：下面的订单卡要上移 26px 叠压进来，
     不留出这段空间的话卡片顶部会压到头像上（头像底 84px，卡片顶 102px）。 */
  padding: 24px 20px 44px;
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
/* 已登录时的设置入口，与箭头同位同尺寸，登录态切换不会引起头部跳动 */
.member__setting {
  flex-shrink: 0;
  font-size: 20px;
  opacity: 0.95;
  cursor: pointer;
}

.member__body {
  position: relative;
  margin-top: -26px;
}

.member__card {
  margin: 0 16px;
  padding: 13px 8px 10px;
  background: var(--van-background-2, #fff);
  border-radius: 12px;
  /* 极轻的投影只为把卡片从渐变头上「托起来」，重了会显脏 */
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.member__card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 6px 10px;
  border-bottom: 0.5px solid var(--van-border-color, #ebedf0);
}
.member__card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--van-text-color, #323233);
}
/* 「全部订单」是次要入口，用 Vant 的次级文字色，不与左侧标题抢层级 */
.member__more {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 0;
  font: inherit;
  font-size: 12px;
  color: var(--van-text-color-2, #969799);
  background: none;
  border: none;
  cursor: pointer;
}

.member__grid {
  display: flex;
  padding-top: 12px;
}
.member__entry {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 0;
  font: inherit;
  background: none;
  border: none;
  cursor: pointer;
}
/* 图标相对定位，给角标当参照物。
   颜色取设计稿 SVG 自带的 #2c2c2c（图标本身是 currentColor 填充，见 OrderStatusIcon）——
   深灰在白卡上对比度约 13:1，比原先的品牌浅蓝（约 2.6:1）清楚得多。
   想换回品牌色只改这一行。 */
.member__entry-icon {
  position: relative;
  display: flex;
  color: #2c2c2c;
}
.member__entry-label {
  font-size: 12px;
  /* 黑色（与图标同为 #2c2c2c）：这四个是入口而不是说明文字，
     用次要灰会显得像禁用态、可点性弱。 */
  color: #2c2c2c;
}

/* 角标：贴图标右上角。红点是电商通用语言，用 Vant 的 danger 色，
   注意它和品牌浅蓝不是一套色，别改成蓝色。 */
.member__badge {
  position: absolute;
  top: -6px;
  right: -12px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  font-size: 11px;
  /* 数字加粗：11px 的小字在白底红点上本来就偏细，加粗后更好认。
     角标宽度是自适应的（min-width 16px + right 定位），加粗变宽不会挤到图标。 */
  font-weight: 600;
  line-height: 16px;
  color: #fff;
  text-align: center;
  background: var(--van-danger-color, #ee0a24);
  border-radius: 8px;
}

.member__cells {
  margin-top: 12px;
}

/* 退出登录：通栏按钮，左右各留 16px。
   与购物车空态「去逛逛」(.cart__empty-btn--brand) 同款配色：
   品牌浅蓝 rgb(107,163,200) 底 + Vant 默认白字 + font-weight 600。
   本类直接写在 <van-button> 标签上（= 子组件根节点本身），普通选择器即可命中；
   若挂在父级包裹 div 上则必须用 :deep(.van-button)。
   覆盖的是 Vant primary 按钮自己的两个变量（默认取 --van-primary-color = #1989fa），
   字重也用 Vant 变量而非写死 600，以后改 --van-font-bold 两处会一起变。
   宽度必须显式算：van-button--block 自带 width:100%，margin 不会把 100% 挤小，
   不写 calc 会左右各溢出 16px。 */
.member__logout {
  width: calc(100% - 32px);
  margin: 20px 16px 0;
  --van-button-primary-background: rgb(107, 163, 200);
  --van-button-primary-border-color: rgb(107, 163, 200);
  font-weight: var(--van-font-bold, 600);
}
</style>
