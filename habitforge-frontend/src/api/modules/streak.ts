import request from '@/api/request'
import type { StreakTopItem } from '@/types/habit'

export const apiTopStreaks = (limit = 10) =>
  request.get<never, StreakTopItem[]>('/streaks/top', { params: { limit } })
