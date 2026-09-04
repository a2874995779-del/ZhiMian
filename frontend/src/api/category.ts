import { get } from './http'

export interface CategoryNode {
  id: number
  name: string
  children: CategoryNode[] | null
}

export function fetchCategoryTree(): Promise<CategoryNode[]> {
  return get<CategoryNode[]>('/categories')
}
