import { defineStore } from 'pinia'
import { apiTodayHabits, apiListHabits, apiCreateHabit, apiUpdateHabit, apiDeleteHabit } from '@/api'
import type { Habit, HabitCreatePayload, HabitUpdatePayload } from '@/types/habit'

export const useHabitStore = defineStore('habit', {
  state: () => ({
    todayList: [] as Habit[],
    allList: [] as Habit[],
    loading: false
  }),
  actions: {
    async loadToday() {
      this.loading = true
      try {
        this.todayList = await apiTodayHabits()
      } finally {
        this.loading = false
      }
    },
    async loadAll(activeOnly?: boolean) {
      this.allList = await apiListHabits(activeOnly)
    },
    async create(payload: HabitCreatePayload) {
      return apiCreateHabit(payload)
    },
    async update(id: string, payload: HabitUpdatePayload) {
      return apiUpdateHabit(id, payload)
    },
    async remove(id: string) {
      await apiDeleteHabit(id)
      this.allList = this.allList.filter((h) => h.id !== id)
      this.todayList = this.todayList.filter((h) => h.id !== id)
    }
  }
})
