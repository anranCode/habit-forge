/** 日记关联的习惯摘要 */
export interface JournalHabitBrief {
  id: string
  name: string
  category?: string
  identityTag?: string
}

/** 日记图片（objectKey 走 /images/ 代理展示） */
export interface JournalImage {
  id: string
  objectKey: string
  originalName?: string
  contentType: string
  fileSize?: number
  width?: number
  height?: number
  sortOrder?: number
}

/** 日记详情 */
export interface JournalDetail {
  id: string
  journalDate: string
  title?: string
  mood?: number
  content?: string
  createdAt?: string
  updatedAt?: string
  habits: JournalHabitBrief[]
  images: JournalImage[]
}

/** 日记摘要（列表） */
export interface JournalSummary {
  id: string
  journalDate: string
  title?: string
  mood?: number
  hasContent: boolean
  imageCount: number
  habitCount: number
}

export interface JournalCreatePayload {
  journalDate?: string
  title?: string
  mood?: number
  content?: string
  /** null/不传 = 自动关联当日已打卡习惯；空数组 = 不关联 */
  habitIds?: string[] | null
}

export interface JournalUpdatePayload {
  title?: string
  mood?: number
  content?: string
}

/** 习惯心得 */
export interface Reflection {
  id: string
  journalId: string
  habitId: string
  checkinId?: string
  /** 1完成 0未完成 */
  result: number
  /** 1😊好 2😐一般 3😫糟糕 */
  feeling?: number
  /** 难度 1-5 星 */
  difficulty?: number
  reason?: string
  obstacle?: string
  learning?: string
  adjustment?: string
  createdAt?: string
  updatedAt?: string
  habitName?: string
  journalDate?: string
  journalTitle?: string
}

export interface ReflectionPayload {
  journalId: string
  habitId: string
  result: number
  feeling?: number
  difficulty?: number
  reason?: string
  obstacle?: string
  learning?: string
  adjustment?: string
}

export interface ReflectionUpdatePayload {
  result?: number
  feeling?: number
  difficulty?: number
  reason?: string
  obstacle?: string
  learning?: string
  adjustment?: string
}
