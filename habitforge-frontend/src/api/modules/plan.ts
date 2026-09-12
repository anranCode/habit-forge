import request from '@/api/request'
import type {
  DailyPlan,
  PlanBlock,
  PlanUsage,
  FreeSlot,
  FreeSlotSavePayload,
  BlockCreatePayload,
  BlockUpdatePayload
} from '@/types/plan'

// ============ 生成 / 查询 ============

/** 生成/重新生成今日安排（同步阻塞调 LLM，单请求超时放宽到 120s，仿 apiUploadImage 先例） */
export const apiGeneratePlan = (date?: string) =>
  request.post<never, DailyPlan>('/plans/generate', null, {
    params: date ? { date } : {},
    timeout: 120000
  })

/** 今日计划；未生成时 data=null（仿 /journals/today） */
export const apiPlanToday = () => request.get<never, DailyPlan | null>('/plans/today')

/** 按日期查历史计划 yyyy-MM-dd */
export const apiPlanByDate = (date: string) => request.get<never, DailyPlan>(`/plans/${date}`)

/** 今日生成用量 {used,remaining,todayTokens} */
export const apiPlanUsage = () => request.get<never, PlanUsage>('/plans/usage')

// ============ 空闲时段 ============

/** 整体覆盖式保存（事务内先删后插; ≤8 条、不重叠，违者 6014） */
export const apiSaveFreeSlots = (payload: FreeSlotSavePayload) =>
  request.put<never, FreeSlot[]>('/plans/free-slots', payload)

// ============ 采纳 / 块 CRUD ============

/** 一键采纳全部 PROPOSED 块，返回采纳条数 */
export const apiAdoptPlan = (date?: string) =>
  request.post<never, number>('/plans/adopt', null, { params: date ? { date } : {} })

/** 手动加块（创建即 ADOPTED） */
export const apiCreateBlock = (payload: BlockCreatePayload) =>
  request.post<never, PlanBlock>('/plans/blocks', payload)

export const apiUpdateBlock = (id: string, payload: BlockUpdatePayload) =>
  request.put<never, PlanBlock>(`/plans/blocks/${id}`, payload)

export const apiDeleteBlock = (id: string) => request.delete<never, void>(`/plans/blocks/${id}`)

// ============ 块状态机 ============

/** 完成块; checkinHabit=true 且有 habitId 时走完整 CheckinService 联动打卡（重复打卡幂等成功） */
export const apiCompleteBlock = (id: string, checkinHabit: boolean) =>
  request.post<never, PlanBlock>(`/plans/blocks/${id}/complete`, { checkinHabit })

/** 跳过（PROPOSED/ADOPTED 可跳） */
export const apiSkipBlock = (id: string) => request.post<never, PlanBlock>(`/plans/blocks/${id}/skip`)

/** 重开（DONE→ADOPTED） */
export const apiReopenBlock = (id: string) =>
  request.post<never, PlanBlock>(`/plans/blocks/${id}/reopen`)
