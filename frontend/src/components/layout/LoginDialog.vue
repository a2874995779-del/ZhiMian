<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../../stores/auth'

const visible = defineModel<boolean>({ default: false })

const auth = useAuthStore()
const username = ref('')
const password = ref('')
const loading = ref(false)

async function submit() {
  if (!username.value.trim() || !password.value.trim()) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(username.value.trim(), password.value)
    ElMessage.success(`欢迎回来,${auth.user?.nickname ?? auth.user?.username}`)
    visible.value = false
    password.value = ''
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="登录智面" width="360px" align-center destroy-on-close>
    <div class="login-form">
      <p class="zm-prompt hint">&gt; 需要一个已存在的账号(题库任务里用过的账号就行)</p>
      <el-input v-model="username" placeholder="账号" size="large" @keyup.enter="submit" />
      <el-input v-model="password" type="password" placeholder="密码" size="large" show-password @keyup.enter="submit" />
      <el-button type="primary" size="large" :loading="loading" class="submit-btn" @click="submit">
        登录
      </el-button>
    </div>
  </el-dialog>
</template>

<style scoped>
.login-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.hint {
  font-size: 12px;
  color: var(--zm-ink-faint);
  margin-bottom: 2px;
}

.submit-btn {
  width: 100%;
  margin-top: 4px;
}
</style>
