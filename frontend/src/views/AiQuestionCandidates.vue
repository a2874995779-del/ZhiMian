<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Check, CloseBold, Management, Refresh, Search } from '@element-plus/icons-vue'
import {
  approveAiQuestionCandidate,
  fetchAiQuestionCandidates,
  ignoreAiQuestionCandidate,
} from '../api/ai-question-candidate'
import { fetchCategoryTree, type CategoryNode } from '../api/category'
import { fetchTags, type TagOption } from '../api/tag'
import type {
  AiQuestionApprovePayload,
  AiQuestionCandidate,
  AiQuestionCandidateQuery,
  AiQuestionCandidateStatus,
} from '../types/ai-question-candidate'
import { directionLabel } from '../types/interview'
import { formatDateTime } from '../utils/format'

interface CategoryOption {
  id: number
  label: string
}

interface ReviewForm {
  title: string
  content: string
  answer: string
  difficulty: number | null
  categoryId: number | null
  tagIds: number[]
}

const pageSize = 10
const query = reactive<AiQuestionCandidateQuery>({
  pageNum: 1,
  pageSize,
  status: 1,
})
const records = ref<AiQuestionCandidate[]>([])
const total = ref(0)
const loading = ref(false)
const busyId = ref<number | null>(null)

const categories = ref<CategoryOption[]>([])
const tags = ref<TagOption[]>([])
const categoryNameMap = computed(() =>
  new Map(categories.value.map((category) => [category.id, category.label])),
)
const tagNameMap = computed(() => new Map(tags.value.map((tag) => [tag.id, tag.name])))

