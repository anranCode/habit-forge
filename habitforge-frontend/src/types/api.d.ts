/** 后端统一响应 */
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
  timestamp: string
}
