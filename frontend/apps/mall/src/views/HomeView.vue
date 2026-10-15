<script setup lang="ts">
import { computed, nextTick, onActivated, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import type { Category, ProductListItem } from '@tickcart/shared-types'
import { fetchCategories, fetchProducts } from '@/api/product'
import BackToTop from '@/components/BackToTop.vue'
import ProductCard from '@/components/ProductCard.vue'

// 显式声明组件名：App.vue 里用 <KeepAlive include="HomeView"> 缓存首页
defineOptions({ name: 'HomeView' })

const router = useRouter()
const categories = ref<Category[]>([])
const products = ref<ProductListItem[]>([])
/** 分类栏元素，用于把选中的分类滚到屏幕中间 */
const catBarRef = ref<HTMLElement | null>(null)
/** 当前选中的分类；null 表示「全部」（首页默认，展示热销榜） */
const activeCategoryId = ref<number | null>(null)

/** 首页每屏加载的商品数 */
const PAGE_SIZE = 10

/**
 * **翻页**加载动画的最短展示时长。
 * 本机接口 ~10ms 就返回，动画会一闪而过、看起来像「商品凭空冒出来」，
 * 所以给翻页的圆环兜一个下限：无论接口多快，都先让圆环完整转完**一圈**再追加商品。
 * 圆环每 0.8s 转一圈（Vant 的 --van-loading-spinner-duration），1 圈 = 800ms。
 *
 * **只作用于触底翻页（第二页起）**。一批商品的第一页（首次进入首页、切换分类）
 * 不播加载动画、也不吃这个下限 —— 首屏要直接看到商品，见 loadingFirstPage。
 * 改这个值要同步改 .workbuddy-ai/tmp/cdp/home-loading-verify.mjs 里的同名常量。
 */
const MIN_LOADING_MS = 800

/**
 * 下拉刷新的最短展示时长（ms）。与翻页的 MIN_LOADING_MS 同一思路：
 * 本机接口 ~10ms 就返回，不兜下限的话头部指示会「闪一下就没了」，像没刷新。
 */
const MIN_REFRESH_MS = 500

/** van-pull-refresh 的刷新态（由组件 v-model 驱动） */
const refreshing = ref(false)

/** van-list 的加载态（由组件 v-model 驱动） */
const listLoading = ref(false)

/**
 * 正在加载「一批商品的第一页」—— 首次进入首页、以及每次切换分类后的第一屏。
 *
 * 这种加载**不播加载动画**：打开首页应当直接看到商品，而不是先看 800ms 圆环；
 * 切换分类同理，用户刚点了分类就想立刻看到新商品。
 * 只有触底翻页（第二页起）才播动画 —— 那时列表里已经有商品，动画是「后面还有」的提示。
 *
 * 由 loadMore 按 pageNo 判定，同时驱动 van-list 的 #loading 插槽是否渲染圆环。
 */
const loadingFirstPage = ref(false)
/** 已无更多数据 */
const listFinished = ref(false)
/** 本次加载失败，等待用户点击重试 */
const listError = ref(false)
/** 下一页页码 */
const pageNo = ref(1)

/**
 * 随机种子：**同一次浏览内保持不变**，因此后端返回的随机顺序稳定，翻页不会重复或漏项。
 * 进入首页、以及每次切换分类时重新生成，得到新的一批随机商品。
 * 在 setup 阶段就初始化（不能放到 onMounted）—— van-list 挂载时即会触发首次加载，
 * 那时种子必须已经就绪。
 */
const seed = ref(newSeed())

function newSeed(): number {
  return Math.floor(Math.random() * 1_000_000_000) + 1
}

/** 分类栏数据：首项固定为「全部」（渲染时钉在分类栏最左侧，不随其余分类横向滑动） */
const categoryTabs = computed<{ id: number | null; name: string }[]>(() => [
  { id: null, name: '全部' },
  ...categories.value.map((cat) => ({ id: cat.id as number | null, name: cat.name })),
])

/** 分类在分类栏里的下标（「全部」为 0），用于判定切换方向 */
function catIndexOf(id: number | null): number {
  return categoryTabs.value.findIndex((tab) => tab.id === id)
}

/** 商品面板的 key：一个分类一块面板，key 变化即触发左右滑动过渡 */
const panelKey = computed(() => activeCategoryId.value ?? 'all')

/**
 * 商品区切换方向：`cat-slide-left` = 往右切（新面板从右侧划入），
 * `cat-slide-right` = 往左切（新面板从左侧划入）。
 * 命名与 App.vue 里 tab 页切换的 `page-slide-tab-*` 保持同一套语义。
 */
const catTransition = ref<'cat-slide-left' | 'cat-slide-right'>('cat-slide-left')

/** 商品区元素，切换时用来量取并锁定高度 */
const sectionRef = ref<HTMLElement | null>(null)

/**
 * 切换分类期间锁定的商品区最小高度（px，0 = 不锁定）。
 *
 * 新面板的第一页数据回来之前它是空的，`.section` 会塌到只剩内边距 ——
 * 分类栏是吸顶的，用户常常已经滚到下方才点分类，此时文档高度骤降会把 scrollY 夹到 0，
 * 页面「唰」地弹回顶部。锁定切换前的高度即可避免这次无谓的跳动。
 */
const sectionMinHeight = ref(0)

// 请求序号：连点分类时丢弃过期响应，避免先发的慢请求覆盖后发的快请求
let requestSeq = 0
// 居中滚动序号：连点分类时丢弃过期的滚动，避免两次滚动互相打架
let centerSeq = 0

/** 把当前选中的分类横向滚到分类栏可视区的正中间。 */
function scrollActiveToCenter(token: number) {
  const bar = catBarRef.value
  if (!bar || token !== centerSeq) return
  // 「全部」钉在最左侧、无法居中，此时直接回到起点
  if (activeCategoryId.value === null) {
    bar.scrollTo({ left: 0, behavior: 'smooth' })
    return
  }
  const item = bar.querySelector<HTMLElement>('.cat-bar__item.is-active')
  if (!item) return
  const barRect = bar.getBoundingClientRect()
  const itemRect = item.getBoundingClientRect()
  // 选中项中心当前相对可视区左缘的偏移，换算成让它落在可视区中点所需的 scrollLeft
  const centerOffset = itemRect.left + itemRect.width / 2 - barRect.left
  const left = Math.max(0, bar.scrollLeft + centerOffset - bar.clientWidth / 2)
  bar.scrollTo({ left, behavior: 'smooth' })
}

/**
 * 同步「选中项是否已被横向滚到钉住项（「全部」）下面」。
 *
 * 选中项的放大是 transform: scale，绕名称盒中心缩放会让名称左右各向外溢出约 8px。
 * 因此选中项默认抬到钉住项之上（见样式里 .cat-bar__item.is-active 的 z-index 注释），
 * 否则紧挨「全部」的分类（如「手机数码」）放大后左半边会被「全部」的不透明背景切掉。
 * 但同一个抬升会让「手动横滑时滑到『全部』下面的选中项」浮在「全部」之上、露出半截字，
 * 所以这里按布局位置判断：选中项的可视左边缘一旦越过钉住项右边缘，就给它降层，
 * 恢复「被『全部』盖住」的常规行为。
 * 判据用 offsetLeft / offsetWidth（布局值，不含 transform），不会被放大产生的溢出干扰。
 */
function syncActiveUnderPinned() {
  const bar = catBarRef.value
  if (!bar) return
  const active = bar.querySelector<HTMLElement>('.cat-bar__item.is-active')
  const pinned = bar.querySelector<HTMLElement>('.cat-bar__item--pinned')
  // 「全部」自身被选中时不需要降层（钉住项本来就是被遮的基准）
  const under =
    !!active &&
    !!pinned &&
    active !== pinned &&
    active.offsetLeft - pinned.offsetLeft - bar.scrollLeft < pinned.offsetWidth
  bar.classList.toggle('is-active-under-pinned', under)
}

/** 选中项字号有 0.15s 过渡，宽度在过渡中还会变，所以等过渡结束再校正一次。 */
async function centerActiveCategory() {
  const token = ++centerSeq
  await nextTick()
  syncActiveUnderPinned()
  scrollActiveToCenter(token)
  window.setTimeout(() => {
    scrollActiveToCenter(token)
    syncActiveUnderPinned()
  }, 200)
}

/**
 * 加载下一页商品（van-list 触底时回调，也用于重置后立即拉第一页）。
 *
 * 注意不能写成「不满足条件就提前 return」的形式：van-list 会先把 loading 置 true
 * 再 emit load，一旦中途返回且没人复位 loading，列表就再也加载不出东西了。
 * 这里所有分支都保证走到 finally 复位。
 */
async function loadMore() {
  const seq = ++requestSeq
  // 第一页（首次进入 / 切换分类后的第一屏）不播动画；第二页起才播。
  // 这里读 pageNo 必须在发请求之前 —— 请求成功后 pageNo 会自增。
  const isFirstPage = pageNo.value === 1
  loadingFirstPage.value = isFirstPage
  listError.value = false
  const startedAt = Date.now()
  try {
    const page = await fetchProducts({
      page: pageNo.value,
      size: PAGE_SIZE,
      categoryId: activeCategoryId.value ?? undefined,
      seed: seed.value,
    })
    // 期间切换了分类：丢弃过期响应，交给最新一次请求去更新状态
    if (seq !== requestSeq) return
    // 只有翻页才补足动画的最短展示时长，再往列表里追加商品 ——
    // 这样「看到加载动画 → 商品出现」的顺序才成立，接口再快也不会变成凭空冒出来。
    // 第一页直接展示，不等。
    if (!isFirstPage) await waitMinLoading(startedAt)
    if (seq !== requestSeq) return
    products.value.push(...page.records)
    pageNo.value += 1
    // 取满总数、或本页已经返回空，都视为没有更多，避免空转
    listFinished.value = products.value.length >= page.total || page.records.length === 0
  } catch {
    if (seq !== requestSeq) return
    listError.value = true
  } finally {
    if (seq === requestSeq) {
      listLoading.value = false
      loadingFirstPage.value = false
      // 第一页（新分类的首屏）已经渲染进 DOM，或本次加载失败 —— 两种情况都该
      // 解除切换时锁定的最小高度，把版面交还给真实内容高度。
      // 再查一次 seq：等待期间用户又切了分类的话，锁要留给最新那次请求去解。
      if (isFirstPage) {
        await nextTick()
        if (seq === requestSeq) sectionMinHeight.value = 0
      }
    }
  }
}

/** 翻页加载若快于 MIN_LOADING_MS，补齐差额，保证圆环至少完整展示一次。 */
function waitMinLoading(startedAt: number): Promise<void> {
  return waitMin(startedAt, MIN_LOADING_MS)
}

/** 本次加载若快于 minMs，补齐差额，让动画/指示至少完整展示一次。 */
function waitMin(startedAt: number, minMs: number): Promise<void> {
  const rest = minMs - (Date.now() - startedAt)
  return rest > 0 ? new Promise((resolve) => setTimeout(resolve, rest)) : Promise.resolve()
}

/**
 * 重新随机并回到第一页（首次进入首页、切换分类时调用）。
 *
 * 先手动把 loading 置 true，是为了压住 van-list 因列表被清空而自动触发的那次 load，
 * 否则会和我们这里发起的请求撞在一起（虽然请求序号能兜住，但白白多打一次接口）。
 *
 * 这里发出的第一页请求**不播加载动画**（loadMore 按 pageNo === 1 判定，见 loadingFirstPage）。
 */
function resetList() {
  requestSeq += 1 // 作废所有在途请求
  products.value = []
  pageNo.value = 1
  listFinished.value = false
  listError.value = false
  seed.value = newSeed()
  listLoading.value = true
  loadMore()
}

/**
 * 下拉刷新：换一个新的随机种子重拉商品第一页，顺带刷新分类栏。
 *
 * 与切分类（resetList）的差别是**原子替换**：不先清空 products，等新数据回来再整体换掉。
 * 清空再 push 的话，刷新期间商品区会整块消失、文档高度跟着塌掉（`.section` 没有内容撑着），
 * 观感就是「一拉商品全没了，然后才冒出来」。
 *
 * 不动 activeCategoryId —— 刷新是「重拉当前这一屏」，不是换分类；
 * 改了它会连带换掉 panelKey，触发一次本不该有的左右滑动过渡。
 */
async function onRefresh() {
  // 刷新优先于在途的翻页 / 切分类请求：自增序号即作废它们（被作废的请求不再更新任何状态）
  const seq = ++requestSeq
  const startedAt = Date.now()
  const nextSeed = newSeed()
  listError.value = false
  try {
    const [cats, page] = await Promise.all([
      // 分类拉不到不该让整次刷新失败：失败就沿用现有分类栏
      fetchCategories().catch(() => null),
      fetchProducts({
        page: 1,
        size: PAGE_SIZE,
        categoryId: activeCategoryId.value ?? undefined,
        seed: nextSeed,
      }),
    ])
    if (seq !== requestSeq) return
    await waitMin(startedAt, MIN_REFRESH_MS)
    if (seq !== requestSeq) return
    if (cats) categories.value = cats
    seed.value = nextSeed
    products.value = page.records
    pageNo.value = 2
    listFinished.value = page.records.length >= page.total || page.records.length === 0
  } catch {
    if (seq !== requestSeq) return
    // 失败也要兜满最短时长，否则提示一闪而过；原列表原样留着，不清空
    await waitMin(startedAt, MIN_REFRESH_MS)
    if (seq === requestSeq) showToast('刷新失败')
  } finally {
    if (seq === requestSeq) {
      refreshing.value = false
      // ★ 下面三句不能省：上面自增 requestSeq 之后，被作废的那个请求不会走到 loadMore
      //   的 finally 分支（它判 seq 相等才复位），这些状态就会永远停在「加载中」：
      //   listLoading 卡 true → van-list 再也不加载下一页；loadingFirstPage 卡 true →
      //   翻页圆环永远不渲染；sectionMinHeight 卡住 → 切分类时锁定的高度再也解不开。
      listLoading.value = false
      loadingFirstPage.value = false
      sectionMinHeight.value = 0
    }
  }
}

/** 切换分类：商品区按分类的左右顺序左右滑动、原地刷新商品，并把选中的分类滚到屏幕中间。 */
function selectCategory(id: number | null) {
  if (activeCategoryId.value !== id) {
    // 方向必须先定再改 activeCategoryId —— 改完就分不清「从哪个分类切过来」了。
    // 新分类排在当前分类右边 → 新面板从右侧划入（点左边的分类则反之）。
    catTransition.value =
      catIndexOf(id) > catIndexOf(activeCategoryId.value) ? 'cat-slide-left' : 'cat-slide-right'
    // 量高度也必须在清空列表之前：之后 .section 就只剩内边距了
    sectionMinHeight.value = sectionRef.value?.offsetHeight ?? 0
    activeCategoryId.value = id
    resetList()
    // 换了分类就是换了一批商品，视口必须回到顶部。
    // 否则用户停在上一批商品的滚动位置上，而新列表（只有一页）比旧列表短，
    // scrollY 会被夹到「刚好触底」，van-list 顺势再加载一页 ——
    // 实测就是「切一次分类变成 20 个商品」。
    // 用瞬时滚动：分类栏是吸顶的，回到顶部时它在视觉上原地不动，不会和商品区的滑动打架。
    if (window.scrollY > 0) window.scrollTo(0, 0)
  }
  centerActiveCategory()
}

/**
 * 点搜索框进独立搜索页。
 *
 * ★ 这里**不再**跳 `/products`。首页的搜索框是 `readonly` 的装饰态（真输入在搜索页），
 * 原先跳列表页既带不上关键词、也没有历史/热搜，用户以为能搜却搜不了。
 * 现在统一进 `/search`（空态给历史 + 热搜，提交后同页出结果）。
 */
function goSearch() {
  router.push({ name: 'search' })
}

onMounted(async () => {
  // 横滑过程中随时校正选中项的层叠状态（passive：监听不参与滚动性能路径）
  catBarRef.value?.addEventListener('scroll', syncActiveUnderPinned, { passive: true })
  try {
    categories.value = await fetchCategories()
  } catch {
    showToast('分类加载失败')
  }
  // 首屏商品不在这里拉：van-list 挂载时会做一次「内容是否撑满滚动容器」的检查，
  // 此刻列表为空必然不撑满，于是自动 emit 一次 load，走的正是 loadMore()。
  // 这里若再手动拉一次只会白打一次接口（随机种子在 setup 阶段就已就绪，首屏随机性不受影响）。
})

// 从商品详情等页面返回时，浏览器会恢复分类栏的横向滚动位置但不触发 scroll 事件，这里补一次
onActivated(syncActiveUnderPinned)

onBeforeUnmount(() => {
  catBarRef.value?.removeEventListener('scroll', syncActiveUnderPinned)
})
</script>

<template>
  <div class="home">
    <!-- 吸顶区：搜索栏 + 分类栏作为整体钉在页面顶部，纵向滚动时保持不动。
         ★ 必须留在 van-pull-refresh **之外**：下拉刷新的指示条是 Vant 渲染在 track 上方的，
           包住吸顶区的话指示条会跑到搜索栏上面；只包商品区，它才正好落在分类条下方。 -->
    <div class="home__sticky">
      <van-search
        placeholder="搜索商品"
        readonly
        show-action
        @click="goSearch"
      >
        <template #left-icon>
          <svg
            class="search-icon"
            viewBox="0 0 1024 1024"
            xmlns="http://www.w3.org/2000/svg"
            aria-hidden="true"
          >
            <path
              d="M932.571429 493.714286C932.571429 261.485714 744.228571 73.142857 512 73.142857S91.428571 261.485714 91.428571 493.714286s188.342857 420.571429 420.571429 420.571428c89.6 0 171.885714-27.428571 239.542857-74.971428l111.542857 111.542857 51.2-51.2-106.057143-106.057143c76.8-76.8 124.342857-182.857143 124.342858-299.885714zM164.571429 493.714286C164.571429 301.714286 320 146.285714 512 146.285714s347.428571 155.428571 347.428571 347.428572-155.428571 347.428571-347.428571 347.428571S164.571429 685.714286 164.571429 493.714286z"
              fill="currentColor"
            />
          </svg>
        </template>
        <template #action>
          <button class="search-btn" type="button" @click.stop="goSearch">搜索</button>
        </template>
      </van-search>

      <!-- 分类栏：搜索栏下方一行；「全部」钉在左侧不动，其余分类横向滑动，点击后原地切换下方商品 -->
      <div ref="catBarRef" class="cat-bar">
        <div
          v-for="tab in categoryTabs"
          :key="tab.id ?? 'all'"
          class="cat-bar__item"
          :class="{
            'is-active': activeCategoryId === tab.id,
            'cat-bar__item--pinned': tab.id === null,
          }"
          @click="selectCategory(tab.id)"
        >
          <span class="cat-bar__name">{{ tab.name }}</span>
          <span class="cat-bar__bar" />
        </div>
      </div>
    </div>

    <!-- 下拉刷新：滚到顶部（scrollY = 0）后继续下拉即刷新商品。
         ★ 只包商品区（.section）—— 指示条才会落在分类条下方（见上面吸顶区那条注释）。
         ★ 也不含 BackToTop：刷新时 track 会被 translate3d 下移，transform 祖先会把内部
           position: fixed 后代的包含块从视口换成它自己，按钮会被一起拖下去。
         ★ 包裹层要撑满「吸顶区以下」的剩余视口（见样式里 .van-pull-refresh 的 flex 说明），
           否则列表短、或落到「暂无商品」空态时，下方一大片区域接不到手势。
         头部文案用 Vant 的默认中文；成功态不加提示（用户先不做），刷新完成头部直接收起。 -->
    <van-pull-refresh
      v-model="refreshing"
      pulling-text="下拉可以刷新"
      loosing-text="松开立即刷新"
      loading-text="正在刷新..."
      @refresh="onRefresh"
    >
      <div
        ref="sectionRef"
        class="section"
        :style="{ minHeight: sectionMinHeight ? `${sectionMinHeight}px` : '' }"
      >
        <!-- 分类切换：整块商品区左右滑动。用 key 换面板，旧面板绝对定位滑出、
             新面板留在文档流里滑入 —— 新面板留在流里，.section 的高度才始终有人撑着，
             滚动位置不会因为文档骤然变矮被夹掉。方向由 selectCategory 里的下标比较决定。
             面板包一层 div 是必须的：<Transition> 要求子节点单一，而 van-list 的
             finished / error 文案和 #loading 插槽都挂在它内部。 -->
        <Transition :name="catTransition">
          <div :key="panelKey" class="cat-panel">
            <!-- 商品列表：触底自动加载下一页交给 van-list 托管。
                 它的挂载检查（immediate-check 默认开启）会在列表为空时自动 emit 一次 load，
                 所以首屏 10 个商品不用在 onMounted 里手动拉 —— 见 script 里的说明。
                 finished / error 的提示文案也由组件负责，我们只在 @load 里取数据。
                 切换分类会重建这个 van-list，但 loading 在 resetList 里已先置 true，
                 重建时的挂载检查会被它挡住，不会白打一次接口。 -->
            <van-list
              v-model:loading="listLoading"
              v-model:error="listError"
              :finished="listFinished"
              finished-text="没有更多了"
              error-text="加载失败，点击重试"
              @load="loadMore"
            >
              <div class="product-grid">
                <ProductCard v-for="p in products" :key="p.id" :product="p" />
              </div>
              <!-- 空态只在「确实没有数据」时出现：加载中列表本来就空，不能误判成暂无商品 -->
              <van-empty
                v-if="listFinished && products.length === 0"
                description="暂无商品"
              />
              <!-- 触底加载动画：列表末尾留白里居中转的一个空心圆环。
                   放在文档流内而不是做浮层 —— 浮层会盖住商品、也和 tabbar 抢位置；
                   放这里才符合「拉到最下边，在留白区域转圈」的预期。
                   第一页（首次进入 / 切换分类）不渲染它：那时列表里还没有商品，
                   要的是「直接展示」，不是先看一圈动画。 -->
              <template #loading>
                <div v-if="!loadingFirstPage" class="list-loading">
                  <van-loading class="list-loading__spinner" size="20" />
                </div>
              </template>
            </van-list>
          </div>
        </Transition>
      </div>
    </van-pull-refresh>

    <!-- 回到顶部：滚过一屏后出现在右下角。
         ★ 必须是 .home 的直接子元素：① 放进 .section 会被它的 overflow: clip 裁掉；
         ② 放进上面的 <van-pull-refresh> 会被下拉时 track 的 transform 拖走 ——
         transform 祖先会把 fixed 后代的包含块从视口换成它自己，按钮会跟着内容一起下移。
         ★ bottom 默认 62px = 50px tabbar + 12px，悬在 tabbar 上方，不与底部导航抢点击。 -->
    <BackToTop />
  </div>