const drawerVisible = ref(false)
const activeCandidate = ref<AiQuestionCandidate | null>(null)
const reviewFormRef = ref<FormInstance>()
const reviewForm = reactive<ReviewForm>({
  title: '',
  content: '',
  answer: '',
  difficulty: null,
  categoryId: null,
  tagIds: [],
})
const reviewRules: FormRules<ReviewForm> = {
  title: [
    { required: true, message: '请输入题目标题', trigger: 'blur' },
    { max: 256, message: '标题最多 256 个字符', trigger: 'blur' },
  ],
  answer: [{ required: true, message: '请输入参考答案', trigger: 'blur' }],
  difficulty: [{ required: true, message: '请选择难度', trigger: 'change' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
}

const statusOptions: Array<{ label: string; value: AiQuestionCandidateStatus }> = [
  { label: '补全中', value: 0 },
  { label: '待审核', value: 1 },
  { label: '已收录', value: 2 },
  { label: '已忽略', value: 3 },
  { label: '补全失败', value: 4 },
  { label: '收录中', value: 5 },
]

const directionOptions = [
  { label: 'Java 并发', value: 'java_concurrency' },
  { label: 'JVM', value: 'jvm' },
  { label: 'MySQL', value: 'mysql' },
  { label: 'Redis', value: 'redis' },
  { label: '系统设计', value: 'system_design' },
]

function flattenCategories(nodes: CategoryNode[], parents: string[] = []): CategoryOption[] {
  return nodes.flatMap((node) => {
    const path = [...parents, node.name]
    const current = { id: node.id, label: path.join(' / ') }
    return node.children?.length
      ? [current, ...flattenCategories(node.children, path)]
      : [current]
  })
}

async function loadOptions() {
  const [categoryTree, tagList] = await Promise.all([fetchCategoryTree(), fetchTags()])
  categories.value = flattenCategories(categoryTree)
  tags.value = tagList
}

async function loadCandidates() {
  loading.value = true
  try {
    const page = await fetchAiQuestionCandidates({ ...query })
    records.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  if (query.pageNum === 1) {
    loadCandidates()
  } else {
    query.pageNum = 1
  }
}

function resetFilters() {
  query.status = 1
  query.direction = undefined
  query.keyword = undefined
  applyFilters()
}

function openReview(candidate: AiQuestionCandidate) {
  activeCandidate.value = candidate
  reviewForm.title = candidate.title
  reviewForm.content = ''
  reviewForm.answer = candidate.answer ?? ''
  reviewForm.difficulty = candidate.difficulty ?? 2
  reviewForm.categoryId = candidate.categoryId
  reviewForm.tagIds = [...candidate.tagIds]
  drawerVisible.value = true
}

async function submitApproval() {
  const candidate = activeCandidate.value
  if (!candidate || !reviewFormRef.value) return

  await reviewFormRef.value.validate()
  const payload: AiQuestionApprovePayload = {
    title: reviewForm.title.trim(),
    content: reviewForm.content.trim() || undefined,
    answer: reviewForm.answer.trim(),
    difficulty: reviewForm.difficulty as number,
    categoryId: reviewForm.categoryId as number,
    tagIds: [...reviewForm.tagIds],
  }

  busyId.value = candidate.id
  try {
    const questionId = await approveAiQuestionCandidate(candidate.id, payload)
    ElMessage.success(`已收录到正式题库，题目 ID：${questionId}`)
    drawerVisible.value = false
    await loadCandidates()
  } finally {
    busyId.value = null
  }
}

async function ignoreCandidate(candidate: AiQuestionCandidate) {
  try {
    await ElMessageBox.confirm(
      '忽略后这道候选题不会进入正式题库。',
      '确认忽略候选题',
      {
        type: 'warning',
        confirmButtonText: '忽略',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }

  busyId.value = candidate.id
  try {
    await ignoreAiQuestionCandidate(candidate.id)
    ElMessage.success('候选题已忽略')
    if (activeCandidate.value?.id === candidate.id) {
      drawerVisible.value = false
    }
    await loadCandidates()
  } finally {
    busyId.value = null
  }
}

function canApprove(status: AiQuestionCandidateStatus) {
  return status === 1 || status === 4
}

function canIgnore(status: AiQuestionCandidateStatus) {
  return status === 0 || status === 1 || status === 4
}

function statusLabel(status: AiQuestionCandidateStatus) {
  return statusOptions.find((item) => item.value === status)?.label ?? '未知状态'
}

function statusType(status: AiQuestionCandidateStatus) {
  if (status === 1) return 'warning'
  if (status === 2) return 'success'
  if (status === 3) return 'info'
  if (status === 4) return 'danger'
  return 'primary'
}

function difficultyLabel(difficulty: number | null) {
  return ({ 1: '简单', 2: '中等', 3: '困难' } as Record<number, string>)[difficulty ?? 0] ?? '待补全'
}

function categoryLabel(categoryId: number | null) {
  if (categoryId === null) return '待选择'
  return categoryNameMap.value.get(categoryId) ?? `分类 #${categoryId}`
}

function candidateTagNames(tagIds: number[]) {
  return tagIds.map((id) => tagNameMap.value.get(id) ?? `标签 #${id}`)
}

watch(() => query.pageNum, loadCandidates)

onMounted(() => {
  loadCandidates()
  loadOptions()
})
</script>

<template>
  <div class="candidate-page">
    <header class="page-heading">
      <div>
        <p class="page-eyebrow zm-prompt">&gt; ai_question_review</p>
        <h1>AI 题目审核</h1>
        <p class="page-description">检查面试中自动沉淀的问题，再决定是否进入正式题库。</p>
      </div>
      <div class="heading-mark" aria-hidden="true">
        <el-icon><Management /></el-icon>
      </div>
    </header>

    <section class="filter-bar zm-glass" aria-label="候选题筛选">
      <el-select v-model="query.status" clearable placeholder="全部状态" @change="applyFilters">
        <el-option
          v-for="option in statusOptions"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <el-select v-model="query.direction" clearable placeholder="全部方向" @change="applyFilters">
        <el-option
          v-for="option in directionOptions"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <el-input
        v-model="query.keyword"
        clearable
        placeholder="搜索候选题标题"
        @keyup.enter="applyFilters"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button type="primary" :icon="Search" @click="applyFilters">搜索</el-button>
      <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
    </section>

    <div class="result-summary">
      <span class="zm-prompt">&gt; candidates</span>
      <span>{{ total }} 条候选题</span>
    </div>

    <section class="table-panel zm-glass" v-loading="loading">
      <div class="desktop-table">
        <el-table :data="records" row-key="id" empty-text="当前条件下没有候选题">
        <el-table-column label="题目" min-width="300">
          <template #default="{ row }">
            <button class="question-link" type="button" @click="openReview(row)">
              {{ row.title }}
            </button>
            <div class="question-meta">
              <span>会话 #{{ row.sourceSessionId }}</span>
              <span>消息 #{{ row.sourceMessageId }}</span>
              <span>{{ formatDateTime(row.createTime) }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="方向 / 分类" min-width="170">
          <template #default="{ row }">
            <div class="stacked-cell">
              <span>{{ directionLabel(row.direction) }}</span>
              <span class="cell-muted">{{ categoryLabel(row.categoryId) }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="难度" width="90">
          <template #default="{ row }">{{ difficultyLabel(row.difficulty) }}</template>
        </el-table-column>

        <el-table-column label="出现次数" width="100" align="center">
          <template #default="{ row }">
            <span class="duplicate-count">{{ row.duplicateCount }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="light">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button
                v-if="canApprove(row.status)"
                type="primary"
                link
                :icon="Check"
                @click="openReview(row)"
              >
                审核
              </el-button>
              <el-button
                v-else
                link
                @click="openReview(row)"
              >
                查看
              </el-button>
              <el-button
                v-if="canIgnore(row.status)"
                type="danger"
                link
                :icon="CloseBold"
                :loading="busyId === row.id"
                @click="ignoreCandidate(row)"
              >
                忽略
              </el-button>
            </div>
          </template>
        </el-table-column>
        </el-table>
      </div>

      <div class="mobile-list">
        <el-empty v-if="!loading && records.length === 0" description="当前条件下没有候选题" />
        <template v-else>
        <article v-for="candidate in records" :key="candidate.id" class="mobile-row">
          <div class="mobile-row-heading">
            <el-tag :type="statusType(candidate.status)" effect="light">
              {{ statusLabel(candidate.status) }}
            </el-tag>
            <span class="mobile-duplicate">出现 {{ candidate.duplicateCount }} 次</span>
          </div>
          <button class="question-link" type="button" @click="openReview(candidate)">
            {{ candidate.title }}
          </button>
          <div class="mobile-detail-grid">
            <span>{{ directionLabel(candidate.direction) }}</span>
            <span>{{ categoryLabel(candidate.categoryId) }}</span>
            <span>{{ difficultyLabel(candidate.difficulty) }}</span>
            <span>会话 #{{ candidate.sourceSessionId }}</span>
          </div>
          <div class="mobile-row-actions">
            <el-button
              v-if="canApprove(candidate.status)"
              type="primary"
              :icon="Check"
              @click="openReview(candidate)"
            >
              审核
            </el-button>
            <el-button v-else @click="openReview(candidate)">查看</el-button>
            <el-button
              v-if="canIgnore(candidate.status)"
              type="danger"
              plain
              :icon="CloseBold"
              :loading="busyId === candidate.id"
              @click="ignoreCandidate(candidate)"
            >
              忽略
            </el-button>
          </div>
        </article>
        </template>
      </div>

      <el-pagination
        v-if="total > pageSize"
        v-model:current-page="query.pageNum"
        class="pager"
        background
        layout="prev, pager, next, total"
        :total="total"
        :page-size="pageSize"
      />
    </section>

    <el-drawer
      v-model="drawerVisible"
      :title="canApprove(activeCandidate?.status ?? 2) ? '审核候选题' : '候选题详情'"
      size="min(680px, 100%)"
      destroy-on-close
    >
      <template v-if="activeCandidate">
        <div class="drawer-summary">
          <div>
            <span class="summary-label">来源</span>
            <strong>{{ directionLabel(activeCandidate.direction) }}</strong>
          </div>
          <div>
            <span class="summary-label">出现次数</span>
            <strong>{{ activeCandidate.duplicateCount }}</strong>
          </div>
          <div>
            <span class="summary-label">当前状态</span>
            <el-tag :type="statusType(activeCandidate.status)">
              {{ statusLabel(activeCandidate.status) }}
            </el-tag>
          </div>
        </div>

        <el-alert
          v-if="activeCandidate.errorMessage"
          class="candidate-error"
          type="error"
          :title="activeCandidate.errorMessage"
          :closable="false"
          show-icon
        />

        <el-form
          ref="reviewFormRef"
          :model="reviewForm"
          :rules="reviewRules"
          label-position="top"
          :disabled="!canApprove(activeCandidate.status)"
        >
          <el-form-item label="题目标题" prop="title">
            <el-input v-model="reviewForm.title" maxlength="256" show-word-limit />
          </el-form-item>

          <el-form-item label="题干补充">
            <el-input
              v-model="reviewForm.content"
              type="textarea"
              :rows="3"
              placeholder="可选，用于补充题目背景或限制条件"
            />
          </el-form-item>

          <el-form-item label="参考答案" prop="answer">
            <el-input
              v-model="reviewForm.answer"
              type="textarea"
              :rows="12"
              placeholder="检查 AI 生成的答案后再收录"
            />
          </el-form-item>

          <div class="form-grid">
            <el-form-item label="难度" prop="difficulty">
              <el-select v-model="reviewForm.difficulty" placeholder="选择难度">
                <el-option label="简单" :value="1" />
                <el-option label="中等" :value="2" />
                <el-option label="困难" :value="3" />
              </el-select>
            </el-form-item>

            <el-form-item label="分类" prop="categoryId">
              <el-select v-model="reviewForm.categoryId" filterable placeholder="选择分类">
                <el-option
                  v-for="category in categories"
                  :key="category.id"
                  :label="category.label"
                  :value="category.id"
                />
              </el-select>
            </el-form-item>
          </div>

          <el-form-item label="标签">
            <el-select
              v-model="reviewForm.tagIds"
              multiple
              filterable
              collapse-tags
              :max-collapse-tags="3"
              placeholder="选择标签"
            >
              <el-option v-for="tag in tags" :key="tag.id" :label="tag.name" :value="tag.id" />
            </el-select>
          </el-form-item>
        </el-form>

        <div v-if="activeCandidate.tagIds.length" class="original-tags">
          <span class="summary-label">AI 推荐标签</span>
          <el-tag v-for="name in candidateTagNames(activeCandidate.tagIds)" :key="name" type="info">
            {{ name }}
          </el-tag>
        </div>
      </template>

      <template #footer>
        <div v-if="activeCandidate" class="drawer-actions">
          <el-button
            v-if="canIgnore(activeCandidate.status)"
            type="danger"
            plain
            :icon="CloseBold"
            :loading="busyId === activeCandidate.id"
            @click="ignoreCandidate(activeCandidate)"
          >
            忽略候选题
          </el-button>
          <div class="drawer-actions-right">
            <el-button @click="drawerVisible = false">关闭</el-button>
            <el-button
              v-if="canApprove(activeCandidate.status)"
              type="primary"
              :icon="Check"
              :loading="busyId === activeCandidate.id"
              @click="submitApproval"
            >
              审核并收录
            </el-button>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.candidate-page {
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

.filter-bar {
  display: grid;
  grid-template-columns: minmax(150px, 0.7fr) minmax(160px, 0.8fr) minmax(240px, 1.4fr) auto auto;
  gap: 10px;
  padding: 14px;
  margin-bottom: 16px;
}

.result-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  color: var(--zm-ink-faint);
  font-size: 12px;
}

.table-panel {
  min-height: 360px;
  padding: 8px 12px 16px;
  overflow: hidden;
}

.mobile-list {
  display: none;
}

.question-link {
  max-width: 100%;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--zm-ink);
  font: inherit;
  font-weight: 650;
  line-height: 1.55;
  text-align: left;
  cursor: pointer;
}

.question-link:hover {
  color: var(--zm-accent);
}

.question-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin-top: 5px;
  color: var(--zm-ink-faint);
  font-size: 11px;
}

.stacked-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.cell-muted {
  color: var(--zm-ink-faint);
  font-size: 12px;
}

.duplicate-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 30px;
  height: 26px;
  padding: 0 8px;
  border-radius: var(--zm-radius-sm);
  background: var(--zm-accent-soft);
  color: var(--zm-accent);
  font-family: var(--zm-font-mono);
  font-size: 12px;
  font-weight: 700;
}

.row-actions {
  display: flex;
  align-items: center;
  min-height: 32px;
}

.pager {
  justify-content: center;
  margin-top: 18px;
}

.drawer-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 20px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--zm-border);
}

.drawer-summary > div {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.summary-label {
  color: var(--zm-ink-faint);
  font-size: 12px;
}

.candidate-error {
  margin-bottom: 18px;
}

.form-grid {
  display: grid;
  grid-template-columns: 0.7fr 1.3fr;
  gap: 14px;
}

.form-grid :deep(.el-select),
:deep(.el-form-item > .el-form-item__content > .el-select) {
  width: 100%;
}

.original-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
}

.drawer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.drawer-actions-right {
  display: flex;
  gap: 10px;
}

@media (max-width: 980px) {
  .filter-bar {
    grid-template-columns: 1fr 1fr;
  }

  .filter-bar .el-input {
    grid-column: 1 / -1;
  }
}

@media (max-width: 600px) {
  .page-heading h1 {
    font-size: 26px;
  }

  .heading-mark {
    display: none;
  }

  .filter-bar,
  .form-grid,
  .drawer-summary {
    grid-template-columns: 1fr;
  }

  .filter-bar .el-input {
    grid-column: auto;
  }

  .drawer-actions {
    align-items: stretch;
    flex-direction: column-reverse;
  }

  .drawer-actions-right {
    justify-content: flex-end;
  }

  .table-panel {
    min-height: 0;
    padding: 0 14px;
  }

  .desktop-table {
    display: none;
  }

  .mobile-list {
    display: block;
  }

  .mobile-row {
    padding: 18px 0;
    border-bottom: 1px solid var(--zm-border);
  }

  .mobile-row:last-child {
    border-bottom: none;
  }

  .mobile-row-heading,
  .mobile-row-actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
  }

  .mobile-row .question-link {
    display: block;
    margin: 13px 0 10px;
  }

  .mobile-duplicate {
    color: var(--zm-ink-faint);
    font-size: 12px;
  }

  .mobile-detail-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 6px 12px;
    margin-bottom: 16px;
    color: var(--zm-ink-soft);
    font-size: 12px;
  }

  .mobile-row-actions {
    justify-content: flex-end;
  }
}
</style>
