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

export type RagExplanationMode = 'RAG' | 'MODEL_ONLY' | 'REFERENCE_ANSWER'

export type RagDegradedReason =
  | 'RAG_DISABLED'
  | 'NO_RELEVANT_KNOWLEDGE'
  | 'RETRIEVAL_UNAVAILABLE'
  | 'AI_UNAVAILABLE'

export interface KnowledgeCitation {
  index: number
  chunkId: number
  documentId: number
  documentTitle: string
  headingPath: string | null
  score: number | null
  excerpt: string
}

export interface WrongQuestionExplanation {
  questionId: number
  questionTitle: string
  summary: string
  keyPoints: string[]
  commonMistakes: string[]
  reviewAdvice: string
  mode: RagExplanationMode
  ragApplied: boolean
  aiGenerated: boolean
  degradedReasons: RagDegradedReason[]
  citations: KnowledgeCitation[]
}
