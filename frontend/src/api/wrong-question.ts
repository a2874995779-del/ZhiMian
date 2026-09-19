import { del, get, post, put } from './http'
import type { PageResult } from './question'
import type {
  WrongQuestionExplanation,
  WrongQuestionItem,
  WrongQuestionQuery,
} from '../types/wrong-question'

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

export function generateWrongQuestionExplanation(
  questionId: number,
): Promise<WrongQuestionExplanation> {
  return post<WrongQuestionExplanation>(`/wrong-questions/${questionId}/explanation`, undefined, {
    timeout: 60000,
  })
}
