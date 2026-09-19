import { get, post, put } from './http'
import type {
  KnowledgeDocumentImportPayload,
  KnowledgeDocumentStatusVO,
} from '../types/knowledge-document'

const BASE_URL = '/admin/knowledge-documents'

export function submitKnowledgeDocument(
  data: KnowledgeDocumentImportPayload,
): Promise<number> {
  return post<number>(BASE_URL, data)
}

export function fetchKnowledgeDocumentStatus(
  id: number,
): Promise<KnowledgeDocumentStatusVO> {
  return get<KnowledgeDocumentStatusVO>(`${BASE_URL}/${id}`)
}

export function retryKnowledgeDocument(id: number): Promise<void> {
  return put<void>(`${BASE_URL}/${id}/retry`)
}
