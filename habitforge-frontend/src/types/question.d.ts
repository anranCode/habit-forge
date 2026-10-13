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
  /** 考点标签（后端 knowledge_points JSON 数组；null = 尚未标注） */
  knowledgePoints: string[] | null
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
  questionType?: string
  sourceType?: string
  difficulty?: number
  keyword?: string
  /** 按考点整元素匹配（后端 LIKE '%"x"%'） */
  knowledgePoint?: string
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
  knowledgePoints?: string[]
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
  /** null/不传 = 不改；空数组 = 清空 */
  knowledgePoints?: string[] | null
}

// ============ 刷题 / 判分（功能B）============

/** 随机取题下发：后端做了列裁剪，answer/analysis **不会出现在这个形状里** */
export interface PracticeQuestion {
  id: string
  questionType: QuestionType
  stem: string
  options: QuestionOption[] | null
  difficulty: number | null
}

export interface RandomQuery {
  subjectId?: string
  chapterId?: string
  /** 逗号分隔题型集合，如 "SINGLE,MULTI,JUDGE" */
  questionTypes?: string
  limit?: number
}

/** 提交作答：answer 与存储格式同构 —— 单选 "A"、多选 "A,C"、判断 "T"/"F"、主观自由文本 */
export interface GradePayload {
  questionId: string
  answer: string
}

export interface AiGrade {
  points: { point: string; hit: boolean }[]
  covered: number
  total: number
  ratio: number
}

/** 判分后该题在错题本里的状态（答错入本 / 答对累计连对都由后端做完再回显） */
export interface GradeWrongState {
  inBook: boolean
  wrongCount: number
  correctStreak: number
  mastered: boolean
}

export interface GradeResult {
  questionId: string
  questionType: QuestionType
  correct: boolean
  /** 判后才回显 */
  standardAnswer: string
  analysis: string | null
  /** 仅主观题非 null */
  aiGrade: AiGrade | null
  /** 仅主观题非 null（question_grades 行 id） */
  gradeId: string | null
  wrongBook: GradeWrongState | null
}
