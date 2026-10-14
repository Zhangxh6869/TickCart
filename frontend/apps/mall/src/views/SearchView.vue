<script setup lang="ts">
/**
 * 搜索页。顶部「返回 + 输入框 + 搜索」，下方在**同一路由内**切换两种状态：
 *   - 空态（尚未提交关键词）：搜索历史 + 热门搜索
 *   - 结果态（已提交关键词）：复用 ProductFeed（排序栏 + 网格 + 触底分页）
 *
 * 为什么结果不跳别的页面：搜索是个「反复改词」的动作，结果留在本页可以随时改词重搜，
 * 不必来回跳转，也不会在返回栈里堆一串记录。URL 仍用 `router.replace` 同步 keyword，
 * 因此结果页可刷新、可直接分享。
 *
 * 数据来源：
 *   搜索   → GET /api/products?keyword=…
 *   热搜   → GET /api/search/hot（真实搜索词频，后端无数据时兜底热销商品名）
 *   相关词 → GET /api/search/related?keyword=…（真实日志相关词 + 命中商品高频词兜底）
 *   历史   → localStorage，只存本机
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog } from 'vant'
import { fetchHotSearches, fetchRelatedSearches } from '@/api/search'
import ProductFeed from '@/components/ProductFeed.vue'

const route = useRoute()
const router = useRouter()

/** 搜索历史在本机的存储键与上限 */
const HISTORY_KEY = 'tickcart.search.history'
/**
 * 历史上限。
 *
 * ★ 这个值必须**明显大于**「折叠两行能放下的条数」，否则折叠永远不触发、箭头永远不出现。
 *   踩过的坑：原来是 10，而手机宽度（390px）下两行正好能放下 10 条短词（「手机」「裤子」
 *   这种 2 字词约 6 枚/行）—— 于是历史攒满上限也超不出两行，箭头一次都没出现过。
 *   最坏情况是 1 字词（约 6.9 枚/行 → 两行 12 枚），所以取 20 能保证任何手机宽度下都超出两行。
 */
const HISTORY_MAX = 20
/**
 * 历史折叠时保留的行数。
 *
 * 超出这两行的历史词隐藏起来，末尾给一个箭头点开看全部（见 {@link historyExpanded}）。
 * 为什么折叠：历史攒多了会占掉三四行，把下面的「热门搜索」挤出首屏 ——
 * 而空态里用户更常点的是热搜词。
 *
 * ★ 这里只写「几行」而不是「几条」：chip 宽度随文字长短变化，同样 7 条，
 *   窄屏会占 3 行、宽屏可能只占 2 行，写死条数没法保证「刚好两行」。
 *   实际能放几条由 {@link measureFoldCapacity} 量出来。
 */
const HISTORY_FOLD_ROWS = 2
/** 量不到行间距时的兜底值，与 `.chips` 的 gap 保持一致 */
const CHIP_GAP_FALLBACK = 8
/** 热门搜索展示的个数 */
const HOT_MAX = 8
/** 相关搜索展示的个数 */
const RELATED_MAX = 8
/** 关键词长度上限。后端对该字段没有长度约束，先在前端收口。 */
const KEYWORD_MAX = 30

const inputText = ref('')
/** 已提交的关键词。空串 = 空态。与 inputText 分开，避免边打字边重查。 */
const keyword = ref('')
const total = ref(0)
const history = ref<string[]>(readHistory())
/** 历史是否已展开（仅影响展示，不改存储） */
const historyExpanded = ref(false)
/** 历史词容器（要拿它量行高 / 行宽，见 measureFoldCapacity） */
const chipsRef = ref<HTMLElement | null>(null)
/**
 * 折叠时能显示的条数 —— 由 {@link measureFoldCapacity} 实测「两行」得出。
 *
 * 放得下全部时会被设成 `history.length`，此时不折叠、也不显示箭头。
 */
const foldCapacity = ref(Number.MAX_SAFE_INTEGER)
/**
 * 首次实测是否完成。
 *
 * 实测要先把全部历史词渲染出来量一遍，所以首帧会短暂是「全部展开」的样子；
 * 用这个标记在实测完成前把 chips 容器设成 `visibility: hidden`，
 * 避免用户看到一帧「先全展开、再收起来」的跳动。
 * （实测与收紧都发生在同一个 microtask 批次里，正常情况下根本不会绘制到屏幕。）
 */
