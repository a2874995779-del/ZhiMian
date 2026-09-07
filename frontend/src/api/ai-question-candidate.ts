import { get, put } from './http'
import type { PageResult } from './question'
import type {
  AiQuestionApprovePayload,
  AiQuestionCandidate,
  AiQuestionCandidateQuery,
} from '../types/ai-question-candidate'

const BASE_URL = '/admin/ai-question-candidates'

export function fetchAiQuestionCandidates(
  params: AiQuestionCandidateQuery,
): Promise<PageResult<AiQuestionCandidate>> {
  return get<PageResult<AiQuestionCandidate>>(BASE_URL, { params })
}

export function approveAiQuestionCandidate(
  id: number,
  data: AiQuestionApprovePayload,
): Promise<number> {
  return put<number>(`${BASE_URL}/${id}/approve`, data)
}

export function ignoreAiQuestionCandidate(id: number): Promise<void> {
  return put<void>(`${BASE_URL}/${id}/ignore`)
}
