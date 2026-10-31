<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, type UploadFile } from 'element-plus'
import { ArrowDown, ArrowUp, Close, Delete, Plus } from '@element-plus/icons-vue'
import {
  ADDABLE_BLOCK_TYPES,
  createDetailBlock,
  parseDetailHtml,
  serializeDetailBlocks,
  type DetailBlock,
  type DetailBlockType,
} from '@tickcart/shared-utils'
import { ACCEPT_IMAGE_TYPES, uploadImage, validateImageFile } from '@/api/upload'

/**
 * 商品详情的可视化编辑器。
 *
 * <p>把详情拆成「标题 / 段落 / 要点 / 参数表 / 图片 / 分割线」几种区块，运营按区块编辑，
 * 组件负责与 {@code product.detail} 里的 HTML 互转（规则见 {@code @tickcart/shared-utils}
 * 的 detail-blocks）。不引入第三方富文本编辑器：详情只需要这几种固定结构，
 * 块编辑比富文本更可控，产出的 HTML 也一定落在商城端的清洗白名单内。</p>
 *
 * <p>仍保留「HTML 源码」模式：既方便改特殊排版，也是解析器认不出的历史内容的兜底出口。</p>
 */
const props = defineProps<{
  /** 详情 HTML（v-model）。允许空：新建商品时 form.detail 是空串，历史数据也可能是 null */
  modelValue?: string | null
  /** 该商品已有的图集地址，供「从图集选择」使用 */
  images?: string[]
}>()

const emit = defineEmits<{ (e: 'update:modelValue', value: string): void }>()

type ImageBlock = Extract<DetailBlock, { type: 'image' }>
type ListBlock = Extract<DetailBlock, { type: 'list' }>
type ParamsBlock = Extract<DetailBlock, { type: 'params' }>

const TYPE_LABEL: Record<DetailBlockType, string> = {
  heading: '小标题',
  paragraph: '段落',
  list: '要点列表',
  params: '参数表',
  image: '图片',
  divider: '分割线',
  html: '自定义结构',
}

const mode = ref<'blocks' | 'html'>('blocks')
const blocks = ref<DetailBlock[]>([])
/** HTML 源码模式下的编辑缓冲 */
const htmlSource = ref('')

/**
 * 最近一次由本组件写出去的 HTML。
 *
 * <p>用来区分「外部换了内容（切商品/回填）」和「自己 emit 出去又被 v-model 传回来」：
 * 不区分的话，每次输入都会触发一次 parse，光标会跳、正在输入的区块也会被重建。</p>
 */
let lastEmitted = ''

watch(
  () => props.modelValue,
  (value) => {
    const html = value ?? ''
    if (html === lastEmitted) return
    blocks.value = parseDetailHtml(html)
    htmlSource.value = html
  },
  { immediate: true },
)

// 区块改动 → 立刻序列化回 HTML，父组件的 form.detail 始终是最新值
watch(
  blocks,
  () => {
    if (mode.value !== 'blocks') return
    const html = serializeDetailBlocks(blocks.value)
    lastEmitted = html
    emit('update:modelValue', html)
  },
  { deep: true },
)

// 源码模式下的改动同样要写回父组件，否则切到源码改完直接保存会丢
watch(htmlSource, (value) => {
  if (mode.value !== 'html') return
  lastEmitted = value
  emit('update:modelValue', value)
})

/** 两种模式互切时把「另一侧」的内容同步过来 */
watch(mode, (next) => {
  if (next === 'html') {
    htmlSource.value = serializeDetailBlocks(blocks.value)
  } else {
    blocks.value = parseDetailHtml(htmlSource.value)
  }
})

/** 新增区块的 id 只需在本次编辑内唯一，用自增计数即可 */
let seed = 0
const nextId = () => `n${(seed += 1)}`

function addBlock(type: DetailBlockType) {
  blocks.value.push(createDetailBlock(type, nextId()))
}

function removeBlock(index: number) {
  blocks.value.splice(index, 1)
}

function moveBlock(index: number, offset: number) {
  const target = index + offset
  if (target < 0 || target >= blocks.value.length) return
  const [block] = blocks.value.splice(index, 1)
  blocks.value.splice(target, 0, block)
}