const foldReady = ref(false)

/**
 * 实际渲染的历史词。
 *
 * 折叠时只取前 {@link foldCapacity} 条。用 `computed` 而不是在 `pushHistory` 里截断 ——
 * 存储始终是完整的 10 条，折叠只是展示层的切分，删掉一条后下一条会自动补上来。
 */
const visibleHistory = computed(() =>
  historyExpanded.value || !foldReady.value
    ? history.value
    : history.value.slice(0, foldCapacity.value)
)

/**
 * 是否显示「展开/收起」箭头。
 *
 * 实测期间（`!foldReady`）也要渲染出来 —— 量第 2 行还剩多少位置给箭头时需要它的宽度。
 */
const showFoldArrow = computed(
  () => history.value.length > 0 && (!foldReady.value || history.value.length > foldCapacity.value)
)

/**
 * 实测「折叠两行能放下几枚历史词」。
 *
 * 步骤：① 先把全部历史词渲染出来；② 按 `offsetTop` 把 chip 分到第 1 / 第 2 行；
 * ③ 若两行装得下全部 → 不折叠；④ 否则从第 2 行末尾往前让位，
 * 直到「最后一枚 chip 右缘 + 间距 + 箭头宽」不超容器宽 —— 这样箭头才落在第 2 行内。
 *
 * ★ 为什么必须量而不能算：chip 宽度取决于文字长度，且容器宽度随视口变化。
 * ★ 让位只需要做算术，不用反复渲染：从末尾拿掉 chip 不会改变它前面那些 chip 的位置。
 */
async function measureFoldCapacity() {
  const el = chipsRef.value
  if (!el || !history.value.length) {
    foldReady.value = true
    return
  }

  // ① 全部渲染出来量一遍（此时容器是 hidden 的，用户看不到）
  foldReady.value = false
  await nextTick()

  const chips = [...el.querySelectorAll<HTMLElement>('.chip--history')]
  if (!chips.length) {
    foldReady.value = true
    return
  }

  const gap = Number.parseFloat(getComputedStyle(el).rowGap) || CHIP_GAP_FALLBACK
  const firstTop = chips[0].offsetTop
  const rowHeight = chips[0].offsetHeight + gap
  const secondRowBottom = firstTop + HISTORY_FOLD_ROWS * rowHeight

  const row1 = chips.filter((c) => c.offsetTop < firstTop + rowHeight / 2)
  const row2 = chips.filter(
    (c) => c.offsetTop >= firstTop + rowHeight / 2 && c.offsetTop < secondRowBottom
  )

  // ③ 两行就装得下全部 → 不需要折叠，也不显示箭头
  if (row1.length + row2.length === chips.length) {
    foldCapacity.value = chips.length
    foldReady.value = true
    return
  }

  // ④ 给箭头腾位置
  const arrow = el.querySelector<HTMLElement>('.chip--more')
  const arrowWidth = arrow ? arrow.offsetWidth : 0
  const box = el.getBoundingClientRect()
  let keepInRow2 = row2.length
  while (keepInRow2 > 0) {
    const used = row2[keepInRow2 - 1].getBoundingClientRect().right - box.left
    if (used + gap + arrowWidth <= box.width + 0.5) break
    keepInRow2 -= 1
  }

  foldCapacity.value = row1.length + keepInRow2
  foldReady.value = true
}

const hotWords = ref<string[]>([])
/** 与当前关键词相关的搜索词（结果态展示） */
const related = ref<string[]>([])

/**
 * 读取本机搜索历史。
 * 隐私模式 / 被禁用时 `localStorage` 会直接抛异常，脏数据也可能不是数组 ——
 * 这里一律降级成「没有历史」，不让存储问题影响搜索本身。
 */
function readHistory(): string[] {
  try {
    const raw = localStorage.getItem(HISTORY_KEY)
    const list = raw ? JSON.parse(raw) : []
    if (!Array.isArray(list)) return []
    return list.filter((w): w is string => typeof w === 'string' && w.length > 0).slice(0, HISTORY_MAX)
  } catch {
    return []
  }
}

function persistHistory() {
  try {
    localStorage.setItem(HISTORY_KEY, JSON.stringify(history.value))
  } catch {
    // 存不下（隐私模式/配额满）不影响本次搜索，静默忽略
  }
}

