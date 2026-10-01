import request from '@/api/request'
import type { Contract, ContractPayload, ContractStatus, ContractUpdatePayload } from '@/types/contract'

export const apiContracts = () => request.get<never, Contract[]>('/contracts')

export const apiCreateContract = (data: ContractPayload) =>
  request.post<never, Contract>('/contracts', data)

export const apiUpdateContract = (id: string, data: ContractUpdatePayload) =>
  request.put<never, Contract>(`/contracts/${id}`, data)

/** ACTIVE / COMPLETED(做到) / BROKEN(破戒) */
export const apiContractStatus = (id: string, status: ContractStatus) =>
  request.patch<never, Contract>(`/contracts/${id}/status`, { status })

export const apiDeleteContract = (id: string) => request.delete<never, void>(`/contracts/${id}`)
