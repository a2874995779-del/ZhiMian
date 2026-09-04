<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, StarFilled } from '@element-plus/icons-vue'
import { fetchCategoryTree } from '../api/category'
import { fetchFavorites, removeFavorite } from '../api/favorite'
import { fetchQuestionDetail } from '../api/question'
import QuestionCollectionCard from '../components/question-bank/QuestionCollectionCard.vue'
import QuestionDetailDrawer from '../components/question-bank/QuestionDetailDrawer.vue'
import type { QuestionCategory, QuestionDetail } from '../types/question'
import type { FavoriteQuestionItem, FavoriteQuery } from '../types/favorite'

const pageSize = 9
const query = reactive<FavoriteQuery>({ pageNum: 1, pageSize })
const categories = ref<QuestionCategory[]>([])
const items = ref<FavoriteQuestionItem[]>([])
const total = ref(0)
const loading = ref(false)
const busyAction = ref<string | null>(null)

const drawerVisible = ref(false)
const activeQuestion = ref<QuestionDetail | null>(null)

async function loadFavorites() {
  loading.value = true
  try {
    const page = await fetchFavorites({ ...query })
    items.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  if (query.pageNum === 1) {
    loadFavorites()
  } else {
    query.pageNum = 1
  }
}

function resetFilters() {
  query.categoryId = undefined
  query.difficulty = undefined
  query.keyword = undefined
  applyFilters()
}

async function openDetail(questionId: number) {
  activeQuestion.value = await fetchQuestionDetail(questionId)
  drawerVisible.value = true
}

async function removeItem(questionId: number) {
  try {
    await ElMessageBox.confirm('取消收藏后，这道题会从收藏夹中移除。', '确认取消收藏', {
      type: 'warning',
      confirmButtonText: '取消收藏',
      cancelButtonText: '保留',
    })
  } catch {
    return
  }

  busyAction.value = `${questionId}:remove`
  try {
    await removeFavorite(questionId)
    ElMessage.success('已取消收藏')
    await loadFavorites()
  } finally {
    busyAction.value = null
  }
}

function handleFavoriteChanged(favorited: boolean) {
  if (!favorited) {
    loadFavorites()
  }
}

watch(() => query.pageNum, loadFavorites)

onMounted(async () => {
  loadFavorites()
  const tree = await fetchCategoryTree()
  categories.value = tree.flatMap((top) => (top.children?.length ? top.children : [top]))
    .map((category) => ({ id: category.id, name: category.name }))
})

</script>

<template>
  <div class="collection-page">
    <header class="page-heading">
      <div>
        <p class="page-eyebrow zm-prompt">&gt; saved_questions</p>
        <h1>收藏夹</h1>
        <p class="page-description">把值得反复推敲的题目留在手边。</p>
      </div>
      <div class="heading-mark" aria-hidden="true"><el-icon><StarFilled /></el-icon></div>
    </header>

    <section class="filter-bar zm-glass">
      <el-select v-model="query.categoryId" clearable placeholder="全部分类" @change="applyFilters">
        <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
      </el-select>
      <el-select v-model="query.difficulty" clearable placeholder="全部难度" @change="applyFilters">
        <el-option label="简单" :value="1" />
        <el-option label="中等" :value="2" />
        <el-option label="困难" :value="3" />
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
      <span class="summary-label zm-prompt">&gt; favorites</span>
      <span>{{ total }} 道题目</span>
    </div>

    <section class="collection-grid" v-loading="loading">
      <el-empty v-if="!loading && items.length === 0" description="还没有收藏题目" />
      <QuestionCollectionCard
        v-for="item in items"
        v-else
        :key="item.questionId"
        :item="item"
        mode="favorite"
        :busy-action="busyAction"
        @open="openDetail"
        @remove="removeItem"
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

    <QuestionDetailDrawer
      v-model="drawerVisible"
      :question="activeQuestion"
      @favorite-changed="handleFavoriteChanged"
    />
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
  border: 1px solid var(--zm-gold);
  border-radius: var(--zm-radius-md);
  color: var(--zm-gold);
  background: var(--zm-gold-soft);
  font-size: 22px;
}

.filter-bar {
  display: grid;
  grid-template-columns: minmax(150px, 0.8fr) minmax(130px, 0.7fr) minmax(220px, 1.4fr) auto auto;
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
