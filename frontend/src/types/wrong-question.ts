export interface WrongQuestionQuery {
  pageNum: number
  pageSize: number
  status?: number
  categoryId?: number
  keyword?: string
}

export interface WrongQuestionItem {
  id: number
  questionId: number
  title: string
  difficulty: number
  categoryId: number
  categoryName: string
  tags: string[]
  wrongCount: number
  correctCount: number
  status: number
  lastWrongTime: string | null
  lastReviewTime: string | null
}
