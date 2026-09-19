<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  CircleCheck,
  Close,
  DocumentAdd,
  Files,
  Refresh,
  RefreshRight,
  UploadFilled,
  WarningFilled,
} from '@element-plus/icons-vue'
import {
  fetchKnowledgeDocumentStatus,
  retryKnowledgeDocument,
  submitKnowledgeDocument,
} from '../api/knowledge-document'
import type {
  KnowledgeDocumentImportPayload,
  KnowledgeDocumentStatus,
  KnowledgeDocumentStatusVO,
  TrackedKnowledgeDocument,
} from '../types/knowledge-document'
import { formatDateTime } from '../utils/format'

const STORAGE_KEY = 'zm-rag-document-history'
const POLL_INTERVAL_MS = 2_000
const MAX_TRACKED_DOCUMENTS = 20
const MAX_CONTENT_LENGTH = 2_000_000

const formRef = ref<FormInstance>()
const fileInput = ref<HTMLInputElement>()
const submitting = ref(false)
const refreshing = ref(false)
const busyId = ref<number | null>(null)
const selectedFileName = ref('')
const statuses = ref<Record<string, KnowledgeDocumentStatusVO>>({})
const tracked = ref<TrackedKnowledgeDocument[]>(readTrackedDocuments())

const form = reactive<KnowledgeDocumentImportPayload>({
  title: '',
  originalFilename: '',
  sourceType: 'MARKDOWN',
  content: '',
})

const rules: FormRules<KnowledgeDocumentImportPayload> = {
  title: [
    { required: true, message: '请输入文档标题', trigger: 'blur' },
    { max: 200, message: '标题最多 200 个字符', trigger: 'blur' },
  ],
  originalFilename: [
    { required: true, message: '请选择文件或填写原始文件名', trigger: 'blur' },
    { max: 255, message: '文件名最多 255 个字符', trigger: 'blur' },
  ],
  sourceType: [{ required: true, message: '请选择来源类型', trigger: 'change' }],
  content: [
    { required: true, message: '请输入知识文档内容', trigger: 'blur' },
    { max: MAX_CONTENT_LENGTH, message: '文档内容不能超过 200 万字符', trigger: 'blur' },
  ],
}

const trackedRows = computed(() =>
  tracked.value.map((entry) => ({
    ...entry,
    document: statuses.value[String(entry.id)] ?? null,
  })),
)

const activeCount = computed(() =>
  trackedRows.value.filter(({ document }) =>
    document && (document.status === 0 || document.status === 1),
  ).length,
)

const contentLength = computed(() => form.content.length.toLocaleString('zh-CN'))

let pollTimer: number | undefined

function readTrackedDocuments(): TrackedKnowledgeDocument[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]')
    if (!Array.isArray(parsed)) return []
    return parsed
      .filter((item) => Number.isInteger(item?.id) && typeof item?.submittedAt === 'string')
      .slice(0, MAX_TRACKED_DOCUMENTS)
  }
  catch {
    localStorage.removeItem(STORAGE_KEY)
    return []
  }
}

function persistTrackedDocuments() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(tracked.value))
}

function addTrackedDocument(id: number) {
  tracked.value = [
    { id, submittedAt: new Date().toISOString() },
    ...tracked.value.filter((item) => item.id !== id),
  ].slice(0, MAX_TRACKED_DOCUMENTS)
  persistTrackedDocuments()
}

function statusMeta(status: KnowledgeDocumentStatus | undefined) {
  const definitions = {
    0: { label: '等待处理', type: 'info', progress: 12 },
    1: { label: '向量化中', type: 'primary', progress: 62 },
    2: { label: '导入完成', type: 'success', progress: 100 },
    3: { label: '导入失败', type: 'danger', progress: 100 },
    4: { label: '删除中', type: 'warning', progress: 86 },
  } as const
  return status === undefined
    ? { label: '正在读取', type: 'info' as const, progress: 0 }
    : definitions[status]
}

function progressStatus(status: KnowledgeDocumentStatus | undefined) {
  if (status === 2) return 'success'
  if (status === 3) return 'exception'
  return undefined
}

function openFilePicker() {
  fileInput.value?.click()
}

async function handleFileInput(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) await loadFile(file)
}

