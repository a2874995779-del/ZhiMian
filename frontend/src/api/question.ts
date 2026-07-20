import http from './http'
import type { QuestionDetail, QuestionListItem } from '../types/question'

export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}

export interface QuestionPageParams {
  pageNum: number
  pageSize: number
  categoryId?: number
  difficulty?: number
  keyword?: string
}

export function fetchQuestionPage(params: QuestionPageParams): Promise<PageResult<QuestionListItem>> {
  return http.get('/questions', { params }) as unknown as Promise<PageResult<QuestionListItem>>
}

export function fetchQuestionDetail(id: number): Promise<QuestionDetail> {
  return http.get(`/questions/${id}`) as unknown as Promise<QuestionDetail>
}
