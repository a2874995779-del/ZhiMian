<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Delete, Key, Link } from '@element-plus/icons-vue'
import {
  clearAiModelSettings,
  getAiModelSettings,
  saveAiModelSettings,
  testAiModelSettings,
} from '../api/ai-model'
import type { AiModelSettings } from '../types/ai-model'

const settings = ref<AiModelSettings | null>(null)
const model = ref('')
const apiKey = ref('')
const loading = ref(true)
const saving = ref(false)
const testing = ref(false)
const clearing = ref(false)
const secureTransport = computed(() => window.location.protocol === 'https:'
  || ['localhost', '127.0.0.1'].includes(window.location.hostname))

async function loadSettings() {
  loading.value = true
  try {
    settings.value = await getAiModelSettings()
    model.value = settings.value.model ?? ''
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!model.value.trim()) {
    ElMessage.warning('请填写模型名称')
    return
  }
  if (!settings.value?.configured && !apiKey.value.trim()) {
    ElMessage.warning('首次配置需要填写 API Key')
    return
  }
  saving.value = true
  try {
    settings.value = await saveAiModelSettings(model.value.trim(), apiKey.value.trim() || undefined)
    model.value = settings.value.model ?? ''
    apiKey.value = ''
    ElMessage.success('模型配置已安全保存')
  } finally {
    saving.value = false
  }
}

async function testConnection() {
  if (!settings.value?.configured && !apiKey.value.trim()) {
    ElMessage.warning('请先填写模型名称和 API Key')
    return
  }
  testing.value = true
  try {
    if (apiKey.value.trim() || model.value.trim() !== settings.value?.model) {
      settings.value = await saveAiModelSettings(model.value.trim(), apiKey.value.trim() || undefined)
      apiKey.value = ''
    }
    await testAiModelSettings()
    ElMessage.success('连接成功，模型可以正常响应')
  } finally {
    testing.value = false
  }
}

async function clearSettings() {
  try {
    await ElMessageBox.confirm('移除配置后，AI 面试和讲解功能将暂停，确定继续吗？', '移除模型配置', {
      confirmButtonText: '移除配置',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  clearing.value = true
  try {
    await clearAiModelSettings()
    apiKey.value = ''
    await loadSettings()
    ElMessage.success('模型配置已移除')
  } finally {
    clearing.value = false
  }
}

onMounted(loadSettings)
</script>

<template>
  <section class="model-settings" v-loading="loading">
    <header class="settings-heading">
      <div>
        <p class="eyebrow zm-prompt">&gt; account / ai-model</p>
        <h2>模型设置</h2>
        <p class="subtitle">使用你自己的模型账户进行面试与 AI 评价。</p>
      </div>
      <span class="connection-state" :class="{ 'connection-state--ready': settings?.configured }">
        <span class="state-dot"></span>
        {{ settings?.configured ? '已配置' : '未配置' }}
      </span>
    </header>

    <el-alert
      v-if="!secureTransport"
      class="transport-alert"
      title="请先启用 HTTPS，再保存或测试 API Key。当前连接未加密，密钥提交已禁用。"
      type="warning"
      :closable="false"
      show-icon
    />

    <div class="settings-layout">
      <section class="settings-section">
        <div class="section-heading">
          <div class="section-icon"><el-icon><Key /></el-icon></div>
          <div>
            <h3>聊天模型</h3>
            <p>{{ settings?.provider ?? 'SiliconFlow 兼容接口' }}</p>
          </div>
        </div>

        <el-form label-position="top" class="model-form" @submit.prevent="save">
          <el-form-item label="模型名称">
            <el-input
              v-model="model"
              placeholder="例如 deepseek-ai/DeepSeek-V3.2"
              maxlength="128"
              autocomplete="off"
            />
          </el-form-item>
          <el-form-item label="API Key">
            <el-input
              v-model="apiKey"
              type="password"
              show-password
              autocomplete="new-password"
              :placeholder="settings?.configured ? `已保存 ${settings.maskedApiKey}，留空表示继续使用` : '粘贴你的 API Key'"
            />
          </el-form-item>
          <div class="form-actions">
            <el-button type="primary" :loading="saving" :disabled="!secureTransport" @click="save">
              <el-icon><Check /></el-icon>
              保存配置
            </el-button>
            <el-button :loading="testing" :disabled="!secureTransport" @click="testConnection">
              <el-icon><Link /></el-icon>
              测试连接
            </el-button>
            <el-button v-if="settings?.configured" text type="danger" :loading="clearing" @click="clearSettings">
              <el-icon><Delete /></el-icon>
              移除
            </el-button>
          </div>
        </el-form>
      </section>

      <aside class="privacy-notes">
        <h3>密钥与计费</h3>
        <ul>
          <li>API Key 加密后保存在服务器，不会再次以明文返回。</li>
          <li>面试对话、单题评分、报告和 AI 讲解使用此配置。</li>
          <li>Embedding 向量服务仍由智面站点配置，和聊天模型 Key 分开。</li>
          <li>请求由你的模型账户计费，请妥善保管 Key。</li>
        </ul>
        <p class="https-hint">公开网络使用前，请确认网站使用 HTTPS。</p>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.model-settings {
  width: min(100%, 1040px);
  margin: 0 auto;
  padding: 10px 0 40px;
}

.settings-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding: 12px 2px 28px;
  border-bottom: 1px solid var(--zm-border);
}

.transport-alert { margin-top: 18px; }

.eyebrow {
  margin: 0 0 8px;
  color: var(--zm-accent);
  font-size: 12px;
}

h2, h3, p { margin-top: 0; }
h2 { margin-bottom: 7px; font-size: 24px; }
.subtitle { margin-bottom: 0; color: var(--zm-ink-soft); font-size: 14px; }

.connection-state {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding-top: 8px;
  color: var(--zm-ink-soft);
  font-size: 13px;
  white-space: nowrap;
}

.state-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #a9b0bb;
}

.connection-state--ready .state-dot { background: #23a879; }
.settings-layout { display: grid; grid-template-columns: minmax(0, 1.5fr) minmax(250px, 0.8fr); gap: 44px; padding-top: 28px; }
.settings-section { min-width: 0; }
.section-heading { display: flex; align-items: center; gap: 13px; margin-bottom: 26px; }
.section-icon { display: grid; width: 40px; aspect-ratio: 1; place-items: center; border: 1px solid var(--zm-border); border-radius: 10px; color: var(--zm-accent); }
.section-heading h3, .privacy-notes h3 { margin-bottom: 4px; font-size: 16px; }
.section-heading p { margin-bottom: 0; color: var(--zm-ink-soft); font-size: 13px; }
.model-form { max-width: 600px; }
.form-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin-top: 6px; }
.privacy-notes { align-self: start; padding: 2px 0 0 24px; border-left: 1px solid var(--zm-border); }
.privacy-notes h3 { margin-bottom: 14px; }
.privacy-notes ul { display: grid; gap: 12px; margin: 0; padding-left: 18px; color: var(--zm-ink-soft); font-size: 13px; line-height: 1.65; }
.https-hint { margin: 18px 0 0; color: #b66b19; font-size: 12px; line-height: 1.6; }

@media (max-width: 760px) {
  .model-settings { padding: 4px 2px 28px; }
  .settings-heading { padding-bottom: 20px; }
  .settings-layout { grid-template-columns: 1fr; gap: 30px; padding-top: 22px; }
  .privacy-notes { padding: 20px 0 0; border-left: 0; border-top: 1px solid var(--zm-border); }
}
</style>