</template>

<style scoped>
.home {
  /* 品牌色，用于搜索框描边、搜索按钮底色。
     分类栏选中小横杆**不再**用它（见 .cat-bar__bar），改与底部 tabbar 的选中指示条同源 */
  --color-brand: rgb(107, 163, 200);
  /* 搜索图标与「搜索商品」占位文字共用的颜色 */
  --color-search-icon: #515151;

  /* ── 根节点必须「不透明 + 至少一屏高」（★ 别删，与 .cart / .member 同一条约定）──
     tab 互切时**旧页是 fixed 留在视口里让位**的（见 App.vue 的 .page-slide-tab-*），
     所以进入页必须自己把视口盖住，否则旧页会从它下面透出来。

     首页原先漏了这两条：根节点既没有背景色，高度也随内容走。它只靠 .home__sticky 与
     .section 各自涂白，而这两块之间隔着 12px（.section 的 margin-top），
     .section 左右/下方也各留 12px 外边距 —— 这些「没被涂到」的缝里，旧页就露出来了。
     实测（把 tab 过渡放慢到 4s 后截图）：由购物车 / 我的切回首页时，搜索栏与分类栏下方
     那条 12px 横带里能看到购物车的内容（空态的蓝色按钮、浅灰底），就是这里漏出来的。

     高度按 tabbar 占位算：.layout 是 min-height: 100vh + padding-bottom: 50px，
     box-sizing: border-box 下内容盒正好 100vh - 50px，剩下的 50px 由不透明的 tabbar 补。
     （写成 min-height: 100vh 会凭空多出 50px 滚动条，别照抄 .cart 的历史写法。） */
  min-height: calc(100vh - 50px);
  background: var(--van-background, #fff);

  /* ── 纵向 flex 只为一件事：让下拉刷新的包裹层撑满「吸顶区以下」的剩余视口 ──
     包裹层只包商品区（这样指示条才落在分类条下方），而它的高度若随内容走，
     列表短、或落到「暂无商品」空态时，下方一大片区域就接不到下拉手势。
     ★ 包裹层用 flex: 1 0 auto，不是 flex: 1（= 1 1 0%）：grow 负责填满剩余空间，
       basis 取 auto、且不许收缩，内容比视口高时高度仍由内容撑开。
       写成 flex: 1 会把它压进剩余空间里，而包裹层带 overflow: clip，商品会被裁掉。 */
  display: flex;
  flex-direction: column;
}
/* ── 下拉刷新的包裹层：必须把 Vant 自带的 overflow: hidden 改成 clip（★ 别删）──────
   `.van-pull-refresh { overflow: hidden }` 是 Vant 自己的样式，本意是裁掉静止时藏在
   track 上方 -50px 处的头部指示。但 `hidden` 会**创建滚动容器**，而 sticky 元素的定位基准
   是「最近的滚动容器」——于是 `.home__sticky` 的最近 scrollport 从视口变成了这个包裹层
   （它自己不滚动），吸顶直接失效，整条搜索栏 + 分类栏会跟着页面滚走。
   （同一坑在 TabBarLayout 里踩过：`overflow-x: hidden` 会把 `overflow-y` 计算成 auto，
   一样创建滚动容器；那边用的是 clip。）

   `clip` 的裁剪效果与 `hidden` 完全一致（头部照样藏住），但不创建滚动容器 ——
   sticky 仍以视口为基准，Vant 的 getScrollParent（正则只认 scroll|auto|overlay）
   也照样把 window 认成滚动容器，下拉刷新的「滚到顶部」判定与 van-list 的分页都不受影响。
   实测：覆盖后滚到中部，`.home__sticky` 的 rect.top 仍是 0（见 cdp/home-pull-refresh-verify.mjs）。 */
.home :deep(.van-pull-refresh) {
  /* 撑满吸顶区以下的剩余视口，让手势区始终覆盖整屏（详见 .home 里那段 flex 注释）。
     flex: 1 0 auto 而不是 flex: 1 —— 后者会把商品区压进剩余空间、被下面的 clip 裁掉。 */
  flex: 1 0 auto;
  overflow: clip;
  /* ★ 包裹层自己也要是纵向 flex：让 track 撑满它。
     track 自带 `height: 100%`，而包裹层的高度是**flex grow 出来的**（不是确定高度），
     百分比高度解析不出来会退化成内容高度 —— 于是「列表很短」时 track 只有那么点高，
     下方整片区域都接不到手势（空态同理）。改成 flex grow 就与百分比解析无关了。 */
  display: flex;
  flex-direction: column;
}
.home :deep(.van-pull-refresh__track) {
  flex: 1 0 auto;
}
/* 吸顶区：搜索栏 + 分类栏作为整体钉在页面顶部，纵向滚动时保持不动。
   背景必须是不透明实底，否则下方商品会从吸顶区后面透出来；
   z-index 让吸顶区盖在 .section 之上（sticky 已是定位元素，写死 z-index 更稳妥）。 */
.home__sticky {
  position: sticky;
  top: 0;
  z-index: 2;
  /* 作为 .home 的 flex 项：高度按内容走、不参与拉伸（拉伸会改变吸顶区的实际高度） */
  flex: 0 0 auto;
  /* 吸顶后本区即页面最顶部，原本 .home 上的 12px 顶部留白挪到这里：
     静止时版面与改动前逐像素一致（搜索栏 bottom 54、分类栏 54~98、商品区 top 110）。
     仍用 padding 而非给搜索框加 margin-top——margin 会塌陷穿透出去凭空多出 12px 高度 */
  padding-top: 12px;
  background: var(--van-background);
  /* 底部分隔：1px 发丝线给出明确的分界，下面再叠一层 8px 柔影避免像硬边框。
     阴影画在盒子外侧（向下 1~10px），落在 .section 的 12px 上外边距和卡片顶部；
     吸顶区 z-index 更高，阴影不会被 .section 盖住。
     强度实测（390px 视口，量边界下方像素灰度，纯白 = 255）：
       单层 0 1px 4px rgba(0,0,0,.08) → 最深 244（仅差 11，几乎看不出，故弃用）
       本组合 → 最深 221（差 34），再经约 10px 渐变回纯白 */
  box-shadow:
    0 1px 0 rgba(0, 0, 0, 0.08),
    0 2px 8px rgba(0, 0, 0, 0.1);
}
/* 搜索区：整个 van-search 作为圆角长方形容器（品牌绿描边），按钮嵌在容器内部 */
.home :deep(.van-search) {
  /* 图标与占位文字同色：直接复用 Vant 自己的两个颜色变量 */
  --van-search-left-icon-color: var(--color-search-icon);
  --van-field-placeholder-text-color: var(--color-search-icon);
  margin: 0 12px;
  padding: 2px; /* 内部元素与边框保持 2px 间距 */
  border: 2px solid var(--color-brand);
  border-radius: 6px;
  background: rgb(232, 240, 248); /* #E8F0F8，搜索框底色 */
}
/* 自定义搜索图标：尺寸对齐 Vant 默认图标（--van-field-icon-size = 18px），颜色走 currentColor */
.search-icon {
  display: block;
  width: var(--van-field-icon-size);
  height: var(--van-field-icon-size);
}
/* 输入区不再单独描边/填色，融入外层容器 */
.home :deep(.van-search__content) {
  border: none;
  background: transparent;
  /* 图标距搜索框左侧 10px = 外层 .van-search 的 2px + 这里的 8px */
  padding-left: 8px;
}
/* 图标与右侧「搜索商品」文字的间距同为 10px（Vant 默认是 --van-padding-base = 4px） */
.home :deep(.van-field__left-icon) {
  margin-right: 10px;
}
/* 去掉 Vant 默认的 0 8px 内边距，与边框的间距统一由外层 padding 控制 */
.home :deep(.van-search__action) {
  padding: 0;
}
.search-btn {
  height: var(--van-search-input-height); /* 34px，与输入区等高 */
  padding: 0 14px;
  border: none;
  border-radius: 4px; /* 容器圆角 6px - 内缩 2px */
  background: var(--color-brand);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}
/* 商品区所在的白底卡片。
   position: relative 是旧面板（绝对定位滑出）的包含块；
   overflow: clip 一举两得 —— 收口新面板从 left: ±100% 滑入时的横向溢出
   （不夹住会把文档 scrollWidth 撑到两倍宽、还能横向拖动），
   同时裁掉旧面板滑出期间溢出卡片的那部分（它绝对定位后不再撑高 .section）。
   必须是 clip 不是 hidden：hidden 会创建滚动容器，程序化 scrollTo 仍能滚过去。
   两个方向都用 clip 是安全的：.section 内没有 sticky / fixed 后代，
   首页吸顶区是它的兄弟节点，不在这个裁剪范围内。 */
.section {
  position: relative;
  background: #fff;
  margin: 12px;
  border-radius: 8px;
  overflow: clip;
}

/* 分类面板：内边距放在这里而不是 .section 上。
   旧面板绝对定位时，包含块是 .section 的 padding box；.section 自己没有 padding，
   所以旧面板 left: 0 / width: 100% 的内容位置和新面板逐像素一致，滑动时不会横向错位。

   必须给不透明底色：滑动途中两块面板一上一下叠着，面板本身是透明的，
   卡片之间那 10px 间隙就会透出下层内容 —— 观感就是「上一页的残留」。
   .section 的白底挡不住，因为两块面板都在它内部、盖在白底之上。 */
.cat-panel {
  padding: 12px;
  background: #fff;
}

/* ── 分类切换：商品区左右滑动 ────────────────────────────────────────
   与 App.vue 的 tab 页切换同一套写法：位移一律用 left，绝不用 transform
   （transform 会把页面内 position: fixed 的后代改挂到该元素上，
   购物车的 van-submit-bar 正是这种元素，实测会飞出屏幕）。

   新面板留在文档流里（relative），旧面板绝对定位滑出后由 <Transition> 移除。
   新面板留在流里是有意的：.section 的高度始终由它撑着，滚动位置不会被夹掉。
   横向溢出由 .section 的 overflow: clip 收口，这里不用再管。 */
.cat-slide-left-enter-active,
.cat-slide-right-enter-active {
  position: relative;
  left: 0;
  z-index: 1;
  transition: left 260ms cubic-bezier(0.32, 0.72, 0, 1);
}
/* 往右切（点了右边的分类）：新面板从右侧划入 */
.cat-slide-left-enter-from {
  left: 100%;
}
/* 往左切（点了左边的分类）：新面板从左侧划入 */
.cat-slide-right-enter-from {
  left: -100%;
}
.cat-slide-left-leave-active,
.cat-slide-right-leave-active {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  z-index: 0;
  transition: left 260ms cubic-bezier(0.32, 0.72, 0, 1);
}
/* 旧面板整块滑出，位移量与新面板完全相同（都是 100%）。
   两块像同一条内容整体平移，滑动途中视口里是「左旧右新」的无缝拼接，
   而且两块**始终首尾相接、互不重叠**。
   为什么不能像 iOS push 那样只让旧面板移 -30%：两者位移量不同（视差）时，
   新面板移动快会**追上并压在旧面板之上**（实测最大重叠 256px，正好等于两块位移量之差
   366 - 110），而面板本身是透明的（computed background 为 rgba(0,0,0,0)），
   上层卡片之间那 10px 间隙就会透出下层的旧商品 —— 观感正是「上一页的残留」。
   改成同速整块平移后两块不再重叠，配合 .cat-panel 的白底彻底杜绝透出。 */
.cat-slide-left-leave-to {
  left: -100%;
}
.cat-slide-right-leave-to {
  left: 100%;
}

/* 用户开启「减弱动态效果」时，过渡压到接近瞬时，只保留状态切换 */
@media (prefers-reduced-motion: reduce) {
  .cat-slide-left-enter-active,
  .cat-slide-right-enter-active,
  .cat-slide-left-leave-active,
  .cat-slide-right-leave-active {
    transition-duration: 1ms;
  }
}
/* 分类栏：紧贴搜索栏下方，单行横向排列，超出部分横向滚动（隐藏滚动条） */
.cat-bar {
  display: flex;
  /* 项间距用 .cat-bar__item 的 margin-right 实现，不用 gap：
     钉住的「全部」需要把右侧那段间距一起画进自己的背景里（见下） */
  /* 按首行基线对齐：选中项放大后，其余分类名仍与其处在同一水平线上 */
  align-items: baseline;
  /* 高度写死 44px（16px 上内边距 + 28px 内容区）。
     内容区 28px = 名称行高 22px + 间距 3px + 小横杆 3px，本身已恒定：
     选中态放大走 transform（不占布局），名称盒高不随切换变化。
     这里再写死一次是为了兜底——将来若误把放大改回 font-size，
     行高（flex 取最高项）就会先降后升，下方商品区会跟着上下抖一下 */
  height: 44px;
  /* 左内边距交给钉住的「全部」项自己承担（见下），否则滚动时分类名会从它左侧那 12px 里漏出来。
     上内边距 16px 与 .cat-bar__name 的行高 22px 是一对：两者相加正好 44px 填满高度，
     并让名称基线与小横杆都停在改动前的位置（基线 y=87、横杆 95~98） */
  padding: 16px 12px 0 0;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}
.cat-bar::-webkit-scrollbar {
  display: none;
}
.cat-bar__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  /* 名称与小横杆的间距 */
  gap: 3px;
  /* 不被 flex 压缩，保证横向滚动而不是挤成一团 */
  flex: 0 0 auto;
  /* 项间距 */
  margin-right: 20px;
  cursor: pointer;
  /* 层叠基准：未选中项固定在 0，低于钉住项（1），滑动时照旧被「全部」盖住。
     选中项会抬到 2，详见 .cat-bar__item.is-active */
  position: relative;
  z-index: 0;
}
.cat-bar__item:last-child {
  margin-right: 0;
}
/* 「全部」钉在分类栏最左侧：横向滑动时原地不动，其余分类从它后面滑过 */
.cat-bar__item--pinned {
  position: sticky;
  left: 0;
  /* 左 12px：自身盒子贴住滚动区左边缘（x=0），文字被顶到 12px，与搜索框左对齐；
     右 16px：把「项间距」一并画进背景，滑到它后面的分类名从标签外侧才露出来 */
  padding: 0 16px 0 12px;
  margin-right: 0;
  /* 实底，与 App.vue 的页面底色同源：盖住滑到它后面的分类名 */
  background: var(--van-background);
  z-index: 1;
}
/* 选中项必须压在钉住项（z-index: 1）之上。
   放大是绕名称盒中心缩放，名称左右各向外溢出 (scale-1)/2 × 名称宽 ≈ 8px（4 字）：
   选中紧挨「全部」的分类（如「手机数码」）时，左侧那 8px 会落进「全部」的不透明背景里，
   层叠顺序若低于它，就会被切掉半边字。抬到 2 即可整字完整显示。
   注意这里不能用「降钉住项 z-index」的写法：那会让滑动时的分类名盖在「全部」上。
   2 个 class 的选择器（0,2,0）优先级高于 .cat-bar__item--pinned（0,1,0），
   所以「全部」自己被选中时同样是 2，行为不变。 */
