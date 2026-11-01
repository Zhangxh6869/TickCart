<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAdminStore } from '@/stores/admin'
import { fetchCaptcha, type CaptchaChallenge } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const adminStore = useAdminStore()

const loading = ref(false)
/** 登录账号为手机号（与前台统一），默认填种子管理员 */
const form = reactive({ phone: '13800000000', password: 'admin123' })

/** 人机验证挑战；非空即表示当前处于「验证升级」态 */
const captcha = ref<CaptchaChallenge | null>(null)
const captchaCode = ref('')

const PHONE_RE = /^1[3-9]\d{9}$/

/** 从 axios 错误里取出后端约定的 code / message / data */
function errorBody(e: unknown): { code?: number; message?: string; data?: unknown } {
  const resp = (e as {
    response?: { data?: { code?: number; message?: string; data?: unknown } }
  })?.response
  return resp?.data ?? {}
}

/** 换一张图形码：看不清或过期时点图重取 */
async function refreshCaptcha() {
  try {
    captcha.value = await fetchCaptcha()
    captchaCode.value = ''
  } catch {
    // 刷新失败保留旧图，不打断当前输入
  }
}

async function onSubmit() {
  if (!form.phone || !form.password) {
    ElMessage.warning('请输入手机号和密码')
    return
  }
  if (!PHONE_RE.test(form.phone)) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  // 已经进入验证态就必须把图形码带上，否则后端会再回一次 40101
  if (captcha.value && !captchaCode.value.trim()) {
    ElMessage.warning('请输入图形验证码')
    return
  }

  loading.value = true
  try {
    await adminStore.login(
      form.phone,
      form.password,
      captcha.value ? { id: captcha.value.id, code: captchaCode.value.trim() } : undefined,
    )
    ElMessage.success('登录成功')
    // 没有 redirect 参数时进数据看板（与侧边栏首项、路由默认 redirect 保持一致）
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.replace(redirect)
  } catch (e) {
    const body = errorBody(e)
    if (body.code === 40101) {
      const challenge = body.data as CaptchaChallenge | undefined
      if (challenge?.id) {
        captcha.value = challenge
        captchaCode.value = ''
      }
      ElMessage.warning(body.message || '登录尝试次数较多，请完成安全验证')
      return
    }
    // 其余情况（凭据错误 / 非管理员 / 锁定 / 网络）后端 message 已经说清楚了
    ElMessage.error(body.message || '登录失败，请稍后再试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <el-card class="login__card">
      <div class="login__title">TickCart 管理后台</div>
      <el-form label-position="top" @submit.prevent="onSubmit">
        <el-form-item label="手机号">
          <el-input v-model="form.phone" maxlength="11" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <!-- 失败次数达到阈值后出现：验证通过才继续比对密码 -->
        <el-form-item v-if="captcha" label="图形验证码">
          <div class="login__captcha">
            <el-input
              v-model="captchaCode"
              maxlength="6"
              placeholder="请输入图中字符"
              @keyup.enter="onSubmit"
            />
            <button
              type="button"
              class="login__captcha-img"
              aria-label="刷新图形验证码"
              @click="refreshCaptcha"
              v-html="captcha.svg"
            ></button>
          </div>
        </el-form-item>
        <el-button type="primary" class="login__btn" :loading="loading" @click="onSubmit">
          登录
        </el-button>
      </el-form>
      <div class="login__tip">默认管理员：13800000000 / admin123</div>
    </el-card>
  </div>
</template>

<style scoped>
.login {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1890ff, #001529);
}
.login__card {
  width: 380px;
  padding: 12px 8px;
}
.login__title {
  text-align: center;
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 24px;
}
.login__btn {
  width: 100%;
}
/* 图形码一行：输入框占满剩余宽度，图片固定宽高、点击换一张 */
.login__captcha {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.login__captcha :deep(.el-input) {
  flex: 1;
  min-width: 0;
}
.login__captcha-img {
  flex: 0 0 auto;
  width: 104px;
  height: 40px;
  padding: 0;
  border: none;
  border-radius: 4px;
  background: #f5f7fa;
  overflow: hidden;
  cursor: pointer;
}
.login__captcha-img :deep(svg) {
  display: block;
  width: 100%;
  height: 100%;
}
.login__tip {
  text-align: center;
  color: #909399;
  font-size: 12px;
  margin-top: 16px;
}
</style>
