import { del, get, post } from './http'
import type { PageResult } from './question'
import type { FavoriteQuestionItem, FavoriteQuery } from '../types/favorite'

export function fetchFavorites(params: FavoriteQuery): Promise<PageResult<FavoriteQuestionItem>> {
  return get<PageResult<FavoriteQuestionItem>>('/favorites', { params })
}

export function addFavorite(questionId: number): Promise<void> {
  return post<void>(`/favorites/questions/${questionId}`)
}

export function removeFavorite(questionId: number): Promise<void> {
  return del<void>(`/favorites/questions/${questionId}`)
}

export function checkFavorite(questionId: number): Promise<boolean> {
  return get<boolean>(`/favorites/questions/${questionId}`)
}
