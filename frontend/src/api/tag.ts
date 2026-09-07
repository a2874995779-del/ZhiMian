import { get } from './http'

export interface TagOption {
  id: number
  name: string
}

export function fetchTags(): Promise<TagOption[]> {
  return get<TagOption[]>('/tags')
}
