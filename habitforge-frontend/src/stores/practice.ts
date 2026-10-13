import { defineStore } from 'pinia'
import type { PracticeQuestion } from '@/types/question'

/**
 * 刷题会话队列（刻意不配 persist：内存态，刷新即丢，回来重新选题 —— 断点续刷不在需求内）
 */
export const usePracticeStore = defineStore('practice', {
  state: () => ({
    queue: [] as PracticeQuestion[],
    /** 选题范围文案，完成页/头部回显用 */
    scopeLabel: ''
  }),
  getters: {
    active: (s) => s.queue.length > 0
  },
  actions: {
    start(queue: PracticeQuestion[], scopeLabel: string) {
      this.queue = queue
      this.scopeLabel = scopeLabel
    },
    clear() {
      this.queue = []
      this.scopeLabel = ''
    }
  }
})