async function handleDrop(event: DragEvent) {
  const file = event.dataTransfer?.files?.[0]
  if (file) await loadFile(file)
}

async function loadFile(file: File) {
  const extension = file.name.split('.').pop()?.toLowerCase()
  if (!extension || !['md', 'markdown', 'txt'].includes(extension)) {
    ElMessage.warning('请选择 Markdown 或 TXT 文件')
    return
  }

  const content = await file.text()
  if (!content.trim()) {
    ElMessage.warning('文件内容为空')
    return
  }
  if (content.length > MAX_CONTENT_LENGTH) {
    ElMessage.warning('文档内容不能超过 200 万字符')
    return
  }

  form.originalFilename = file.name
  form.sourceType = extension === 'txt' ? 'TEXT' : 'MARKDOWN'
  form.content = content
  if (!form.title.trim()) {
    form.title = file.name.replace(/\.(md|markdown|txt)$/i, '')
  }
  selectedFileName.value = file.name
  await formRef.value?.clearValidate()
}

function clearSelectedFile() {
  selectedFileName.value = ''
  form.originalFilename = ''
  form.content = ''
  if (fileInput.value) fileInput.value.value = ''
}

async function submitDocument() {
  if (!formRef.value) return
  await formRef.value.validate()

  submitting.value = true
  try {
    const documentId = await submitKnowledgeDocument({
      title: form.title.trim(),
      originalFilename: form.originalFilename.trim(),
      sourceType: form.sourceType,
      content: form.content,
    })
    addTrackedDocument(documentId)
    statuses.value[String(documentId)] = {
      id: documentId,
      title: form.title.trim(),
      status: 1,
      chunkCount: 0,
      embeddingModel: null,
      vectorDimension: null,
      errorMessage: null,
      updateTime: new Date().toISOString(),
    }
    ElMessage.success(`导入任务已提交，文档 ID：${documentId}`)
    resetForm()
    window.setTimeout(() => refreshDocument(documentId), 600)
  }
  finally {
    submitting.value = false
  }
}

function resetForm() {
  form.title = ''
  form.originalFilename = ''
  form.sourceType = 'MARKDOWN'
  form.content = ''
  selectedFileName.value = ''
  if (fileInput.value) fileInput.value.value = ''
  formRef.value?.clearValidate()
}

async function refreshDocument(id: number) {
  try {
    const document = await fetchKnowledgeDocumentStatus(id)
    statuses.value[String(id)] = document
  }
  catch {
    // 全局 HTTP 拦截器已经展示错误，这里保留历史记录供用户手动处理。
  }
}

async function refreshAll(showMessage = false) {
  if (refreshing.value || tracked.value.length === 0) return
  refreshing.value = true
  try {
    await Promise.allSettled(tracked.value.map((item) => refreshDocument(item.id)))
    if (showMessage) ElMessage.success('状态已刷新')
  }
  finally {
    refreshing.value = false
  }
}

async function retryDocument(id: number) {
  busyId.value = id
  try {
    await retryKnowledgeDocument(id)
    const current = statuses.value[String(id)]
    if (current) {
      statuses.value[String(id)] = {
        ...current,
        status: 1,
        errorMessage: null,
        updateTime: new Date().toISOString(),
      }
    }
    ElMessage.success(`文档 #${id} 已重新提交`)
    window.setTimeout(() => refreshDocument(id), 600)
  }
  finally {
    busyId.value = null
  }
}

function removeTrackedDocument(id: number) {
  tracked.value = tracked.value.filter((item) => item.id !== id)
  delete statuses.value[String(id)]
  persistTrackedDocuments()
}

async function clearHistory() {
  if (tracked.value.length === 0) return
  try {
    await ElMessageBox.confirm(
      '这只会清除此浏览器中的展示记录，不会删除服务器知识文档。',
      '清除本地记录',
      {
        type: 'warning',
        confirmButtonText: '清除',
        cancelButtonText: '取消',
      },
    )
  }
  catch {
    return
  }
  tracked.value = []
  statuses.value = {}
  persistTrackedDocuments()
}

onMounted(() => {
  refreshAll()
  pollTimer = window.setInterval(() => {
    if (activeCount.value > 0) refreshAll()
  }, POLL_INTERVAL_MS)
})

