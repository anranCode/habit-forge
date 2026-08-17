import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', {
  state: () => ({
    activeTab: 0
  }),
  actions: {
    setTab(i: number) {
      this.activeTab = i
    }
  }
})
