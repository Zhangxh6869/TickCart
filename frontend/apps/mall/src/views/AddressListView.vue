<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import type { UserAddress } from '@tickcart/shared-types'
import PageNavBar from '@/components/PageNavBar.vue'
import * as addressApi from '@/api/address'

const router = useRouter()

const list = ref<UserAddress[]>([])
const loading = ref(false)

const showForm = ref(false)
const saving = ref(false)
/** null 表示新增，非空表示编辑该 ID */
const editingId = ref<number | null>(null)
/** 被编辑地址原本是否为默认（默认地址不允许取消默认，只能转移） */
const editingIsDefault = ref(false)

const form = reactive({
  receiver: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  isDefault: false,
})

async function load() {
  loading.value = true
  try {
    list.value = await addressApi.fetchAddresses()
  } catch {
    // 错误提示由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}

/** 拼装完整地址用于展示。 */
function fullAddress(address: UserAddress) {
  return `${address.province}${address.city}${address.district}${address.detail}`
}

function resetForm() {
  form.receiver = ''
  form.phone = ''
  form.province = ''
  form.city = ''
  form.district = ''
  form.detail = ''
  form.isDefault = false
}

function openCreate() {
  editingId.value = null
  editingIsDefault.value = false
  resetForm()
  showForm.value = true
}

function openEdit(address: UserAddress) {
  editingId.value = address.id
  editingIsDefault.value = address.isDefault === 1
  form.receiver = address.receiver
  form.phone = address.phone
  form.province = address.province
  form.city = address.city
  form.district = address.district
  form.detail = address.detail
  form.isDefault = address.isDefault === 1
  showForm.value = true
}

/** 前端预校验，返回错误信息；通过则返回 null。 */
function validate(): string | null {
  if (!form.receiver.trim()) return '请输入收货人姓名'
  if (!/^1[3-9]\d{9}$/.test(form.phone.trim())) return '请输入正确的手机号'
  if (!form.province.trim()) return '请输入省份'
  if (!form.city.trim()) return '请输入城市'
  if (!form.district.trim()) return '请输入区县'
  if (!form.detail.trim()) return '请输入详细地址'
  return null
}

async function onSubmit() {
  const error = validate()
  if (error) {
    showToast(error)
    return
  }

  const payload = {
    receiver: form.receiver.trim(),
    phone: form.phone.trim(),
    province: form.province.trim(),
    city: form.city.trim(),
    district: form.district.trim(),
    detail: form.detail.trim(),
    isDefault: form.isDefault ? 1 : 0,
  }

  saving.value = true
  try {
    if (editingId.value === null) {
      await addressApi.createAddress(payload)
      showToast('地址已新增')
    } else {
      await addressApi.updateAddress(editingId.value, payload)
      showToast('地址已保存')
    }
    showForm.value = false
    await load()
  } catch {
    // 错误提示由请求拦截器统一处理
  } finally {
    saving.value = false
  }
}

async function onSetDefault(address: UserAddress) {
  if (address.isDefault === 1) return
  try {
    await addressApi.setDefaultAddress(address.id)
    showToast('已设为默认地址')
    await load()
  } catch {
    // 错误提示由请求拦截器统一处理
  }
}

async function onDelete(address: UserAddress) {
  try {
    await showConfirmDialog({
      title: '删除地址',
      message: `确认删除「${address.receiver}」的收货地址？`,
    })
  } catch {
    return // 用户取消
  }
  try {
    await addressApi.deleteAddress(address.id)
    showToast('地址已删除')
    await load()
  } catch {
    // 错误提示由请求拦截器统一处理
  }
}

onMounted(load)
</script>

<template>
  <div class="address">
    <PageNavBar title="收货地址" @click-left="router.back()" />

    <van-empty v-if="!loading && list.length === 0" description="还没有收货地址" />

    <div v-else class="address__list">
      <van-swipe-cell v-for="item in list" :key="item.id">
        <div class="address__item" @click="openEdit(item)">
          <div class="address__head">
            <span class="address__receiver">{{ item.receiver }}</span>
            <span class="address__phone">{{ item.phone }}</span>
            <van-tag v-if="item.isDefault === 1" type="danger" plain>默认</van-tag>
          </div>
          <div class="address__detail">{{ fullAddress(item) }}</div>
          <div class="address__actions">
            <span
              class="address__action"
              :class="{ 'address__action--muted': item.isDefault === 1 }"
              @click.stop="onSetDefault(item)"
            >
              {{ item.isDefault === 1 ? '当前默认' : '设为默认' }}
            </span>
            <span class="address__action" @click.stop="openEdit(item)">编辑</span>
          </div>
        </div>
        <template #right>
          <van-button square type="danger" text="删除" class="address__delete" @click="onDelete(item)" />
        </template>
      </van-swipe-cell>
    </div>

    <div class="address__footer">
      <van-button block round type="primary" @click="openCreate">新增收货地址</van-button>
    </div>

    <van-popup v-model:show="showForm" position="bottom" round :style="{ maxHeight: '88%' }">
      <div class="address-form">
        <div class="address-form__title">{{ editingId === null ? '新增收货地址' : '编辑收货地址' }}</div>

        <van-cell-group inset>
          <van-field v-model="form.receiver" label="收货人" placeholder="请输入收货人姓名" maxlength="50" />
          <van-field v-model="form.phone" label="手机号" type="tel" placeholder="请输入手机号" maxlength="11" />
          <van-field v-model="form.province" label="省份" placeholder="如 广东省" maxlength="50" />
          <van-field v-model="form.city" label="城市" placeholder="如 深圳市" maxlength="50" />
          <van-field v-model="form.district" label="区县" placeholder="如 南山区" maxlength="50" />
          <van-field
            v-model="form.detail"
            label="详细地址"
            type="textarea"
            rows="2"
            autosize
            placeholder="街道、门牌号等"
            maxlength="255"
            show-word-limit
          />
          <van-cell title="设为默认地址">
            <template #right-icon>
              <van-switch
                v-model="form.isDefault"
                size="20"
                :disabled="editingIsDefault"
              />
            </template>
          </van-cell>
        </van-cell-group>

        <div v-if="editingIsDefault" class="address-form__hint">
          默认地址不可取消，可在其它地址上点击「设为默认」转移
        </div>

        <div class="address-form__actions">
          <van-button block round type="primary" :loading="saving" @click="onSubmit">保存</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.address {
  min-height: 100vh;
  padding-bottom: 88px;
  background: var(--van-background-2, #f7f8fa);
}
.address__list {
  padding: 12px 12px 0;
}
.address__item {
  position: relative;
  padding: 14px 14px 10px;
  margin-bottom: 10px;
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
}
.address__head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.address__receiver {
  font-size: 15px;
  font-weight: 500;
  color: #323233;
}
.address__phone {
  font-size: 13px;
  color: #969799;
}
.address__detail {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.5;
  color: #646566;
}
.address__actions {
  display: flex;
  justify-content: flex-end;
  gap: 16px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #f2f3f5;
}
.address__action {
  font-size: 13px;
  color: #1989fa;
}
.address__action--muted {
  color: #c8c9cc;
}
.address__delete {
  height: 100%;
}
.address__footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
  background: #fff;
  box-shadow: 0 -1px 0 rgba(0, 0, 0, 0.06);
}
.address-form {
  padding: 16px 0 calc(16px + env(safe-area-inset-bottom));
}
.address-form__title {
  margin-bottom: 12px;
  text-align: center;
  font-size: 15px;
  font-weight: 500;
  color: #323233;
}
.address-form__hint {
  margin: 10px 20px 0;
  font-size: 12px;
  line-height: 1.5;
  color: #969799;
}
.address-form__actions {
  margin: 16px 16px 0;
}
</style>