.cat-bar__item.is-active {
  z-index: 2;
}
/* 选中项被横滑到钉住项下面时（由 syncActiveUnderPinned 打上这个标记）：
   降回与普通项同层，重新被「全部」的实底背景盖住 —— 否则上面那条抬升会让它浮在
   「全部」之上，在左侧 12px 留白里露出半截字。
   4 个 class 的选择器（0,4,0）优先级高于 .cat-bar__item.is-active（0,2,0）。 */
.cat-bar.is-active-under-pinned .cat-bar__item.is-active {
  z-index: 0;
}
.cat-bar__name {
  font-size: 14px;
  font-weight: 500;
  color: #646566;
  white-space: nowrap;
  /* 行高写死 22px：名称盒高不随选中状态变化（放大走 transform，不占布局），
     小横杆的纵向位置才不会跟着动；22px 与容器的上内边距 16px 配套，合计 44px */
  line-height: 22px;
  /* 0.15s 的加速过渡：点中后名称迅速放大变深。
     放大走 transform 而不是 font-size —— transform 不参与布局，
     所以既没有横向回流，也不会让 flex 的基线锚点（由最高项决定）浮动 */
  transition: transform 0.15s ease-out, color 0.15s ease-out;
}
/* 选中态：名称放大、加粗、颜色加深。
   1.2857 = 18 / 14，即把 14px 等效放大到 18px（改字号要同步改这个比值）；
   translateY 抵消「绕盒中心缩放会把基线推低」的量 = (scale - 1) × 6px ≈ 1.72px，
   其中 6px 是本项目字体栈在 14px 下「基线到行盒中心」的距离，换字体需重新量；
   两项用同一条缓动同步插值，故每一帧都精确抵消 */
