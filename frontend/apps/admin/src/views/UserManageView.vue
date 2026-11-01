<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import type { AdminUserDetail, AdminUserSummary } from '@tickcart/shared-types'
import { formatPrice } from '@tickcart/shared-utils'
import { changeUserRole, changeUserStatus, fetchUserDetail, fetchUsers } from '@/api/user'
import { useAdminStore } from '@/stores/admin'

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

/** 订单状态 → 标签色，与订单管理页保持一致的语义（红=待处理，灰=终态） */
const ORDER_TAG: Record<number, TagType> = {
  0: 'warning',
  1: 'danger',
  2: 'primary',
  3: 'success',
  4: 'info',
  5: 'info',
  6: 'warning',
  7: 'info',
}

const GENDER_TEXT: Record<number, string> = { 0: '未知', 1: '男', 2: '女' }

const adminStore = useAdminStore()

const loading = ref(false)
const list = ref<AdminUserSummary[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  /** undefined = 全部；el-select 清空后就是 undefined */
  status: undefined as number | null | undefined,
})

const dateRange = ref<[string, string] | null>(null)

const dateShortcuts = [
  {
    text: '今天',
    value: () => {
      const end = new Date()
      return [end, end]
    },
  },
  {
    text: '近 7 天',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setDate(start.getDate() - 6)
      return [start, end]
    },
  },
  {
    text: '近 30 天',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setDate(start.getDate() - 29)
      return [start, end]
    },
  },
]

// ---------------- 列表 ----------------

