import request from '@/api/request'
import type { Reflection, ReflectionPayload, ReflectionUpdatePayload } from '@/types/journal'

export const apiCreateReflection = (data: ReflectionPayload) =>
  request.post<never, Reflection>('/reflections', data)

export const apiUpdateReflection = (id: string, data: ReflectionUpdatePayload) =>
  request.put<never, Reflection>(`/reflections/${id}`, data)

export const apiDeleteReflection = (id: string) => request.delete<never, void>(`/reflections/${id}`)

export const apiReflectionsByJournal = (journalId: string) =>
  request.get<never, Reflection[]>(`/reflections/journal/${journalId}`)

export const apiReflectionsByHabit = (habitId: string) =>
  request.get<never, Reflection[]>(`/reflections/habit/${habitId}`)
