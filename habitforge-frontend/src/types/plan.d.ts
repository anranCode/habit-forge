/** 时间块类型 */
export type BlockType = 'HABIT' | 'STUDY' | 'REST' | 'OTHER'

/** 块状态机: PROPOSED→adopt→ADOPTED→complete→DONE→reopen→ADOPTED; PROPOSED/ADOPTED→skip→SKIPPED */
export type BlockStatus = 'PROPOSED' | 'ADOPTED' | 'DONE' | 'SKIPPED'

/** 空闲时段（整体覆盖式保存; label 为纯前端快捷标签预设） */
export interface FreeSlot {
  id?: string
  /** HH:mm */
  startTime: string
  /** HH:mm */
  endTime: string
  label: string
  sortOrder?: number
}

/** 计划块（habit/科目名为后端富化只读字段; habitCheckedToday 供「完成并打卡」反向标记） */
export interface PlanBlock {
  id: string
  planId: string
  blockType: BlockType
  title: string
  /** HH:mm */
  startTime: string
  /** HH:mm */
  endTime: string
  sortOrder: number
  status: BlockStatus
  /** AI 生成 / 手动添加（手动块创建即 ADOPTED） */
  source: 'AI' | 'MANUAL'
  habitId: string | null
  habitName: string | null
  /** 关联习惯今日是否已打卡（反向富化，块状态不自动联动） */
  habitCheckedToday: boolean
  subjectId: string | null
  subjectName: string | null
  chapterId: string | null
  /** ISO 时间，仅 DONE 非空 */
  completedAt: string | null
}

/** 每日计划（一天一份; 未生成时 GET /plans/today 返回 null） */
export interface DailyPlan {
  id: string
  /** yyyy-MM-dd */
  planDate: string
  /** 今日已生成次数 */
  genCount: number
  lastModel: string | null
  blocks: PlanBlock[]
  freeSlots: FreeSlot[]
}

/** 生成用量（/plans/usage） */
export interface PlanUsage {
  used: number
  remaining: number
  /** 今日累计 token 消耗 */
  todayTokens: number
}

/** 空闲时段保存（先删后插整体覆盖; 违者 6014） */
export interface FreeSlotSavePayload {
  /** yyyy-MM-dd，不传 = 今天 */
  date?: string
  slots: {
    /** HH:mm */
    startTime: string
    /** HH:mm */
    endTime: string
    label: string
  }[]
}

/** 手动新增块（MANUAL 块创建即 ADOPTED） */
export interface BlockCreatePayload {
  /** yyyy-MM-dd */
  date: string
  blockType: BlockType
  /** ≤100（AI prompt 约束中文≤30） */
  title: string
  /** HH:mm */
  startTime: string
  /** HH:mm */
  endTime: string
  habitId?: string
  subjectId?: string
  chapterId?: string
}

/** 编辑块（同 BlockCreatePayload 去 date; 不传 = 不改） */
export interface BlockUpdatePayload {
  blockType?: BlockType
  title?: string
  /** HH:mm */
  startTime?: string
  /** HH:mm */
  endTime?: string
  habitId?: string
  subjectId?: string
  chapterId?: string
}
