export type Category = 'HEALTH' | 'LEARNING' | 'WORK' | 'LIFE' | 'OTHER'
export type FrequencyType = 'DAILY' | 'WEEKLY_DAYS' | 'WEEKLY_COUNT'

export interface Habit {
  id: string
  name: string
  identityTag?: string
  category: Category
  habitType?: string
  frequencyType: FrequencyType
  frequencyDays?: string
  frequencyTarget?: number
  twoMinuteVersion?: string
  execTime?: string
  execPlace?: string
  stackAfter?: string
  isActive: boolean
  priority?: number
  createdAt?: string
  /** 列表附带字段 */
  currentStreak?: number
  longestStreak?: number
  checkedToday?: boolean
  missedYesterday?: boolean
  weekCheckedCount?: number
}

export interface HabitCreatePayload {
  name: string
  identityTag?: string
  category?: Category
  frequencyType?: FrequencyType
  frequencyDays?: string
  frequencyTarget?: number
  twoMinuteVersion?: string
  execTime?: string
  execPlace?: string
  stackAfter?: string
}

export interface HabitUpdatePayload extends Partial<HabitCreatePayload> {
  isActive?: number
  priority?: number
}

export interface HabitStats {
  totalHabits: number
  activeHabits: number
  totalCheckins: number
  monthCheckins: number
  monthScheduled: number
  monthCompletionRate: number
  longestStreakOverall: number
  currentStreakMax: number
}

export interface CheckinRecord {
  id: string
  habitId: string
  checkDate: string
  isCompleted: number
  note?: string
  createdAt?: string
}

export interface CheckinPayload {
  habitId: string
  checkDate?: string
  note?: string
}

export interface CheckinResponse {
  checkin: CheckinRecord
  streak: {
    currentStreak: number
    longestStreak: number
  }
  pointsEarned: number
  newAchievements: string[]
}

export interface StreakTopItem {
  habitId: string
  name: string
  identityTag?: string
  category: Category
  frequencyType: FrequencyType
  currentStreak: number
  longestStreak: number
  lastCheckDate?: string
}

export interface MonthCheckins {
  month: string
  dates: string[]
  total: number
}
