export type QuestionType = 'SINGLE' | 'MULTI' | 'JUDGE' | 'SHORT'

export interface QuestionOption {
  key: string
  text: string
}

export interface Question {
  id: string
  subjectId: string
  chapterId: string | null
  questionType: QuestionType
  /** 题干，可含 Markdown */
  stem: string
  /** SINGLE/MULTI 为选项数组；JUDGE/SHORT 为 null */
  options: QuestionOption[] | null
  /** 单选 A；多选 "A,C" 升序逗号分隔；判断 "T"/"F"；主观为参考答案 */
  answer: string
  analysis: string | null
  sourceType: 'CUSTOM' | 'PAST_EXAM' | 'TEXTBOOK' | 'AI'
  sourceDetail: string | null
  /** 1-5，可为 null */
  difficulty: number | null
  createdAt: string
  updatedAt: string
}

/** 后端 MyBatis-Plus 分页壳 */
export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export interface WrongQuestion {
  questionId: string
  question: Question
  wrongCount: number
  correctStreak: number
  mastered: boolean
  lastWrongAt: string
  lastPracticedAt: string | null
}

export interface QuestionQuery {
  subjectId?: string
  chapterId?: string
  sourceType?: string
  difficulty?: number
  keyword?: string
  page?: number
  size?: number
}

export interface QuestionCreatePayload {
  subjectId: string
  chapterId?: string
  questionType: QuestionType
  stem: string
  options?: QuestionOption[]
  answer: string
  analysis?: string
  sourceType?: string
  sourceDetail?: string
  difficulty?: number
}

/** 各字段可选，不传 = 不改 */
export interface QuestionUpdatePayload {
  chapterId?: string | null
  questionType?: QuestionType
  stem?: string
  options?: QuestionOption[] | null
  answer?: string
  analysis?: string | null
  sourceType?: string
  sourceDetail?: string | null
  difficulty?: number | null
}
