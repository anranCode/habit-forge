import request from '@/api/request'
import type {
  EnvironmentCategory,
  EnvironmentSetting,
  EnvironmentSettingPayload,
  EnvironmentSettingUpdatePayload
} from '@/types/environment'

export const apiEnvironmentSettings = (category?: EnvironmentCategory) =>
  request.get<never, EnvironmentSetting[]>('/environment-settings', { params: { category } })

export const apiCreateEnvironmentSetting = (data: EnvironmentSettingPayload) =>
  request.post<never, EnvironmentSetting>('/environment-settings', data)

/** 预设清单一键导入（重复描述后端自动跳过，可重复点击） */
export const apiBatchCreateEnvironmentSettings = (data: EnvironmentSettingPayload[]) =>
  request.post<never, EnvironmentSetting[]>('/environment-settings/batch', data)

export const apiUpdateEnvironmentSetting = (id: string, data: EnvironmentSettingUpdatePayload) =>
  request.put<never, EnvironmentSetting>(`/environment-settings/${id}`, data)

/** 勾选/取消 */
export const apiToggleEnvironmentSetting = (id: string, isActive: boolean) =>
  request.patch<never, EnvironmentSetting>(`/environment-settings/${id}/active`, { isActive })

export const apiDeleteEnvironmentSetting = (id: string) =>
  request.delete<never, void>(`/environment-settings/${id}`)
