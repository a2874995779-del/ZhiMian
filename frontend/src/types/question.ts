export type Difficulty = 1 | 2 | 3

export interface QuestionCategory {
  id: number
  name: string
}

export interface QuestionListItem {
  id: number
  title: string
  categoryId: number
  categoryName: string
  tags: string[]
  difficulty: number
  viewCount: number
  // 可选展示字段，接口未返回时不展示
  readMinutes?: number
  mastery?: number
}

export interface QuestionDetail extends QuestionListItem {
  content: string | null
  answer: string
  // 可选代码示例，接口未返回时不展示
  codeSnippet?: {
    language: string
    code: string
  }
}
