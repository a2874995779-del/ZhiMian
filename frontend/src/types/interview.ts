export interface ReportDimension {
  name: string
  score: number // 0-100
  comment: string
}

export interface InterviewStage {
  id: number
  name: string
  question: string
  hint: string
  report: ReportDimension[]
}

export type StagePhase = 'answering' | 'evaluating' | 'reported'
