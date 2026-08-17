import axios, { AxiosError } from 'axios'
import { showToast } from 'vant'
import type { ApiResult } from '@/types/api'
import { getToken, clearToken } from '@/utils/storage'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL as string,
  timeout: 15000
})

// 请求拦截：注入 token
request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：解包 data、统一错误提示
request.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResult
    if (body.code !== 200) {
      showToast(body.message || '请求失败')
      return Promise.reject(new Error(body.message))
    }
    return body.data as never
  },
  (error: AxiosError<ApiResult>) => {
    const status = error.response?.status
    const msg = error.response?.data?.message
    if (status === 401) {
      clearToken()
      // 同步清掉 pinia 持久化的登录态(见 stores/user.ts 的 persist key),
      // 否则过期 token 会在整页刷新后被恢复,导致 /login ↔ /home 无限重定向
      localStorage.removeItem('habitforge-user')
      showToast(msg || '登录已过期，请重新登录')
      if (!location.pathname.startsWith('/login') && !location.pathname.startsWith('/register')) {
        location.href = '/login'
      }
    } else if (status === 429) {
      showToast(msg || '操作太频繁，请稍后再试')
    } else {
      showToast(msg || error.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

export default request
