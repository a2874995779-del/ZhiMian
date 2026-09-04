import { del, get, put } from './http'
import type { PageResult } from './question'
import type { WrongQuestionItem, WrongQuestionQuery } from '../types/wrong-question'

export function fetchWrongQuestions(params: WrongQuestionQuery): Promise<PageResult<WrongQuestionItem>> {
  return get<PageResult<WrongQuestionItem>>('/wrong-questions', { params })
}

export function markWrongQuestionMastered(questionId: number): Promise<void> {
  return put<void>(`/wrong-questions/${questionId}/mastered`)
}

export function markWrongQuestionUnmastered(questionId: number): Promise<void> {
  return put<void>(`/wrong-questions/${questionId}/unmastered`)
}

export function removeWrongQuestion(questionId: number): Promise<void> {
  return del<void>(`/wrong-questions/${questionId}`)
}
