import request from '@/api/request'
import type { Habit, HabitCreatePayload, HabitUpdatePayload, HabitStats } from '@/types/habit'

export const apiListHabits = (activeOnly?: boolean) =>
  request.get<never, Habit[]>('/habits', { params: { activeOnly } })

export const apiTodayHabits = () => request.get<never, Habit[]>('/habits/today')

export const apiHabitStats = () => request.get<never, HabitStats>('/habits/stats')

export const apiHabitDetail = (id: string) => request.get<never, Habit>(`/habits/${id}`)

export const apiCreateHabit = (data: HabitCreatePayload) =>
  request.post<never, Habit>('/habits', data)

export const apiUpdateHabit = (id: string, data: HabitUpdatePayload) =>
  request.put<never, Habit>(`/habits/${id}`, data)

export const apiDeleteHabit = (id: string) => request.delete<never, void>(`/habits/${id}`)
