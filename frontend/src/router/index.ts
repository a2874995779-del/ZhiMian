import { createRouter, createWebHistory } from 'vue-router'

const TOKEN_KEY = 'zm-token'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'dashboard',
      component: () => import('../views/Dashboard.vue'),
      meta: { label: '仪表盘', icon: 'grid', requiresAuth: true },
    },
    {
      path: '/questions',
      name: 'question-bank',
      component: () => import('../views/QuestionBank.vue'),
      meta: { label: '题库中心', icon: 'book' },
    },
    {
      path: '/leaderboard',
      name: 'leaderboard',
      component: () => import('../views/Leaderboard.vue'),
      meta: { label: '荣誉排行榜', icon: 'trophy' },
    },
    {
      path: '/interview',
      name: 'interview-cabin',
      component: () => import('../views/InterviewCabin.vue'),
      meta: { label: '模拟面试舱', icon: 'mic', requiresAuth: true },
    },
    {
      path: '/interview/history',
      name: 'interview-history',
      component: () => import('../views/InterviewHistory.vue'),
      meta: { label: '面试记录', icon: 'history', requiresAuth: true },
    },
    {
      path: '/wrong-questions',
      name: 'wrong-questions',
      component: () => import('../views/WrongQuestions.vue'),
      meta: { label: '错题本', icon: 'wrong', requiresAuth: true },
    },
    {
      path: '/favorites',
      name: 'favorites',
      component: () => import('../views/Favorites.vue'),
      meta: { label: '收藏夹', icon: 'favorites', requiresAuth: true },
    },
    {
      path: '/admin/ai-questions',
      name: 'ai-question-review',
      component: () => import('../views/AiQuestionCandidates.vue'),
      meta: {
        label: 'AI 题目审核',
        icon: 'review',
        requiresAuth: true,
        requiresAdmin: true,
      },
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('../views/ComingSoon.vue'),
      meta: { label: '成就中心', icon: 'user', requiresAuth: true },
    },
  ],
})

router.beforeEach((to) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (to.meta.requiresAdmin) {
    try {
      const user = JSON.parse(localStorage.getItem('zm-user') ?? 'null')
      if (token && user?.role === 'admin') {
        return true
      }
    } catch {
      localStorage.removeItem('zm-user')
    }
    return token ? { path: '/' } : {
      path: '/questions',
      query: { login: '1', redirect: to.fullPath },
    }
  }
  if (!to.meta.requiresAuth || token) {
    return true
  }
  return {
    path: '/questions',
    query: {
      login: '1',
      redirect: to.fullPath,
    },
  }
})

export default router
