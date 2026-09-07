export type AiQuestionCandidateStatus = 0 | 1 | 2 | 3 | 4 | 5

export interface AiQuestionCandidate {
  id: number
  title: string
  normalizedTitle: string
  answer: string | null
  difficulty: number | null
  categoryId: number | null
  tagIds: number[]
  direction: string
  sourceSessionId: number
  sourceMessageId: number
  duplicateCount: number
  status: AiQuestionCandidateStatus
  errorMessage: string | null
  questionId: number | null
  createTime: string
  updateTime: string
}

export interface AiQuestionCandidateQuery {
  pageNum: number
  pageSize: number
  status?: AiQuestionCandidateStatus
  direction?: string
  keyword?: string
}

export interface AiQuestionApprovePayload {
  title: string
  content?: string
  answer: string
  difficulty: number
  categoryId: number
  tagIds: number[]
}
