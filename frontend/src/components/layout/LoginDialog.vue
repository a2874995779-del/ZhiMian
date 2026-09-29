<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../../stores/auth'

const visible = defineModel<boolean>({ default: false })

const auth = useAuthStore()
const mode = ref<'login' | 'register'>('login')
const username = ref('')
const password = ref('')
const nickname = ref('')
const confirmPassword = ref('')
const loading = ref(false)

watch(visible, (isVisible) => {
  if (!isVisible) {
    mode.value = 'login'
    password.value = ''
    confirmPassword.value = ''
  }
})

async function submit() {
  const normalizedUsername = username.value.trim()
  if (!normalizedUsername || !password.value) {
    ElMessage.warning('请输入账号和密码')
    return
  }

  if (mode.value === 'register') {
    if (!/^[a-zA-Z0-9_]{4,32}$/.test(normalizedUsername)) {
      ElMessage.warning('账号需为 4–32 位字母、数字或下划线')
      return
    }
    if (password.value.length < 8 || password.value.length > 24) {
      ElMessage.warning('密码长度需为 8–24 位')
      return
    }
    if (nickname.value.trim().length > 16) {
      ElMessage.warning('昵称不能超过 16 个字符')
      return
    }
    if (password.value !== confirmPassword.value) {
      ElMessage.warning('两次输入的密码不一致')
      return
    }
  }

  loading.value = true
  try {
    if (mode.value === 'register') {
      await auth.register(normalizedUsername, password.value, nickname.value.trim() || undefined)
      ElMessage.success('账号创建成功，请先到“模型设置”填写自己的模型信息')
    } else {
      await auth.login(normalizedUsername, password.value)
      ElMessage.success(`欢迎回来，${auth.user?.nickname ?? auth.user?.username}`)
    }
    visible.value = false
    password.value = ''
    confirmPassword.value = ''
  } catch {
    // The HTTP interceptor displays the server's error message.
  } finally {
    loading.value = false
  }
}

function switchMode() {
  mode.value = mode.value === 'login' ? 'register' : 'login'
  password.value = ''
  confirmPassword.value = ''
}
</script>

<template>
  <el-dialog v-model="visible" :title="mode === 'login' ? '登录智面' : '创建智面账号'" width="min(380px, calc(100vw - 32px))" align-center destroy-on-close>
    <div class="login-form">
      <el-input v-model="username" autocomplete="username" placeholder="账号" size="large" maxlength="32" @keyup.enter="submit" />
      <el-input
        v-if="mode === 'register'"
        v-model="nickname"
        autocomplete="nickname"
        placeholder="昵称（选填）"
        size="large"
        maxlength="16"
        @keyup.enter="submit"
      />
      <el-input v-model="password" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" type="password" placeholder="密码" size="large" show-password @keyup.enter="submit" />
      <el-input
        v-if="mode === 'register'"
        v-model="confirmPassword"
        autocomplete="new-password"
        type="password"
        placeholder="确认密码"
        size="large"
        show-password
        @keyup.enter="submit"
      />
      <el-button type="primary" size="large" :loading="loading" class="submit-btn" @click="submit">
        {{ mode === 'login' ? '登录' : '注册并登录' }}
      </el-button>
      <div class="mode-switch">
        <span>{{ mode === 'login' ? '还没有账号？' : '已有账号？' }}</span>
        <el-button link type="primary" :disabled="loading" @click="switchMode">
          {{ mode === 'login' ? '立即注册' : '返回登录' }}
        </el-button>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
.login-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.submit-btn {
  width: 100%;
  margin-top: 4px;
}

.mode-switch {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: var(--zm-ink-faint);
  font-size: 13px;
}
</style>