onBeforeUnmount(() => {
  if (pollTimer !== undefined) window.clearInterval(pollTimer)
})
</script>

<template>
  <div class="knowledge-page">
    <header class="page-heading">
      <div>
        <p class="page-eyebrow zm-prompt">&gt; knowledge_ingestion</p>
        <h1>知识库管理</h1>
        <p class="page-description">导入经过审核的技术资料，并跟踪切片与向量化状态。</p>
      </div>
      <div class="heading-mark" aria-hidden="true">
        <el-icon><Files /></el-icon>
      </div>
    </header>

    <div class="workspace-grid">
      <section class="import-panel zm-glass" aria-labelledby="import-title">
        <div class="panel-heading">
          <div>
            <span class="section-index">01</span>
            <h2 id="import-title">导入知识文档</h2>
          </div>
          <span class="file-support">MD / TXT</span>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.prevent="submitDocument"
        >
          <div
            class="file-drop"
            :class="{ 'file-drop--selected': selectedFileName }"
            @click="openFilePicker"
            @keydown.enter="openFilePicker"
            @keydown.space.prevent="openFilePicker"
            @dragover.prevent
            @drop.prevent="handleDrop"
            role="button"
            tabindex="0"
          >
            <input
              ref="fileInput"
              class="file-input"
              type="file"
              accept=".md,.markdown,.txt,text/plain,text/markdown"
              @change="handleFileInput"
            />
            <el-icon class="file-icon"><UploadFilled /></el-icon>
            <div class="file-copy">
              <strong>{{ selectedFileName || '选择或拖入文档' }}</strong>
              <span>{{ selectedFileName ? '内容已读取，可继续编辑' : '支持 Markdown 与纯文本' }}</span>
            </div>
            <el-button
              v-if="selectedFileName"
              class="clear-file"
              text
              circle
              :icon="Close"
              aria-label="移除已选文件"
              @click.stop="clearSelectedFile"
            />
          </div>

          <div class="form-grid">
            <el-form-item label="文档标题" prop="title">
              <el-input v-model="form.title" maxlength="200" placeholder="例如：Java 并发编程手册" />
            </el-form-item>
            <el-form-item label="来源类型" prop="sourceType">
              <el-segmented
                v-model="form.sourceType"
                :options="[
                  { label: 'Markdown', value: 'MARKDOWN' },
                  { label: '纯文本', value: 'TEXT' },
                ]"
              />
            </el-form-item>
          </div>

          <el-form-item label="原始文件名" prop="originalFilename">
            <el-input v-model="form.originalFilename" maxlength="255" placeholder="例如：java-concurrency.md" />
          </el-form-item>

          <el-form-item label="文档内容" prop="content">
            <el-input
              v-model="form.content"
              type="textarea"
              :rows="15"
              resize="vertical"
              placeholder="# Java 并发&#10;&#10;在这里粘贴知识文档内容……"
            />
          </el-form-item>

          <div class="form-footer">
            <span class="content-count">{{ contentLength }} / 2,000,000 字符</span>
            <div class="form-actions">
              <el-button :icon="RefreshRight" @click="resetForm">重置</el-button>
              <el-button
                native-type="submit"
                type="primary"
                :icon="DocumentAdd"
                :loading="submitting"
              >
                提交导入
              </el-button>
            </div>
          </div>
        </el-form>
      </section>

      <section class="activity-panel zm-glass" aria-labelledby="activity-title">
        <div class="panel-heading activity-heading">
          <div>
            <span class="section-index">02</span>
            <h2 id="activity-title">最近导入</h2>
          </div>
          <div class="activity-actions">
            <el-button
              text
              circle
              :icon="Refresh"
              :loading="refreshing"
              aria-label="刷新导入状态"
              title="刷新导入状态"
              @click="refreshAll(true)"
            />
            <el-button
              v-if="trackedRows.length"
              text
              circle
              :icon="Close"
              aria-label="清除本地记录"
              title="清除本地记录"
              @click="clearHistory"
            />
          </div>
        </div>

        <div v-if="trackedRows.length === 0" class="empty-state">
          <el-icon><DocumentAdd /></el-icon>
          <strong>暂无导入任务</strong>
          <span>提交后的任务会显示在这里</span>
        </div>

        <div v-else class="activity-list">
          <article v-for="row in trackedRows" :key="row.id" class="activity-row">
            <div class="activity-row-top">
              <div class="document-identity">
                <span
                  class="status-icon"
                  :class="`status-icon--${statusMeta(row.document?.status).type}`"
                >
                  <el-icon v-if="row.document?.status === 2"><CircleCheck /></el-icon>
                  <el-icon v-else-if="row.document?.status === 3"><WarningFilled /></el-icon>
                  <span v-else class="status-pulse"></span>
                </span>
                <div>
                  <strong>{{ row.document?.title || `知识文档 #${row.id}` }}</strong>
                  <span>#{{ row.id }} · {{ formatDateTime(row.document?.updateTime || row.submittedAt) }}</span>
                </div>
              </div>
              <el-tag :type="statusMeta(row.document?.status).type" effect="light">
                {{ statusMeta(row.document?.status).label }}
              </el-tag>
            </div>

            <el-progress
              class="document-progress"
              :percentage="statusMeta(row.document?.status).progress"
              :status="progressStatus(row.document?.status)"
              :show-text="false"
            />

            <div class="document-facts">
              <span><b>{{ row.document?.chunkCount ?? 0 }}</b> 个切片</span>
              <span><b>{{ row.document?.vectorDimension ?? '—' }}</b> 维向量</span>
              <span>{{ row.document?.embeddingModel || '等待模型信息' }}</span>
            </div>

            <p v-if="row.document?.errorMessage" class="error-message">
              {{ row.document.errorMessage }}
            </p>

            <div class="row-actions">
              <el-button
                v-if="row.document?.status === 3"
                type="primary"
                plain
                size="small"
                :icon="RefreshRight"
                :loading="busyId === row.id"
                @click="retryDocument(row.id)"
              >
                重试
              </el-button>
              <el-button
                text
                size="small"
                :icon="Close"
                @click="removeTrackedDocument(row.id)"
              >
                移出列表
              </el-button>
            </div>
          </article>
        </div>

        <footer class="activity-footer">
          <span class="connection-dot" :class="{ 'connection-dot--busy': activeCount > 0 }"></span>
          <span>{{ activeCount > 0 ? `${activeCount} 个任务处理中` : '当前没有运行中的任务' }}</span>
        </footer>
      </section>
    </div>
  </div>
