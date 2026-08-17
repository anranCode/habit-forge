import request from '@/api/request'
import type {
  JournalDetail,
  JournalSummary,
  JournalCreatePayload,
  JournalUpdatePayload,
  JournalImage
} from '@/types/journal'

export const apiCreateJournal = (data: JournalCreatePayload) =>
  request.post<never, JournalDetail>('/journals', data)

/** 今日日记，未写时 data 为 null */
export const apiTodayJournal = () => request.get<never, JournalDetail | null>('/journals/today')

export const apiJournalsByRange = (from: string, to: string) =>
  request.get<never, JournalSummary[]>('/journals', { params: { from, to } })

export const apiJournalByDate = (date: string) =>
  request.get<never, JournalSummary[]>('/journals', { params: { date } })

export const apiJournalDetail = (id: string) => request.get<never, JournalDetail>(`/journals/${id}`)

export const apiUpdateJournal = (id: string, data: JournalUpdatePayload) =>
  request.put<never, JournalDetail>(`/journals/${id}`, data)

export const apiDeleteJournal = (id: string) => request.delete<never, void>(`/journals/${id}`)

export const apiLinkHabit = (journalId: string, habitId: string) =>
  request.post<never, JournalDetail>(`/journals/${journalId}/habits`, { habitId })

export const apiUnlinkHabit = (journalId: string, habitId: string) =>
  request.delete<never, void>(`/journals/${journalId}/habits/${habitId}`)

export const apiJournalsByHabit = (habitId: string) =>
  request.get<never, JournalSummary[]>(`/journals/habit/${habitId}`)

export const apiUploadImage = (journalId: string, file: File) => {
  const form = new FormData()
  form.append('file', file)
  return request.post<never, JournalImage>(`/journals/${journalId}/images`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}

export const apiDeleteImage = (journalId: string, imageId: string) =>
  request.delete<never, void>(`/journals/${journalId}/images/${imageId}`)
