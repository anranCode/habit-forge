/** 某日注意力状态（手机节制） */
export interface FocusLog {
  logDate: string
  /** null = 尚未录入（不算达标） */
  entertainmentMinutes: number | null
  pickups: number | null
  note: string | null
  urgeTotal: number
  urgeResisted: number
  /** 忍住率百分比；无冲动记录时为 null */
  resistRatePercent: number | null
  /** 当前生效的每日娱乐时长上限（分钟） */
  limitMinutes: number
  compliant: boolean
  /** 距上限还剩多少分钟；未录入时为 null */
  remainMinutes: number | null
}

/** 单日数据点（趋势图用；未录入的日子 minutes 为 null，画断点而不是 0） */
export interface FocusDayPoint {
  date: string
  entertainmentMinutes: number | null
  compliant: boolean
  urgeTotal: number
  urgeResisted: number
}

export interface FocusTrend {
  from: string
  to: string
  limitMinutes: number
  totalDays: number
  recordedDays: number
  compliantDays: number
  avgEntertainmentMinutes: number | null
  totalEntertainmentMinutes: number
  urgeTotal: number
  urgeResisted: number
  resistRatePercent: number | null
  days: FocusDayPoint[]
}

export interface FocusLogPayload {
  /** 不传 = 今天 */
  logDate?: string
  entertainmentMinutes: number
  pickups?: number
  note?: string
}

export interface UrgePayload {
  /** true = 忍住了 */
  resisted: boolean
  count?: number
}