</template>

<style scoped>
.knowledge-page {
  padding-top: 8px;
}

.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 22px;
}

.page-eyebrow {
  margin-bottom: 8px;
  font-size: 12px;
}

.page-heading h1 {
  font-size: 30px;
}

.page-description {
  margin-top: 8px;
  color: var(--zm-ink-soft);
  font-size: 14px;
}

.heading-mark {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  border: 1px solid var(--zm-accent);
  border-radius: var(--zm-radius-md);
  color: var(--zm-accent);
  background: var(--zm-accent-soft);
  font-size: 22px;
}

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(330px, 0.75fr);
  align-items: start;
  gap: 18px;
}

.import-panel,
.activity-panel {
  padding: 22px;
}

.activity-panel {
  position: sticky;
  top: 18px;
  padding-bottom: 0;
  overflow: hidden;
}

.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.panel-heading > div:first-child {
  display: flex;
  align-items: center;
  gap: 10px;
}

.panel-heading h2 {
  font-size: 18px;
}

.section-index,
.file-support {
  font-family: var(--zm-font-mono);
  font-size: 11px;
}

.section-index {
  color: var(--zm-accent);
  font-weight: 700;
}

.file-support {
  padding: 4px 8px;
  border: 1px solid var(--zm-border);
  border-radius: var(--zm-radius-sm);
  color: var(--zm-ink-faint);
  background: var(--zm-bg-elevated);
}

.file-drop {
  position: relative;
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 82px;
  margin-bottom: 20px;
  padding: 16px;
  border: 1px dashed var(--zm-border-strong);
  border-radius: var(--zm-radius-md);
  background: rgba(10, 132, 255, 0.025);
  cursor: pointer;
  transition: border-color var(--zm-dur-fast) var(--zm-ease), background var(--zm-dur-fast) var(--zm-ease);
}

