import type { QuestionType } from '@/types/question'

/**
 * 题型/来源的中文标签与标签色 —— 以前散在 4 个文件里各抄一份
 * （QuestionCard / QuestionEdit / WrongQuestionItem / QuestionBank），改一处漏三处。
 * 这里只放"值"；断点切换（桌面降饱和）仍由使用方用 useIsDesktop() 选表，
 * 因为 Vant tag 的 color 是直接写进内联样式的，CSS 规则够不着。
 *
 * PRACTICE（刷题）组件也要用同一批表，见 PracticeQuestionCard.vue。
 */

/** 题型 → 中文短标签（tag 用）。注意 SHORT 显示"主观"，下拉里是"主观题"，由下面派生 */
export const TYPE_LABEL: Record<QuestionType, string> = {
  SINGLE: '单选',
  MULTI: '多选',
  JUDGE: '判断',
  SHORT: '主观'
}

/** 移动端题型 tag 色（原色相） */
export const TYPE_COLOR: Record<QuestionType, string> = {
  SINGLE: '#ff7a00',
  MULTI: '#3498db',
  JUDGE: '#27ae60',
  SHORT: '#9b59b6'
}

/** 桌面端：同色相降饱和，与 format.ts 的 categoryColorDesktop 同一套标准 */
export const TYPE_COLOR_DESKTOP: Record<QuestionType, string> = {
  SINGLE: '#ff7a00',
  MULTI: '#6b8db5',
  JUDGE: '#5a9e7f',
  SHORT: '#8b6fa3'
}

export const SOURCE_LABEL: Record<string, string> = {
  PAST_EXAM: '真题',
  TEXTBOOK: '教材',
  AI: 'AI 生成',
  CUSTOM: '自定义'
}

/** 来源 tag 色（信息蓝，走 --hf-info 同值：移动端 #3498db / 桌面 #6b8db5） */
export const SOURCE_COLOR = { mobile: '#3498db', desktop: '#6b8db5' } as const

/** 题型顺序：录题 radio、题库下拉、批量选项都以它为准 */
export const TYPE_ORDER: QuestionType[] = ['SINGLE', 'MULTI', 'JUDGE', 'SHORT']

const SOURCE_ORDER = ['CUSTOM', 'PAST_EXAM', 'TEXTBOOK', 'AI']

/** 录题表单的 radio 选项（QuestionEdit 用） */
export const TYPE_OPTIONS = TYPE_ORDER.map((v) => ({ value: v, label: TYPE_LABEL[v] }))

/** 录题表单的来源 radio 选项（CUSTOM 在前：新题默认"自定义"） */
export const SOURCE_OPTIONS = SOURCE_ORDER.map((v) => ({ value: v, label: SOURCE_LABEL[v] }))

/** 题库来源下拉自己的顺序（真题在前）——与录题 radio 不同序是原有行为，各保各的 */
const SOURCE_DROPDOWN_ORDER = ['PAST_EXAM', 'TEXTBOOK', 'CUSTOM', 'AI']

/** 题库筛选下拉（van-dropdown-item 的 {text,value} 形状，含"全部"项） */
export const TYPE_DROPDOWN_OPTIONS = [{ text: '全部题型', value: '' }].concat(
  TYPE_ORDER.map((v) => ({ text: TYPE_LABEL[v] + '题', value: v }))
)

export const SOURCE_DROPDOWN_OPTIONS = [{ text: '全部来源', value: '' }].concat(
  SOURCE_DROPDOWN_ORDER.map((v) => ({ text: SOURCE_LABEL[v], value: v }))
)
