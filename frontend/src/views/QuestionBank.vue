<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { fetchCategoryTree } from '../api/category'
import { fetchQuestionDetail, fetchQuestionPage } from '../api/question'
import QuestionCard from '../components/question-bank/QuestionCard.vue'
import QuestionDetailDrawer from '../components/question-bank/QuestionDetailDrawer.vue'
import type { QuestionCategory, QuestionDetail, QuestionListItem } from '../types/question'

const categories = ref<QuestionCategory[]>([])
const activeCategoryId = ref<number | null>(null)

const questions = ref<QuestionListItem[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 12
const loading = ref(false)

async function loadQuestions() {
  loading.value = true
  try {
    const page = await fetchQuestionPage({
      pageNum: pageNum.value,
      pageSize,
      categoryId: activeCategoryId.value ?? undefined,
    })
    questions.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

// 切换分类回到第一页;翻页只重新拉数据
watch(activeCategoryId, () => {
  pageNum.value = 1
  loadQuestions()
})
watch(pageNum, loadQuestions)

onMounted(async () => {
  loadQuestions()
  // 后端返回的是两级分类树,题目都挂在二级分类上,把叶子拍平作为过滤项
  const tree = await fetchCategoryTree()
  categories.value = tree.flatMap((top) => (top.children?.length ? top.children : [top]))
    .map((c) => ({ id: c.id, name: c.name }))
})

const drawerVisible = ref(false)
const activeQuestion = ref<QuestionDetail | null>(null)

async function openDetail(id: number) {
  activeQuestion.value = await fetchQuestionDetail(id)
  drawerVisible.value = true
}
</script>

<template>
  <div class="bank">
    <aside class="filter-rail zm-glass">
      <p class="filter-title zm-prompt">&gt; filter_by</p>
      <div class="filter-list">
        <button
          class="filter-pill"
          :class="{ 'filter-pill--active': activeCategoryId === null }"
          type="button"
          @click="activeCategoryId = null"
        >
          全部题目
        </button>
        <button
          v-for="cat in categories"
          :key="cat.id"
          class="filter-pill"
          :class="{ 'filter-pill--active': activeCategoryId === cat.id }"
          type="button"
          @click="activeCategoryId = cat.id"
        >
          {{ cat.name }}
        </button>
      </div>
    </aside>

    <div class="card-area" v-loading="loading">
      <el-empty v-if="!loading && questions.length === 0" description="这个分类下还没有题目" />

      <TransitionGroup v-else tag="div" name="zm-list" class="card-grid">
        <QuestionCard
          v-for="q in questions"
          :key="q.id"
          :question="q"
          @open="openDetail"
        />
      </TransitionGroup>

      <el-pagination
        v-if="total > pageSize"
        v-model:current-page="pageNum"
        class="pager"
        background
        layout="prev, pager, next, total"
        :total="total"
        :page-size="pageSize"
      />
    </div>

    <QuestionDetailDrawer v-model="drawerVisible" :question="activeQuestion" />
  </div>
</template>

<style scoped>
.bank {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

.filter-rail {
  width: 208px;
  flex-shrink: 0;
  padding: 20px 16px;
  position: sticky;
  top: 12px;
}

.filter-title {
  font-size: 12px;
  margin-bottom: 12px;
  padding: 0 6px;
}

.filter-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.filter-pill {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 9px 12px;
  border: none;
  background: transparent;
  border-radius: var(--zm-radius-sm);
  font-size: 13px;
  font-weight: 600;
  color: var(--zm-ink-soft);
  cursor: pointer;
  text-align: left;
  transition: all var(--zm-dur-fast) var(--zm-ease);
}

.filter-pill:hover {
  background: var(--zm-surface-strong);
  color: var(--zm-ink);
}

.filter-pill--active {
  background: var(--zm-accent-soft);
  color: var(--zm-accent);
}

.card-area {
  flex: 1;
  min-width: 0;
  min-height: 320px;
}

.card-grid {
  position: relative;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 18px;
}

.pager {
  margin-top: 24px;
  justify-content: center;
}

@media (max-width: 760px) {
  .bank {
    flex-direction: column;
  }
  .filter-rail {
    width: 100%;
    position: static;
  }
  .filter-list {
    flex-direction: row;
    flex-wrap: wrap;
  }
}
</style>
