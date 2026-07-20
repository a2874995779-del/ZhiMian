export interface DailyProgress {
  done: number
  goal: number
}

export interface TrendPoint {
  label: string
  passProbability: number // 0-100
}

export interface DashboardStats {
  today: DailyProgress
  streakDays: number
  trend: TrendPoint[]
  totalSolved: number
  weeklyDelta: number // 相较上周解题数变化,可正可负
}