async function load() {
  loading.value = true
  try {
    const res = await fetchUsers({
      page: query.page,
      size: query.size,
      keyword: query.keyword.trim() || undefined,
      status: query.status ?? undefined,
      beginDate: dateRange.value?.[0] ?? null,
      endDate: dateRange.value?.[1] ?? null,
    })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

onMounted(load)

function onSearch() {
  query.page = 1
  load()
}

function onReset() {
  query.keyword = ''
  query.status = undefined
  dateRange.value = null
  onSearch()
}

function onSizeChange() {
  query.page = 1
  load()
}

// ---------------- 详情 ----------------

const drawer = ref(false)
const detailLoading = ref(false)
const detail = ref<AdminUserDetail | null>(null)

async function openDetail(id: number) {
  drawer.value = true
  detailLoading.value = true
  try {
    detail.value = await fetchUserDetail(id)
  } catch {
    // 拦截器已提示，关掉抽屉避免留下半截内容
    drawer.value = false
    detail.value = null
  } finally {
    detailLoading.value = false
  }
}

// ---------------- 启用 / 禁用 ----------------

/**
 * 能不能操作这个账号。
 *
 * 前端先挡一道，只为了不让人点出一个必然失败的按钮 ——
 * 真正的约束在后端（自己 / 管理员都拒绝），前端判断不可替代服务端校验。
 */
function canOperate(user: { id: number; role: number }): boolean {
  if (user.role === 1) {
    return false
  }
  return user.id !== adminStore.userInfo?.id
}

async function onToggle(user: { id: number; role: number; status: number; nickname?: string | null; username?: string }) {
  const name = user.nickname || user.username || `用户 ${user.id}`
  const disabling = user.status !== 1
  const action = disabling ? '禁用' : '启用'

  try {
    await ElMessageBox.confirm(
      disabling
        ? `禁用后「${name}」会立即下线（已登录的会话一并作废），且无法再登录。确认禁用？`
        : `确认启用「${name}」？启用后该用户可以正常登录。`,
      `${action}账号`,
      { type: 'warning', confirmButtonText: action, cancelButtonText: '取消' },
    )
  } catch {
    // 点了取消：什么都不做
    return
  }

  try {
    await changeUserStatus(user.id, disabling ? 1 : 0)
    ElMessage.success(`已${action}`)
  } catch {
    // 后端拒绝（禁用自己 / 管理员账号）时，拦截器已弹出具体原因
    return
  }

  await load()
  if (drawer.value && detail.value?.id === user.id) {
    await openDetail(user.id)
  }
}

// ---------------- 修改角色 ----------------

/**
 * 能不能调整这个账号的角色。
 *
 * 只有一条前端护栏：不能改自己（后端也会拒）。与 {@link canOperate} 不同，
 * 这里**允许**对管理员操作 —— 降级另一个管理员是正常管理动作。
 */
function canChangeRole(user: { id: number }): boolean {
  return user.id !== adminStore.userInfo?.id
}

async function onChangeRole(user: AdminUserSummary | AdminUserDetail) {
  const next: 0 | 1 = user.role === 1 ? 0 : 1
  const nextLabel = next === 1 ? '管理员' : '普通用户'
  const name = user.nickname || user.username || `用户 ${user.id}`

  try {
    await ElMessageBox.confirm(
      `将「${name}」的角色改为「${nextLabel}」？变更后该用户会被强制下线，需重新登录才能生效。`,
      '修改角色',
      { type: 'warning', confirmButtonText: '确认修改', cancelButtonText: '取消' },
    )
  } catch {
    // 点了取消
    return
  }

  try {
    await changeUserRole(user.id, next)
    ElMessage.success('角色已修改，该用户需重新登录')
  } catch {
    // 后端拒绝（改自己 / 最后一个管理员）时拦截器已提示
    return
  }

  await load()
  if (drawer.value && detail.value?.id === user.id) {
    await openDetail(user.id)
  }
}

// ---------------- 展示辅助 ----------------

const filteredHint = computed(() => {
  if (query.status === 0) return ' · 仅看正常'
  if (query.status === 1) return ' · 仅看禁用'
  return ''
})

function formatTime(value: string | null | undefined): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

/** 没有头像时用用户名首字占位，比灰色方块好认 */
function initialOf(row: AdminUserSummary): string {
  const name = row.nickname || row.username || ''
  return name.slice(0, 1).toUpperCase()
}

function orderTagType(status: number): TagType {
  return ORDER_TAG[status] ?? 'info'
}

/** 头像挂了就直接隐藏，别留一个碎图标 */
function hideBrokenImage(event: Event) {
  ;(event.target as HTMLImageElement).style.display = 'none'
}
</script>

<template>
  <div class="um">
    <div class="um__head">
      <div class="um__summary">
        共 <b>{{ total }}</b> 个用户{{ filteredHint }}
      </div>
      <div class="um__tip">列表手机号已脱敏，点「详情」看明文与订单记录</div>
    </div>

    <el-card class="um__filter" shadow="never">
      <div class="um__row">
        <el-input
          v-model="query.keyword"
          class="um__search"
          placeholder="手机号 / 用户名 / ID"
          clearable
          :prefix-icon="Search"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-select
          v-model="query.status"
          class="um__status"
          placeholder="全部状态"
          clearable
          @change="onSearch"
        >
          <el-option label="正常" :value="0" />
          <el-option label="禁用" :value="1" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="注册起始日"
          end-placeholder="结束日"
          :shortcuts="dateShortcuts"
          class="um__date"
          @change="onSearch"
        />
        <div class="um__spacer" />
        <el-button @click="onReset">重置</el-button>
        <el-button type="primary" @click="onSearch">查询</el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" row-key="id">
        <el-table-column label="用户" min-width="230">
          <template #default="{ row }">
            <div class="ucell">
              <img
                v-if="row.avatar"
                :src="row.avatar"
                class="ucell__avatar"
                alt=""
                @error="hideBrokenImage"
              />
              <div v-else class="ucell__avatar ucell__avatar--ph">{{ initialOf(row) }}</div>
              <div class="ucell__body">
                <div class="ucell__name">
                  {{ row.nickname || row.username }}
                  <el-tag v-if="row.role === 1" size="small" type="warning" effect="plain">
                    管理员
                  </el-tag>
                </div>
                <!-- 昵称自 V24 起与用户名全库统一，主标题已是账号名，副标题不再重复展示 username -->
                <div class="ucell__sub">ID {{ row.id }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="手机号" width="140">
          <template #default="{ row }">
            <span class="um__mono">{{ row.phone || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="88">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'danger' : 'success'" size="small" effect="plain">
              {{ row.status === 1 ? '禁用' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="下单 / 已付款" width="128" align="right">
          <template #default="{ row }">
            <!-- 下单多、付款少 = 占库存或刷单的可疑信号，标红提示 -->
            <span :class="{ 'um__suspicious': row.orderCount > 0 && row.paidOrderCount === 0 }">
              {{ row.orderCount }} / {{ row.paidOrderCount }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="累计消费" width="118" align="right">
          <template #default="{ row }">¥{{ formatPrice(row.paidAmount) }}</template>
        </el-table-column>

        <el-table-column label="最近下单" width="150">
          <template #default="{ row }">{{ formatTime(row.lastOrderAt) }}</template>
        </el-table-column>

        <el-table-column label="注册时间" width="150">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>

        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
            <el-button
              v-if="canOperate(row)"
              link
              :type="row.status === 1 ? 'success' : 'danger'"
              @click="onToggle(row)"
            >
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="没有匹配的用户" :image-size="80" />
        </template>
      </el-table>

      <div class="um__pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :page-sizes="[20, 50, 100]"
          @current-change="load"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>

    <el-drawer v-model="drawer" title="用户详情" size="560px">
      <div v-loading="detailLoading" class="ud">
        <template v-if="detail">
          <div class="ud__head">
            <img
              v-if="detail.avatar"
              :src="detail.avatar"
              class="ucell__avatar ucell__avatar--lg"
              alt=""
              @error="hideBrokenImage"
            />
            <div v-else class="ucell__avatar ucell__avatar--lg ucell__avatar--ph">
              {{ initialOf(detail) }}
            </div>
            <div class="ud__ident">
              <div class="ud__name">
                {{ detail.nickname || detail.username }}
                <el-tag v-if="detail.role === 1" size="small" type="warning" effect="plain">
                  管理员
                </el-tag>
                <el-tag :type="detail.status === 1 ? 'danger' : 'success'" size="small" effect="plain">
                  {{ detail.status === 1 ? '已禁用' : '正常' }}
                </el-tag>
              </div>
              <!-- 与列表保持同一口径：主标题给名字，副标题只给内部编号 -->
              <div class="ud__sub">ID {{ detail.id }}</div>
            </div>
          </div>

          <el-descriptions :column="2" border size="small" class="ud__desc">
            <el-descriptions-item label="手机号">
              <span class="um__mono">{{ detail.phone || '—' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="性别">
              {{ GENDER_TEXT[detail.gender] ?? '未知' }}
            </el-descriptions-item>
            <el-descriptions-item label="注册时间" :span="2">
              {{ formatTime(detail.createdAt) }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="ud__block">
            <div class="ud__title">
              账号角色
              <span class="ud__hint">变更后该用户会被强制下线，需重新登录</span>
            </div>
            <div class="ud__role">
              <el-tag :type="detail.role === 1 ? 'warning' : 'info'" effect="plain">
                {{ detail.role === 1 ? '管理员' : '普通用户' }}
              </el-tag>
              <el-button
                v-if="canChangeRole(detail)"
                size="small"
                :type="detail.role === 1 ? 'danger' : 'primary'"
                plain
                @click="onChangeRole(detail)"
              >
                {{ detail.role === 1 ? '降为普通用户' : '设为管理员' }}
              </el-button>
            </div>
          </div>

          <div class="ud__stats">
            <div class="stat">
              <div class="stat__k">下单</div>
              <div class="stat__v">{{ detail.orderCount }}</div>
            </div>
            <div class="stat">
              <div class="stat__k">已付款</div>
              <div class="stat__v">{{ detail.paidOrderCount }}</div>
            </div>
            <div class="stat">
              <div class="stat__k">累计消费</div>
              <div class="stat__v">¥{{ formatPrice(detail.paidAmount) }}</div>
            </div>
            <div class="stat" :class="{ 'stat--warn': detail.refundCount > 0 }">
              <div class="stat__k">退款次数</div>
              <div class="stat__v">{{ detail.refundCount }}</div>
            </div>
          </div>

          <div class="ud__block">
            <div class="ud__title">最近订单</div>
            <el-table :data="detail.recentOrders" size="small" empty-text="还没有下过单">
              <el-table-column prop="orderNo" label="订单号" min-width="160" />
              <el-table-column label="状态" width="92">
                <template #default="{ row }">
                  <el-tag :type="orderTagType(row.status)" size="small" effect="plain">
                    {{ row.statusLabel }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="实付" width="86" align="right">
                <template #default="{ row }">¥{{ formatPrice(row.payAmount) }}</template>
              </el-table-column>
              <el-table-column label="下单时间" width="128">
                <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
              </el-table-column>
            </el-table>
          </div>

          <div class="ud__block">
            <div class="ud__title">
              收货地址
              <span class="ud__hint">只读，地址由用户自己维护</span>
            </div>
            <div v-if="detail.addresses.length" class="addr">
              <div v-for="a in detail.addresses" :key="a.id" class="addr__item">
                <div class="addr__top">
                  <b>{{ a.receiver }}</b>
                  <span class="um__mono">{{ a.phone || '—' }}</span>
                  <el-tag v-if="a.isDefault === 1" size="small" type="primary" effect="plain">
                    默认
                  </el-tag>
                </div>
                <div class="addr__full">{{ a.fullAddress }}</div>
              </div>
            </div>
            <el-empty v-else description="没有收货地址" :image-size="56" />
          </div>
        </template>
      </div>

      <template #footer>
        <el-button @click="drawer = false">关闭</el-button>
        <el-button
          v-if="detail && canOperate(detail)"
          :type="detail.status === 1 ? 'success' : 'danger'"
          @click="onToggle(detail)"
        >
          {{ detail.status === 1 ? '启用账号' : '禁用账号' }}
        </el-button>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.um__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}
.um__summary {
  font-size: 13.5px;
  color: var(--el-text-color-regular);
}
.um__summary b {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.um__tip {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
.um__filter {
  margin-bottom: 14px;
}
.um__row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.um__search {
  width: 240px;
}
.um__status {
  width: 130px;
}
.um__date {
  width: 250px;
}
.um__spacer {
  flex: 1;
}
.um__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
.um__mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12.5px;
  color: var(--el-text-color-regular);
}
/* 下单了但一单没付：占库存 / 刷单的可疑信号 */
.um__suspicious {
  color: var(--el-color-danger);
  font-weight: 600;
}

/* ---------------- 用户单元格 ---------------- */
.ucell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ucell__avatar {
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  border-radius: 50%;
  object-fit: cover;
  background: var(--el-fill-color-light);
}
.ucell__avatar--ph {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}
.ucell__avatar--lg {
  width: 46px;
  height: 46px;
  flex: 0 0 46px;
  font-size: 17px;
}
.ucell__body {
  min-width: 0;
}
.ucell__name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13.5px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}
.ucell__sub {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

/* ---------------- 详情抽屉 ---------------- */
.ud__head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.ud__name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
}
.ud__sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
.ud__desc {
  margin-bottom: 14px;
}
.ud__role {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ud__stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-bottom: 18px;
}
.stat {
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
}
.stat--warn {
  background: var(--el-color-danger-light-9);
}
.stat__k {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.stat__v {
  margin-top: 4px;
  font-size: 17px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.stat--warn .stat__v {
  color: var(--el-color-danger);
}
.ud__block {
  margin-bottom: 18px;
}
.ud__title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.ud__hint {
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-placeholder);
}

/* ---------------- 地址 ---------------- */
.addr__item {
  padding: 9px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}
.addr__item + .addr__item {
  margin-top: 8px;
}
.addr__top {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.addr__full {
  margin-top: 3px;
  font-size: 12.5px;
  color: var(--el-text-color-regular);
  line-height: 1.5;
}
</style>
