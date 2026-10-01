/** 每周 AI 复盘报告（reviews 表 type=WEEKLY） */
export interface WeeklyReport {
  id: string
  /** yyyy-MM-dd（周一） */
  periodStart: string
  /** yyyy-MM-dd（周日；本周未过完时为生成当天） */
  periodEnd: string
  reviewDate: string
  title: string | null
  /** 综合评分 0-100 */
  score: number | null
  goodThings: string | null
  badThings: string | null
  learnings: string | null
  /** 多条建议，换行分隔 */
  suggestions: string | null
  aiGenerated: boolean | null
  model: string | null
  totalTokens: number | null
  /** 客观数据快照 JSON（与喂给 AI 的同源） */
  statsSnapshot: string | null
  createdAt: string | null
  updatedAt: string | null
}

/** null/不传 = 不改该字段 */
export interface WeeklyReportUpdatePayload {
  title?: string
  score?: number
  goodThings?: string
  badThings?: string
  learnings?: string
  suggestions?: string
}

export interface WeeklyReportUsage {
  periodStart: string
  periodEnd: string
  used: number
  limit: number
  remaining: number
  /** AI 总开关，false 时应隐藏生成按钮 */
  enabled: boolean
}
