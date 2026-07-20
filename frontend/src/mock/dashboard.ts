import type { DashboardStats } from '../types/dashboard'

export const dashboardStats: DashboardStats = {
  today: { done: 7, goal: 10 },
  streakDays: 12,
  totalSolved: 186,
  weeklyDelta: 23,
  trend: [
    { label: '周一', passProbability: 58 },
    { label: '周二', passProbability: 61 },
    { label: '周三', passProbability: 65 },
    { label: '周四', passProbability: 63 },
    { label: '周五', passProbability: 70 },
    { label: '周六', passProbability: 74 },
    { label: '周日', passProbability: 79 },
  ],
}