function addListItem(block: ListBlock) {
  block.items.push('')
}

function addParamRow(block: ParamsBlock) {
  block.rows.push({ label: '', value: '' })
}

// ---------------- 图片区块 ----------------
const uploading = ref(false)

function pickImage(block: ImageBlock, url: string) {
  block.url = url
}

async function onImageChange(block: ImageBlock, uploadFile: UploadFile) {
  const raw = uploadFile.raw
  if (!raw) return
  const error = validateImageFile(raw)
  if (error) {
    ElMessage.warning(error)
    return
  }
  uploading.value = true
  try {
    block.url = await uploadImage(raw)
    if (!block.alt) block.alt = ''
  } catch {
    // 具体错误由请求拦截器统一提示
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <div class="dbe">
    <div class="dbe__bar">
      <el-radio-group v-model="mode" size="small">
        <el-radio-button value="blocks">可视化编辑</el-radio-button>
        <el-radio-button value="html">HTML 源码</el-radio-button>
      </el-radio-group>
      <span v-if="mode === 'blocks'" class="dbe__count">{{ blocks.length }} 个区块</span>
    </div>

    <!-- 可视化 -->
    <div v-if="mode === 'blocks'" class="dbe__list">
      <div v-if="!blocks.length" class="dbe__empty">还没有内容，从下方添加第一个区块</div>

      <div v-for="(block, index) in blocks" :key="block.id" class="dbe__block">
        <div class="dbe__head">
          <el-tag size="small" type="info" disable-transitions>{{ TYPE_LABEL[block.type] }}</el-tag>
          <span class="dbe__ops">
            <el-button
              link
              :icon="ArrowUp"
              title="上移"
              :disabled="index === 0"
              @click="moveBlock(index, -1)"
            />
            <el-button
              link
              :icon="ArrowDown"
              title="下移"
              :disabled="index === blocks.length - 1"
              @click="moveBlock(index, 1)"
            />
            <el-button link type="danger" :icon="Delete" title="删除" @click="removeBlock(index)" />
          </span>
        </div>

        <el-input
          v-if="block.type === 'heading'"
          v-model="block.text"
          placeholder="小标题，如「产品亮点」"
          maxlength="30"
          show-word-limit
        />

        <el-input
          v-else-if="block.type === 'paragraph'"
          v-model="block.text"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 6 }"
          placeholder="一段说明文字"
        />

        <div v-else-if="block.type === 'list'" class="dbe__rows">
          <div v-for="(_, itemIndex) in block.items" :key="itemIndex" class="dbe__row">
            <span class="dbe__dot" />
            <el-input v-model="block.items[itemIndex]" placeholder="一条要点" />
            <el-button
              link
              type="danger"
              :icon="Close"
              title="删除这条"
              @click="block.items.splice(itemIndex, 1)"
            />
          </div>
          <el-button link type="primary" :icon="Plus" @click="addListItem(block)">
            添加一条
          </el-button>
        </div>

        <div v-else-if="block.type === 'params'" class="dbe__rows">
          <div v-for="(_, rowIndex) in block.rows" :key="rowIndex" class="dbe__row dbe__row--param">
            <el-input v-model="block.rows[rowIndex].label" placeholder="参数名" />
            <el-input v-model="block.rows[rowIndex].value" placeholder="参数值" />
            <el-button
              link
              type="danger"
              :icon="Close"
              title="删除这一行"
              @click="block.rows.splice(rowIndex, 1)"
            />
          </div>
          <el-button link type="primary" :icon="Plus" @click="addParamRow(block)">
            添加一行
          </el-button>
        </div>

        <div v-else-if="block.type === 'image'" class="dbe__image">
          <div class="dbe__image-preview">
            <img v-if="block.url" :src="block.url" alt="" />
            <span v-else class="dbe__image-empty">未选图</span>
          </div>
          <div class="dbe__image-side">
            <el-input v-model="block.url" placeholder="图片地址，如 /img/skincare/201-1.jpg" />
            <el-input v-model="block.alt" placeholder="图片说明（alt，选填）" />
            <div class="dbe__image-ops">
              <el-popover placement="bottom-start" :width="330" trigger="click">
                <template #reference>
                  <el-button size="small" :disabled="!images?.length">从图集选择</el-button>
                </template>
                <div v-if="images?.length" class="dbe__picker">
                  <button
                    v-for="url in images"
                    :key="url"
                    type="button"
                    class="dbe__picker-item"
                    :class="{ 'is-active': block.url === url }"
                    :title="url"
                    @click="pickImage(block, url)"
                  >
                    <img :src="url" alt="" />
                  </button>
                </div>
                <div v-else class="dbe__picker-empty">图集还没有图片，先到「图片」页签添加</div>
              </el-popover>
              <el-upload
                :show-file-list="false"
                :auto-upload="false"
                :accept="ACCEPT_IMAGE_TYPES"
                :disabled="uploading"
                :on-change="(file: UploadFile) => onImageChange(block, file)"
              >
                <el-button size="small" :loading="uploading">上传图片</el-button>
              </el-upload>
            </div>
          </div>
        </div>

        <div v-else-if="block.type === 'divider'" class="dbe__divider" />

        <div v-else-if="block.type === 'html'" class="dbe__html">
          <div class="dbe__tip">这段是自定义结构，已原样保留，可直接改源码。</div>
          <el-input v-model="block.text" type="textarea" :autosize="{ minRows: 3, maxRows: 10 }" />
        </div>
      </div>

      <div class="dbe__add">
        <span class="dbe__add-label">添加区块</span>
        <el-button
          v-for="item in ADDABLE_BLOCK_TYPES"
          :key="item.type"
          size="small"
          plain
          @click="addBlock(item.type)"
        >
          {{ item.label }}
        </el-button>
      </div>
    </div>

    <!-- 源码 -->
    <div v-else class="dbe__source">
      <el-input v-model="htmlSource" type="textarea" :rows="16" spellcheck="false" />
      <div class="dbe__tip">
        支持 h3 / p / ul / li / table / img / hr 等标签，商城端会按白名单清洗后渲染；
        图片地址用 /img/ 或 /api/files/ 开头的站内路径。
      </div>
    </div>
  </div>
</template>

<style scoped>
.dbe {
  width: 100%;
}
.dbe__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.dbe__count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.dbe__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.dbe__empty {
  padding: 18px;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  color: var(--el-text-color-placeholder);
  font-size: 13px;
  text-align: center;
}
.dbe__block {
  padding: 8px 10px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
}
.dbe__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.dbe__ops :deep(.el-button) {
  margin-left: 2px;
  padding: 2px;
}
.dbe__rows {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.dbe__row {
  display: flex;
  align-items: center;
  gap: 6px;
}
/* 参数表的两列等宽，第三列是删除按钮 */
.dbe__row--param :deep(.el-input) {
  flex: 1;
}
.dbe__dot {
  flex: 0 0 5px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--el-color-primary);
}
.dbe__image {
  display: flex;
  gap: 12px;
}
.dbe__image-preview {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 84px;
  height: 84px;
  overflow: hidden;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  background: var(--el-fill-color-light);
}
.dbe__image-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.dbe__image-empty {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
.dbe__image-side {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.dbe__image-ops {
  display: flex;
  gap: 8px;
}
.dbe__picker {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 6px;
  max-height: 220px;
  overflow-y: auto;
}
.dbe__picker-item {
  width: 100%;
  aspect-ratio: 1;
  padding: 0;
  overflow: hidden;
  border: 2px solid transparent;
  border-radius: 4px;
  background: var(--el-fill-color-light);
  cursor: pointer;
}
.dbe__picker-item.is-active {
  border-color: var(--el-color-primary);
}
.dbe__picker-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.dbe__picker-empty {
  color: var(--el-text-color-placeholder);
  font-size: 13px;
}
.dbe__divider {
  height: 1px;
  background: var(--el-border-color);
}
.dbe__html :deep(textarea) {
  font-family: var(--el-font-family-mono, Consolas, Monaco, monospace);
  font-size: 12px;
}
.dbe__tip {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}
.dbe__add {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 10px;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
}
.dbe__add-label {
  margin-right: 2px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.dbe__source :deep(textarea) {
  font-family: var(--el-font-family-mono, Consolas, Monaco, monospace);
  font-size: 12px;
}
</style>