.file-drop:hover,
.file-drop:focus-visible,
.file-drop--selected {
  border-color: var(--zm-accent);
  background: var(--zm-accent-soft);
}

.file-input {
  display: none;
}

.file-icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: var(--zm-radius-sm);
  color: var(--zm-accent);
  background: var(--zm-bg-elevated);
  font-size: 20px;
  box-shadow: var(--zm-shadow-sm);
}

.file-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 3px;
}

.file-copy strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-copy span {
  color: var(--zm-ink-faint);
  font-size: 12px;
}

.clear-file {
  flex-shrink: 0;
}

.form-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 210px;
  gap: 14px;
}

.form-grid :deep(.el-segmented) {
  width: 100%;
}

.form-footer,
.form-actions,
.activity-actions,
.row-actions {
  display: flex;
  align-items: center;
}

.form-footer {
  justify-content: space-between;
  gap: 16px;
}

.content-count {
  color: var(--zm-ink-faint);
  font-family: var(--zm-font-mono);
  font-size: 11px;
}

.form-actions,
.activity-actions,
.row-actions {
  gap: 8px;
}

.activity-heading {
  margin-bottom: 8px;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 310px;
  flex-direction: column;
  gap: 8px;
  color: var(--zm-ink-faint);
  text-align: center;
}

.empty-state .el-icon {
  margin-bottom: 4px;
  color: var(--zm-accent);
  font-size: 30px;
}

.empty-state strong {
  color: var(--zm-ink-soft);
}

.empty-state span {
  font-size: 12px;
}

.activity-list {
  max-height: 610px;
  overflow-y: auto;
}

.activity-row {
  padding: 18px 0;
  border-bottom: 1px solid var(--zm-border);
}

.activity-row:last-child {
  border-bottom: none;
}

.activity-row-top,
.document-identity,
.document-facts,
.activity-footer {
  display: flex;
  align-items: center;
}

.activity-row-top {
  justify-content: space-between;
  gap: 12px;
}

.document-identity {
  min-width: 0;
  gap: 10px;
}

.document-identity > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.document-identity strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-identity span {
  color: var(--zm-ink-faint);
  font-family: var(--zm-font-mono);
  font-size: 10px;
}

.status-icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--zm-accent-soft);
  color: var(--zm-accent);
  font-size: 17px;
}

.status-icon--success {
  color: var(--zm-green);
  background: rgba(52, 199, 89, 0.1);
}

.status-icon--danger {
  color: var(--zm-red);
  background: rgba(255, 59, 48, 0.1);
}

.status-pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 0 5px rgba(10, 132, 255, 0.1);
}

.document-progress {
  margin: 14px 0 10px;
}

.document-facts {
  flex-wrap: wrap;
  gap: 5px 12px;
  color: var(--zm-ink-faint);
  font-size: 11px;
}

.document-facts b {
  color: var(--zm-ink-soft);
  font-family: var(--zm-font-mono);
}

.error-message {
  margin-top: 10px;
  padding: 9px 10px;
  border-left: 2px solid var(--zm-red);
  color: var(--zm-red);
  background: rgba(255, 59, 48, 0.06);
  font-size: 12px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.row-actions {
  justify-content: flex-end;
  margin-top: 10px;
}

.activity-footer {
  gap: 7px;
  margin: 0 -22px;
  padding: 12px 22px;
  border-top: 1px solid var(--zm-border);
  color: var(--zm-ink-faint);
  background: rgba(10, 132, 255, 0.025);
  font-size: 11px;
}

.connection-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--zm-green);
}

.connection-dot--busy {
  background: var(--zm-accent);
  box-shadow: 0 0 0 4px rgba(10, 132, 255, 0.1);
}

@media (max-width: 1100px) {
  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .activity-panel {
    position: static;
  }

  .activity-list {
    max-height: none;
  }
}

@media (max-width: 620px) {
  .page-heading h1 {
    font-size: 26px;
  }

  .heading-mark {
    display: none;
  }

  .import-panel,
  .activity-panel {
    padding: 16px;
  }

  .form-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .form-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .form-actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }

  .activity-footer {
    margin: 0 -16px;
    padding: 12px 16px;
  }

  .document-facts {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
