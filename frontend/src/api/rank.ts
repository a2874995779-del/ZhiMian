import { get } from './http'
import type { RankRecord } from '../types/rank'

export type RankType = 'total' | 'daily'

export function fetchAnswerRanks(type: RankType = 'total', limit = 10): Promise<RankRecord[]> {
  return get<RankRecord[]>('/ranks/answer', { params: { type, limit } })
}
