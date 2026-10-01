import request from '@/api/request'
import type { FocusDayPoint, FocusLog, FocusLogPayload, FocusTrend, UrgePayload } from '@/types/focus'

/** 今日注意力状态（含达标判定与当前上限） */
export const apiFocusToday = () => request.get<never, FocusLog>('/focus/today')

/** 录入/修改某日娱乐时长（达标状态变化会结算积分） */
export const apiSaveFocusLog = (data: FocusLogPayload) =>
  request.put<never, FocusLog>('/focus/logs', data)

/** 记一次「想刷手机」的冲动（只记今天） */
export const apiRecordUrge = (data: UrgePayload) =>
  request.post<never, FocusLog>('/focus/urges', data)

export const apiFocusTrend = (from: string, to: string) =>
  request.get<never, FocusTrend>('/focus/trend', { params: { from, to } })

/** 设置每日娱乐时长上限（分钟） */
export const apiUpdateFocusLimit = (limitMinutes: number) =>
  request.put<never, FocusLog>('/focus/limit', { limitMinutes })

export type { FocusDayPoint }
