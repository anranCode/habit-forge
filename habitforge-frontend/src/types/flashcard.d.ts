/** SM-2 评分：1=重来 2=困难 3=良好 4=简单 */
export type ReviewRating = 1 | 2 | 3 | 4

/** 闪卡 */
export interface Flashcard {
  id: string
  subjectId: string
  chapterId: string | null
  front: string
  back: string
  /** 难度因子，初始 2.50，下限 1.30 */
  easeFactor: number
  intervalDays: number
  repetition: number
  lapses: number
  /** yyyy-MM-dd */
  dueDate: string
  lastReviewedAt: string | null
  status: 'ACTIVE' | 'SUSPENDED'
  createdAt: string
  updatedAt: string
}

/** 复习队列响应：due 在前、新卡补齐至 limit */
export interface ReviewQueueResponse {
  cards: Flashcard[]
  /** 今日到期总数 */
  dueTotal: number
  /** 未到期新卡总数 */
  newTotal: number
}

/** 单次评分后的结果 + 当日达标奖励 */
export interface ReviewResultResponse {
  intervalDays: number
  easeFactor: number
  /** 下次到期日 yyyy-MM-dd（rating=1 时为今天） */
  dueDate: string
  reviewedToday: number
  /** 今日复习达标奖励积分，未达标为 0 */
  rewardPoints: number
}

export interface FlashcardStats {
  dueToday: number
  reviewedToday: number
  total: number
  /** 未来 7 天每日到期数 */
  next7Days: { date: string; count: number }[]
}

export interface FlashcardCreatePayload {
  subjectId: string
  chapterId?: string
  front: string
  back: string
}

/** 各字段可选，不传 = 不改 */
export interface FlashcardUpdatePayload {
  subjectId?: string
  chapterId?: string | null
  front?: string
  back?: string
}
