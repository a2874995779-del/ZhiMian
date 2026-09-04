export interface FavoriteQuery {
  pageNum: number
  pageSize: number
  categoryId?: number
  difficulty?: number
  keyword?: string
}

export interface FavoriteQuestionItem {
  questionId: number
  title: string
  difficulty: number
  categoryId: number
  categoryName: string
  viewCount: number
  tags: string[]
  favoriteTime: string
}