/** 记录一次搜索：去重后置顶，超出上限截断 */
function pushHistory(word: string) {
  const next = [word, ...history.value.filter((w) => w !== word)]
  history.value = next.slice(0, HISTORY_MAX)
  persistHistory()
}

function removeHistory(word: string) {
  history.value = history.value.filter((w) => w !== word)
  // 删到两行就装得下时箭头会消失，此时必须把展开态复位，
  // 否则下次历史又攒多了、箭头重新出现时，会莫名其妙已经是「展开」状态
  if (history.value.length <= foldCapacity.value) historyExpanded.value = false
  persistHistory()
}

async function clearHistory() {
  try {
    await showConfirmDialog({ title: '清空搜索历史', message: '确定要清空全部搜索历史吗？' })
  } catch {
    return // 点了取消
  }
  history.value = []
  historyExpanded.value = false
  persistHistory()
}

/**
 * 提交搜索。`word` 传入时用于点历史/热搜词直接搜。
 * 空串与纯空格不提交，也不写历史 —— 否则清空输入框回车会塞进一条空记录。
 */
function submit(word?: string) {
  const text = (word ?? inputText.value).trim()
  if (!text) return
  inputText.value = text
  keyword.value = text
  pushHistory(text)
  // replace 而非 push：连续改词不该在返回键里堆一长串记录
  router.replace({ name: 'search', query: { keyword: text } })
}

/** 清空输入框（小叉或退格删空）→ 回到空态并摘掉 URL 上的 keyword */
function resetToIdle() {
  keyword.value = ''
  total.value = 0
  router.replace({ name: 'search' })
}

/**
 * 输入框被清空时回到空态。
 *
 * ★ 必须监听而不是只靠 `van-search` 的 `@clear`：`@clear` 只在小叉被点时触发，
 * 用户**用退格把文字删空**时不会发这个事件，页面会停在旧结果上 —— 输入框是空的、
 * 下面却还是上一次的搜索结果，看起来像坏了。小叉本身也会走 `v-model` 把值置空，
 * 所以这一条同时覆盖两种清空方式。
 */
watch(inputText, (val) => {
  if (!val.trim() && keyword.value) resetToIdle()
})

/** 关键词变化 → 重新拉相关搜索词（清空关键词即清空相关词） */
watch(keyword, (kw) => {
  if (kw) loadRelated(kw)
  else related.value = []
})

/** 无上一页时（直接打开 /search）兜底回首页，避免返回箭头点了没反应 */
function goBack() {
  if (window.history.state?.back) {
    router.back()
  } else {
    router.replace({ name: 'home' })
  }
}

/**
 * 拉热门搜索词。
 *
 * 数据来自真实搜索日志（`search_log`，见 V13）；后端在日志为空时会兜底
 * 「热销商品名的首段」。★ 兜底为什么不用分类名：后端搜索只匹配商品名/副标题，
 * 分类名（「手机数码」「鞋类」…）几乎不会出现在商品文本里，点进去**必然** 0 结果
 * （实测 8 个分类名命中 0/8）—— 这条坑已在后端兜底里守住。
 */
async function loadHotWords() {
  try {
    hotWords.value = await fetchHotSearches(HOT_MAX)
  } catch {
    // 热搜拉不到就不显示该区块，不影响搜索本身
  }
}

/**
 * 拉「相关搜索」词：与当前关键词相关，结果态展示在商品网格上方。
 * 拉不到就清空、不显示该区块，不影响搜索本身。
 */
async function loadRelated(kw: string) {
  related.value = []
  try {
    related.value = await fetchRelatedSearches(kw, RELATED_MAX)
  } catch {
    related.value = []
  }
}

onMounted(async () => {
  // 支持 /search?keyword=xxx 直达（首页搜索框、外部分享链接）
  const q = typeof route.query.keyword === 'string' ? route.query.keyword.trim() : ''
  if (q) {
    inputText.value = q
    keyword.value = q
    pushHistory(q)
  }

  await loadHotWords()
})

// ── 折叠两行的实测时机 ───────────────────────────────────────────────
// ① 历史区块刚挂载（从结果态清空输入回空态时会出现）
watch(
  chipsRef,
  (el) => {
    if (el) measureFoldCapacity()
  },
  { flush: 'post' }
)
// ② 历史增删（`flush: 'post'` 保证量的是更新后的 DOM）
watch(history, () => measureFoldCapacity(), { flush: 'post' })

