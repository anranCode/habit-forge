import request from '@/api/request'
import type { WeeklyReport, WeeklyReportUpdatePayload, WeeklyReportUsage } from '@/types/report'

/** 某周报告，未生成时 data 为 null（不传 date = 本周；落在哪周取哪周，周一起算） */
export const apiWeeklyReport = (date?: string) =>
  request.get<never, WeeklyReport | null>('/reviews/weekly', { params: { date } })

/** 生成/重新生成（同步阻塞调 LLM，可能数十秒，单独放宽超时） */
export const apiGenerateWeeklyReport = (date?: string) =>
  request.post<never, WeeklyReport>('/reviews/weekly/generate', null, {
    params: { date },
    timeout: 180000
  })

/** 编辑（AI 只出初稿，终稿归用户） */
export const apiUpdateWeeklyReport = (id: string, data: WeeklyReportUpdatePayload) =>
  request.put<never, WeeklyReport>(`/reviews/weekly/${id}`, data)

/** 本周生成额度 */
export const apiWeeklyReportUsage = (date?: string) =>
  request.get<never, WeeklyReportUsage>('/reviews/weekly/usage', { params: { date } })
