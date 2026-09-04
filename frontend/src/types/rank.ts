export interface RankRecord {
  rank: number
  userId: number
  nickname: string | null
  count: number
}

export interface RankUser {
  rank: number
  userId: number
  nickname: string
  avatarHue: number // 头像渐变的色相,0-360
  count: number // 答对题数
  badges: string[]
}
