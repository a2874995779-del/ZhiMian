import http from './http'

export interface CategoryNode {
  id: number
  name: string
  children: CategoryNode[] | null
}

export function fetchCategoryTree(): Promise<CategoryNode[]> {
  return http.get('/categories') as unknown as Promise<CategoryNode[]>
}
