<script setup lang="ts">
/**
 * 搜索页。顶部「返回 + 输入框 + 搜索」，下方在**同一路由内**切换两种状态：
 *   - 空态（尚未提交关键词）：搜索历史 + 热门搜索
 *   - 结果态（已提交关键词）：复用 ProductFeed（排序栏 + 网格 + 触底分页）
 *
 * 为什么结果不跳 `/products`：搜索是个「反复改词」的动作，结果留在本页可以随时改词重搜，
 * 不必来回跳转，也不会在返回栈里堆一串记录。URL 仍用 `router.replace` 同步 keyword，
 * 因此结果页可刷新、可直接分享。
 *
 * 数据来源全部是既有接口，本页没有新增后端能力：
 *   搜索   → GET /api/products?keyword=…
 *   热搜   → GET /api/categories（取前 8 个分类名）
 *   历史   → localStorage，只存本机
 */
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog } from 'vant'
import { fetchCategories } from '@/api/product'
import ProductFeed from '@/components/ProductFeed.vue'

const route = useRoute()
const router = useRouter()

/** 搜索历史在本机的存储键与上限 */
const HISTORY_KEY = 'tickcart.search.history'
const HISTORY_MAX = 10
/** 热门搜索展示的个数（分类名） */
const HOT_MAX = 8
/** 关键词长度上限。后端对该字段没有长度约束，先在前端收口。 */
const KEYWORD_MAX = 30

const inputText = ref('')
/** 已提交的关键词。空串 = 空态。与 inputText 分开，避免边打字边重查。 */
const keyword = ref('')
const total = ref(0)
const history = ref<string[]>(readHistory())
const hotWords = ref<string[]>([])

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
  persistHistory()
}

async function clearHistory() {
  try {
    await showConfirmDialog({ title: '清空搜索历史', message: '确定要清空全部搜索历史吗？' })
  } catch {
    return // 点了取消
  }
  history.value = []
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

/** 无上一页时（直接打开 /search）兜底回首页，避免返回箭头点了没反应 */
function goBack() {
  if (window.history.state?.back) {
    router.back()
  } else {
    router.replace({ name: 'home' })
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

  try {
    const categories = await fetchCategories()
    hotWords.value = categories.slice(0, HOT_MAX).map((c) => c.name)
  } catch {
    // 热搜拉不到就不显示该区块，不影响搜索本身
  }
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
        <div class="chips">
          <span v-for="word in history" :key="word" class="chip" @click="submit(word)">
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
      </template>
      <template #empty>
        <span class="search__empty-tip">换个关键词试试</span>
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

/* ── 结果态 ─────────────────────────────────────────────────────── */
.search__count {
  padding: 10px 12px 0;
  font-size: 12px;
  color: #969799;
}
.search__empty-tip {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: #969799;
}
</style>