.cat-bar__item.is-active .cat-bar__name {
  font-weight: 700;
  color: #323233;
  transform: translateY(-1.72px) scale(1.2857);
}
/* 小横杆：仅选中项显示，未选中时保留占位（避免切换时整行高度跳动）。
   颜色与底部 tabbar 的选中指示条（TabBarLayout 的 .tabbar__indicator）**同源**：
   取 App.vue 在 :root 覆盖的 Vant 选中色变量，改那一个变量时两处的条会一起变，
   不用在这里再抄一份色值。fallback 与指示条保持一致，防止变量缺失时两处又分叉。
   注意别用 --color-brand：那个还管着搜索框描边与搜索按钮，是另一个色值。 */
.cat-bar__bar {
  width: 18px;
  height: 3px;
  border-radius: 2px;
  background: var(--van-tabbar-item-active-color, rgb(59, 130, 246));
  opacity: 0;
  transform: scaleX(0.2);
  transition: opacity 0.15s ease-out, transform 0.15s ease-out;
}
.cat-bar__item.is-active .cat-bar__bar {
  opacity: 1;
  transform: scaleX(1);
}
/* 首页商品网格：一行两个（与商品列表页一致）。
   两列后卡片宽度充足，直接沿用 ProductCard 的默认样式，不再为挤进一行而压缩字号/隐藏信息 */
.product-grid {
  display: grid;
  /* minmax(0, 1fr) 而非 1fr：否则列宽会被卡片内文字的最小内容宽度撑开而溢出 */
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
/* 触底加载动画：列表末尾留白里居中的一个空心圆环（对齐参考图）。
   它是**文档流内**的元素，不再像上一版那样做 fixed 浮层 ——
   浮层会盖在商品上、还要和 tabbar 抢位置；放在列表末尾才能做到
   「拉到最下边，在留白区域里转圈」。
   上下各 16px 内边距 + 圆环本身 20px，合计 52px 的干净空白带。
   align-items: center 是必须的：flex 默认 stretch 会把圆环的容器盒纵向拉高，
   留白高度就不再由 padding 决定（实测被拉到 50px 高）。 */
.list-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px 0;
}
/* 圆环用 Vant 的浅灰（--van-gray-5），比正文浅一档，不抢视线 */
.list-loading__spinner {
  color: #c8c9cc;
}
</style>
