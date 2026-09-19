export type KnowledgeDocumentStatus = 0 | 1 | 2 | 3 | 4

export interface KnowledgeDocumentImportPayload {
  title: string
  originalFilename: string
  sourceType: 'MARKDOWN' | 'TEXT'
  content: string
}

export interface KnowledgeDocumentStatusVO {
  id: number
  title: string
  status: KnowledgeDocumentStatus
  chunkCount: number
  embeddingModel: string | null
  vectorDimension: number | null
  errorMessage: string | null
  updateTime: string | null
}

export interface TrackedKnowledgeDocument {
  id: number
  submittedAt: string
}
