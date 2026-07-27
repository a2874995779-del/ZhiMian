import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'dashboard',
      component: () => import('../views/Dashboard.vue'),
      meta: { label: '仪表盘', icon: 'grid' },
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
      meta: { label: '模拟面试舱', icon: 'mic' },
    },
    {
      path: '/interview/history',
      name: 'interview-history',
      component: () => import('../views/InterviewHistory.vue'),
      meta: { label: '面试记录', icon: 'history' },
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('../views/ComingSoon.vue'),
      meta: { label: '成就中心', icon: 'user' },
    },
  ],
})

export default router
