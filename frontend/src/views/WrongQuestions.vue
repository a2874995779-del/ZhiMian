<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Notebook } from '@element-plus/icons-vue'
import { fetchCategoryTree } from '../api/category'
import {
  fetchWrongQuestions,
  markWrongQuestionMastered,
  markWrongQuestionUnmastered,
  removeWrongQuestion,
} from '../api/wrong-question'
import { fetchQuestionDetail } from '../api/question'
import QuestionCollectionCard from '../components/question-bank/QuestionCollectionCard.vue'
import QuestionDetailDrawer from '../components/question-bank/QuestionDetailDrawer.vue'
import WrongQuestionExplanationPanel from '../components/wrong-question/WrongQuestionExplanationPanel.vue'
import type { QuestionCategory, QuestionDetail } from '../types/question'
import type { WrongQuestionItem, WrongQuestionQuery } from '../types/wrong-question'

const pageSize = 9
const query = reactive<WrongQuestionQuery>({ pageNum: 1, pageSize })
const categories = ref<QuestionCategory[]>([])
const items = ref<WrongQuestionItem[]>([])
const total = ref(0)
const loading = ref(false)
const busyAction = ref<string | null>(null)

const drawerVisible = ref(false)
const activeQuestion = ref<QuestionDetail | null>(null)

async function loadWrongQuestions() {
  loading.value = true
  try {
    const page = await fetchWrongQuestions({ ...query })
    items.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  if (query.pageNum === 1) {
    loadWrongQuestions()
  } else {
    query.pageNum = 1
  }
}

function resetFilters() {
  query.categoryId = undefined
  query.status = undefined
  query.keyword = undefined
  applyFilters()
}

async function openDetail(questionId: number) {
  activeQuestion.value = await fetchQuestionDetail(questionId)
  drawerVisible.value = true
}

async function removeItem(questionId: number) {
  try {
    await ElMessageBox.confirm('移出后不会删除答题记录，只会从错题本中移除。', '确认移出错题本', {
      type: 'warning',
      confirmButtonText: '移出',
      cancelButtonText: '保留',
    })
  } catch {
    return
  }

  busyAction.value = `${questionId}:remove`
  try {
    await removeWrongQuestion(questionId)
    ElMessage.success('已移出错题本')
    await loadWrongQuestions()
  } finally {
    busyAction.value = null
  }
}

async function toggleMastered(questionId: number, mastered: boolean) {
  busyAction.value = `${questionId}:${mastered ? 'mastered' : 'unmastered'}`
  try {
    if (mastered) {
      await markWrongQuestionMastered(questionId)
    } else {
      await markWrongQuestionUnmastered(questionId)
    }
    ElMessage.success(mastered ? '已标记为掌握' : '已重新加入待复习')
    await loadWrongQuestions()
  } finally {
    busyAction.value = null
  }
}

watch(() => query.pageNum, loadWrongQuestions)

onMounted(async () => {
  loadWrongQuestions()
  const tree = await fetchCategoryTree()
  categories.value = tree.flatMap((top) => (top.children?.length ? top.children : [top]))
    .map((category) => ({ id: category.id, name: category.name }))
})
</script>

<template>
  <div class="collection-page">
    <header class="page-heading">
      <div>
        <p class="page-eyebrow zm-prompt">&gt; review_queue</p>
        <h1>错题本</h1>
        <p class="page-description">把错误变成下一次答对的提示。</p>
      </div>
      <div class="heading-mark" aria-hidden="true"><el-icon><Notebook /></el-icon></div>
    </header>

    <section class="filter-bar zm-glass">
      <el-select v-model="query.status" clearable placeholder="全部状态" @change="applyFilters">
        <el-option label="未掌握" :value="0" />
        <el-option label="已掌握" :value="1" />
      </el-select>
      <el-select v-model="query.categoryId" clearable placeholder="全部分类" @change="applyFilters">
        <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
      </el-select>
      <el-input
        v-model="query.keyword"
        clearable
        placeholder="搜索题目标题"
        @keyup.enter="applyFilters"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button type="primary" :icon="Search" @click="applyFilters">搜索</el-button>
      <el-button text @click="resetFilters">重置</el-button>
    </section>

    <div class="collection-summary">
      <span class="summary-label zm-prompt">&gt; wrong_questions</span>
      <span>{{ total }} 道题目</span>
    </div>

    <section class="collection-grid" v-loading="loading">
      <el-empty v-if="!loading && items.length === 0" description="还没有错题记录" />
      <QuestionCollectionCard
        v-for="item in items"
        v-else
        :key="item.questionId"
        :item="item"
        mode="wrong"
        :busy-action="busyAction"
        @open="openDetail"
        @remove="removeItem"
        @toggle-mastered="toggleMastered"
      />
    </section>

    <el-pagination
      v-if="total > pageSize"
      v-model:current-page="query.pageNum"
      class="pager"
      background
      layout="prev, pager, next, total"
      :total="total"
      :page-size="pageSize"
    />

    <QuestionDetailDrawer v-model="drawerVisible" :question="activeQuestion">
      <template #after-answer>
        <WrongQuestionExplanationPanel
          v-if="activeQuestion"
          :question-id="activeQuestion.id"
        />
      </template>
    </QuestionDetailDrawer>
  </div>
</template>

<style scoped>
.collection-page {
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
  border: 1px solid var(--zm-orange);
  border-radius: var(--zm-radius-md);
  color: var(--zm-orange);
  background: rgba(255, 159, 10, 0.12);
  font-size: 22px;
}

.filter-bar {
  display: grid;
  grid-template-columns: minmax(150px, 0.8fr) minmax(150px, 0.8fr) minmax(220px, 1.4fr) auto auto;
  gap: 10px;
  padding: 14px;
  margin-bottom: 18px;
}

.collection-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  color: var(--zm-ink-faint);
  font-size: 12px;
}

.summary-label {
  color: var(--zm-accent);
}

.collection-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 18px;
  min-height: 280px;
}

.collection-grid > .el-empty {
  grid-column: 1 / -1;
}

.pager {
  justify-content: center;
  margin-top: 24px;
}

@media (max-width: 900px) {
  .filter-bar {
    grid-template-columns: 1fr 1fr;
  }

  .filter-bar .el-input {
    grid-column: 1 / -1;
  }
}

@media (max-width: 560px) {
  .page-heading h1 {
    font-size: 26px;
  }

  .filter-bar {
    grid-template-columns: 1fr;
  }

  .filter-bar .el-input {
    grid-column: auto;
  }
}
</style>
