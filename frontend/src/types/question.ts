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
  // 以下字段后端暂未提供,mock 数据里有,真实接口下为 undefined,界面按需隐藏
  readMinutes?: number
  mastery?: number
}

export interface QuestionDetail extends QuestionListItem {
  content: string | null
  answer: string
  // 后端暂无代码示例字段,仅 mock 数据携带
  codeSnippet?: {
    language: string
    code: string
  }
}