// ③ 视口变化 —— chip 宽度不变，但一行能放几枚会变。
//    防抖：resize 期间连续触发，每次都要读布局，不防抖会拖慢窗口缩放。
let resizeTimer: ReturnType<typeof setTimeout> | undefined
function onWindowResize() {
  clearTimeout(resizeTimer)
  resizeTimer = setTimeout(() => measureFoldCapacity(), 150)
}
onMounted(() => window.addEventListener('resize', onWindowResize))
onBeforeUnmount(() => {
  clearTimeout(resizeTimer)
  window.removeEventListener('resize', onWindowResize)
})
</script>

<template>
  <div class="search">
    <div class="search__bar">
      <button class="search__back" type="button" aria-label="返回" @click="goBack">
        <van-icon name="arrow-left" size="20" />
      </button>

      <van-search
        v-model="inputText"
        class="search__field"
        placeholder="搜索商品"
        show-action
        :maxlength="KEYWORD_MAX"
        clear-trigger="always"
        @search="submit()"
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
          <button class="search-btn" type="button" @click="submit()">搜索</button>
        </template>
      </van-search>
    </div>

    <!-- ── 空态：未提交关键词 ─────────────────────────────────────── -->
    <div v-if="!keyword" class="search__body">
      <section v-if="history.length" class="block">
        <div class="block__head">
          <span class="block__title">搜索历史</span>
          <button class="block__action" type="button" @click="clearHistory">清空</button>
        </div>
        <!-- visibility 由 foldReady 控制：实测「两行能放几枚」期间先不显示，避免闪一下 -->
        <div
          ref="chipsRef"
          class="chips"
          :style="{ visibility: foldReady ? undefined : 'hidden' }"
        >
          <span
            v-for="word in visibleHistory"
            :key="word"
            class="chip chip--history"
            @click="submit(word)"
          >
            {{ word }}
            <button
              class="chip__close"
              type="button"
              :aria-label="`删除历史记录 ${word}`"
              @click.stop="removeHistory(word)"
            >
              ×
            </button>
          </span>
          <!-- 展开/收起箭头：超出两行的历史被折叠时才出现，落在第 2 行的右下角 -->
          <button
            v-if="showFoldArrow"
            class="chip chip--more"
            type="button"
            :aria-label="historyExpanded ? '收起搜索历史' : '展开全部搜索历史'"
            @click="historyExpanded = !historyExpanded"
          >
            <van-icon :name="historyExpanded ? 'arrow-up' : 'arrow-down'" size="14" />
          </button>
        </div>
      </section>

      <section v-if="hotWords.length" class="block">
        <div class="block__head">
          <span class="block__title">热门搜索</span>
        </div>
        <div class="chips">
          <span
            v-for="word in hotWords"
            :key="word"
            class="chip chip--hot"
            @click="submit(word)"
          >
            {{ word }}
          </span>
        </div>
      </section>
    </div>

    <!-- ── 结果态：已提交关键词 ───────────────────────────────────── -->
    <ProductFeed
      v-else
      v-model:total="total"
      :keyword="keyword"
      empty-text="没有找到相关商品"
    >
      <template #summary>
        <div v-if="total > 0" class="search__count">为你找到 {{ total }} 件商品</div>
        <!-- 相关搜索：基于当前关键词，数据来自真实搜索日志（不足时用命中商品高频词兜底） -->
        <section v-if="related.length" class="block block--related">
          <div class="block__head">
            <span class="block__title">相关搜索</span>
          </div>
          <div class="chips">
            <span
              v-for="word in related"
              :key="word"
              class="chip chip--hot"
              @click="submit(word)"
            >
              {{ word }}
            </span>
          </div>
        </section>
      </template>
      <template #empty>
        <span class="search__empty-tip">换个关键词试试</span>
        <!-- 搜不到时给一组「一定搜得到」的热门词，别让用户困在空结果里 -->
        <div v-if="hotWords.length" class="chips chips--center">
          <span
            v-for="word in hotWords"
            :key="word"
            class="chip chip--hot"
            @click="submit(word)"
          >
            {{ word }}
          </span>
        </div>
      </template>
    </ProductFeed>
  </div>
</template>

