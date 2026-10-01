export type ContractStatus = 'ACTIVE' | 'COMPLETED' | 'BROKEN'

/** 习惯契约（问责伙伴 + 违约代价） */
export interface Contract {
  id: string
  habitId: string
  /** 习惯被删/归档时可能为 null */
  habitName: string | null
  partnerName: string
  penalty: string
  isPublic: boolean
  status: ContractStatus
  signedAt?: string
  updatedAt?: string
}

export interface ContractPayload {
  habitId: string
  partnerName: string
  penalty: string
  isPublic?: boolean
}

export interface ContractUpdatePayload {
  partnerName?: string
  penalty?: string
  isPublic?: boolean
  status?: ContractStatus
}
