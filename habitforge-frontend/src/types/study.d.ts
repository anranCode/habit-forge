/** 章节状态：三态循环 NOT_STARTED -> IN_PROGRESS -> DONE -> NOT_STARTED */
export type ChapterStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'DONE'

/** 科目（含实时汇总：章节进度、考试倒计时、到期卡/错题数——P0 卡与错题恒 0） */
export interface Subject {
  id: string
  name: string
  /** yyyy-MM-dd，未设置考期时为 null */
  examDate: string | null
  /** 考期文本，如「2026 年 10 月」 */
  examSession: string | null
  description: string | null
  sortOrder: number
  chapterTotal: number
  chapterDone: number
  /** examDate 距今天数；无考期或已过期为 null（负数按 0） */
  daysLeft: number | null
  /** P0 恒 0，P1 闪卡上线后填真实值 */
  dueCards: number
  /** P0 恒 0，P1 错题本上线后填真实值 */
  wrongCount: number
}

/** 章节（平铺返回，前端按 parentId 组树） */
export interface Chapter {
  id: string
  subjectId: string
  parentId: string | null
  name: string
  sortOrder: number
  status: ChapterStatus
  /** ISO 时间，非 DONE 为 null */
  doneAt: string | null
}

/** 学习总览（Home 卡片与 StudyHome 汇总条共用） */
export interface StudyOverview {
  subjects: Subject[]
  /** P0 恒 0 */
  dueCardsTotal: number
  /** P0 恒 0 */
  wrongsTotal: number
  /** P0 恒 0 */
  reviewedToday: number
}

export interface SubjectCreatePayload {
  /** 必填，≤100 */
  name: string
  /** yyyy-MM-dd */
  examDate?: string
  /** ≤50 */
  examSession?: string
  /** ≤500 */
  description?: string
  sortOrder?: number
}

/** 各字段可选，null/不传 = 不改 */
export interface SubjectUpdatePayload {
  name?: string
  examDate?: string | null
  examSession?: string | null
  description?: string | null
  sortOrder?: number
}

export interface ChapterCreatePayload {
  subjectId: string
  /** null/不传 = 顶层章节 */
  parentId?: string | null
  /** ≤200 */
  name: string
  sortOrder?: number
}

export interface ChapterUpdatePayload {
  name?: string
  /** 换父时后端沿链上溯防环，违者 7003 */
  parentId?: string | null
  sortOrder?: number
}