<style scoped>
.search {
  /* 二级页：无 tabbar，所以占满整屏（不要照抄 .home 的 calc(100vh - 50px)）。
     不透明 + 至少一屏高是页面过渡期间的硬要求，否则旧页会从它下面透出来。 */
  min-height: 100vh;
  background: var(--van-background, #fff);
  /* 品牌色与首页搜索框同源；首页那套是局部变量，这里给同样的默认值兜底 */
  --color-brand: rgb(107, 163, 200);
  --color-search-icon: #515151;
}

/* 搜索栏吸顶：结果列表很长时也随时能改词 */
.search__bar {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  padding: 6px 12px 6px 2px;
  background: var(--van-background, #fff);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.06);
}
.search__back {
  flex: none;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: #323233;
  cursor: pointer;
}
.search__field {
  flex: 1;
  min-width: 0;
}

/* 与首页搜索框同一套外观（品牌色描边 + 浅蓝底 + 内嵌按钮），
   但首页那份样式挂在 .home 作用域下、且是 readonly 触发式，
   两边行为不同，故此处独立维护一份，不强行抽公共组件。 */
.search :deep(.van-search) {
  --van-search-left-icon-color: var(--color-search-icon);
  --van-field-placeholder-text-color: var(--color-search-icon);
  padding: 2px;
  border: 2px solid var(--color-brand);
  border-radius: 6px;
  background: rgb(232, 240, 248);
}
.search-icon {
  display: block;
  width: var(--van-field-icon-size);
  height: var(--van-field-icon-size);
}
.search :deep(.van-search__content) {
  border: none;
  background: transparent;
  padding-left: 8px;
}
.search :deep(.van-field__left-icon) {
  margin-right: 10px;
}
.search :deep(.van-search__action) {
  padding: 0;
}
.search-btn {
  height: var(--van-search-input-height); /* 34px，与输入区等高 */
  padding: 0 14px;
  border: none;
  border-radius: 4px;
  background: var(--color-brand);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}

/* ── 空态：历史 / 热搜 ───────────────────────────────────────────── */
.block {
  padding: 14px 12px 2px;
}
.block__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.block__title {
  font-size: 14px;
  font-weight: 500;
  color: #323233;
}
.block__action {
  border: none;
  background: transparent;
  padding: 0;
  font-size: 12px;
  color: #969799;
  cursor: pointer;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 5px 10px;
  border-radius: 12px;
  background: #f2f3f5;
  color: #323233;
  font-size: 12px;
  cursor: pointer;
}
.chip--hot {
  background: #e6f1fb;
  color: #185fa5;
}
.chip__close {
  border: none;
  background: transparent;
  padding: 0 0 0 2px;
  color: #969799;
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
}
/**
 * 历史「展开/收起」箭头。
 *
 * 沿用 .chip 的底色与圆角，让它落在 chip 流里而不突兀，但**只留一个图标的宽度**
 * （`.chip` 是 5px 10px 的内边距，图标按钮用不上，故这里收窄）。
 *
 * ★ `margin-left: auto` 让它**贴齐容器右边**，也就是历史词区域的「右下角」：
 *   flex 布局里 auto 外边距会吃掉本行的剩余空间，所以不管它最终落在哪一行，
 *   都会被推到那一行的最右端；本行塞不下时会先换行、再贴到新行的最右端。
 *   （只写 `margin-left: auto` 就够，不用 `justify-content` —— 后者会影响所有 chip。）
 *
 * ★ 类名 `chip--more` 是回归脚本的选择器契约：`search-verify.mjs` 用
 *   `.chip:not(.chip--hot):not(.chip--more)` 取「真正的历史词」，改类名要同步改它。
 */
.chip--more {
  border: none;
  padding: 5px 8px;
  color: #646566;
  line-height: 1;
  margin-left: auto;
}
.chip--more:active {
  background: #e6e7e9;
}

/* ── 结果态 ─────────────────────────────────────────────────────── */
.search__count {
  padding: 10px 12px 0;
  font-size: 12px;
  color: #969799;
}
/* 相关搜索区：紧跟在「为你找到 N 件商品」下方，与网格之间留一点距离 */
.block--related {
  padding-top: 10px;
  padding-bottom: 6px;
}
.search__empty-tip {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: #969799;
}
/* 空结果里的热搜词：van-empty 是居中的，这里也要跟着居中且限宽换行 */
.chips--center {
  justify-content: center;
  max-width: 300px;
  margin: 14px auto 0;
}
</style>
