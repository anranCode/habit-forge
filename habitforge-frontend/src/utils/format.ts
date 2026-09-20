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

/**
 * 桌面端的分类色：同色相降饱和。
 *
 * 上面那组是移动端的取值，逐像素基线的一部分，不能动。桌面端的极简标准是
 * "橙色唯一主色 + 灰阶为主"，五个饱和色相并排出现在一屏卡片上会喧宾夺主，
 * 所以只在桌面端换成低饱和版本 —— 仍然能区分分类，但不再抢内容。
 */
export const categoryColorDesktop: Record<Category, string> = {
  HEALTH: '#5a9e7f',
  LEARNING: '#6b8db5',
  WORK: '#8b6fa3',
  LIFE: '#c9903f',
  OTHER: '#9ba3a8'
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
