import type { Category, FrequencyType } from '@/types/habit'

export const categoryLabel: Record<Category, string> = {
  HEALTH: '健康',
  LEARNING: '学习',
  WORK: '工作',
  LIFE: '生活',
  OTHER: '其他'
}

export const categoryColor: Record<Category, string> = {
  HEALTH: '#27ae60',
  LEARNING: '#3498db',
  WORK: '#9b59b6',
  LIFE: '#f39c12',
  OTHER: '#7f8c8d'
}

export const categoryEmoji: Record<Category, string> = {
  HEALTH: '💪',
  LEARNING: '📚',
  WORK: '💼',
  LIFE: '🌿',
  OTHER: '✨'
}

/** 日记心情: 1好 2一般 3疲惫 */
export const moodEmoji = (m?: number | null): string => (m === 1 ? '😊' : m === 2 ? '😐' : m === 3 ? '😫' : '')
export const moodLabel = (m?: number | null): string => (m === 1 ? '好' : m === 2 ? '一般' : m === 3 ? '疲惫' : '')

/** 心得感受: 1好 2一般 3糟糕 */
export const feelingEmoji = (f?: number | null): string => (f === 1 ? '😊' : f === 2 ? '😐' : f === 3 ? '😫' : '')

export function frequencyLabel(type: FrequencyType, days?: string, target?: number): string {
  if (type === 'DAILY') return '每天'
  if (type === 'WEEKLY_COUNT') return `每周 ${target ?? 1} 次`
  if (type === 'WEEKLY_DAYS') {
    const map: Record<string, string> = { 1: '一', 2: '二', 3: '三', 4: '四', 5: '五', 6: '六', 7: '日' }
    const ds = (days || '')
      .split(',')
      .map((d) => map[d.trim()] || d)
      .join('、')
    return ds ? `每周${ds}` : '每周指定'
  }
  return '每天'
}
