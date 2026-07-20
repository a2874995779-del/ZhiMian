export interface SkillPoint {
  name: string
  value: number // 0-100
}

export interface RankUser {
  rank: number
  userId: number
  nickname: string
  avatarHue: number // 头像渐变的色相,0-360
  power: number // 战力值
  solved: number // 已通关题数
  winRate: number // 胜率,0-100
  skills: SkillPoint[]
  badges: string[]
}
