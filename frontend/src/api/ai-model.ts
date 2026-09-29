import { del, get, post, put } from './http'
import type { AiModelSettings } from '../types/ai-model'

export function getAiModelSettings(): Promise<AiModelSettings> {
  return get<AiModelSettings>('/user/ai-model')
}

export function saveAiModelSettings(model: string, apiKey?: string): Promise<AiModelSettings> {
  return put<AiModelSettings>('/user/ai-model', { model, apiKey })
}

export function testAiModelSettings(): Promise<void> {
  return post<void>('/user/ai-model/test')
}

export function clearAiModelSettings(): Promise<void> {
  return del<void>('/user/ai-model')
}
