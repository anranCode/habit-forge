import { defineStore } from 'pinia'
import { apiCheckin } from '@/api'
import type { CheckinPayload, CheckinResponse } from '@/types/habit'

export const useCheckinStore = defineStore('checkin', {
  state: () => ({
    submitting: false,
    lastResult: null as CheckinResponse | null
  }),
  actions: {
    async checkin(payload: CheckinPayload): Promise<CheckinResponse> {
      this.submitting = true
      try {
        const res = await apiCheckin(payload)
        this.lastResult = res
        return res
      } finally {
        this.submitting = false
      }
    }
  }
})
