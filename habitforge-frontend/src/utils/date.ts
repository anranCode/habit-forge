import dayjs from 'dayjs'

export const todayStr = (): string => dayjs().format('YYYY-MM-DD')

export const monthStr = (d?: dayjs.Dayjs): string => (d || dayjs()).format('YYYY-MM')

export const weekdayCn = (d: dayjs.Dayjs): string => {
  const names = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return names[d.day()]
}

export function greeting(): string {
  const h = dayjs().hour()
  if (h < 6) return '夜深了'
  if (h < 9) return '早安'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  if (h < 22) return '晚上好'
  return '夜深了'
}

/** 后端 ISO 时间 -> 可读 */
export const fmtDateTime = (s?: string): string => (s ? dayjs(s).format('YYYY-MM-DD HH:mm') : '-')

export const fmtDate = (s?: string): string => (s ? dayjs(s).format('YYYY-MM-DD') : '-')
