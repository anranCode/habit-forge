export type EnvironmentType = 'PROMPT' | 'RESISTANCE' | 'COMMITMENT'
export type EnvironmentCategory = 'PHONE' | 'HABIT' | 'OTHER'

/** 环境设计清单项 */
export interface EnvironmentSetting {
  id: string
  type: EnvironmentType
  category: EnvironmentCategory
  description: string
  targetHabitId: string | null
  /** 清单勾选状态 */
  isActive: boolean
  createdAt?: string
  updatedAt?: string
}

export interface EnvironmentSettingPayload {
  type?: EnvironmentType
  category?: EnvironmentCategory
  description: string
  targetHabitId?: string
  isActive?: boolean
}

export interface EnvironmentSettingUpdatePayload {
  type?: EnvironmentType
  category?: EnvironmentCategory
  description?: string
  targetHabitId?: string
  isActive?: boolean
}
