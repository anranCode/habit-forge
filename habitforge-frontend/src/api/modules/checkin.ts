import request from '@/api/request'
import type { CheckinPayload, CheckinResponse, CheckinRecord, MonthCheckins } from '@/types/habit'

export const apiCheckin = (data: CheckinPayload) =>
  request.post<never, CheckinResponse>('/checkins', data)

export const apiCheckinsByHabit = (habitId: string) =>
  request.get<never, CheckinRecord[]>(`/checkins/habit/${habitId}`)

export const apiMonthCheckins = (month?: string) =>
  request.get<never, MonthCheckins>('/checkins/month', { params: { month } })

export const apiCancelCheckin = (id: string) => request.delete<never, void>(`/checkins/${id}`)
