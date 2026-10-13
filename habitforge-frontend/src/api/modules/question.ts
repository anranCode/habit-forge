import request from '@/api/request'
import type {
  Question,
  QuestionCreatePayload,
  QuestionUpdatePayload,
  QuestionQuery,
  PageResult,
  WrongQuestion,
  PracticeQuestion,
  RandomQuery,
  GradePayload,
  GradeResult
} from '@/types/question'

export const apiCreateQuestion = (data: QuestionCreatePayload) =>
  request.post<never, Question>('/questions', data)

/** 题库分页查询 */
export const apiQuestions = (q: QuestionQuery) =>
  request.get<never, PageResult<Question>>('/questions', { params: q })

export const apiQuestion = (id: string) => request.get<never, Question>(`/questions/${id}`)

export const apiUpdateQuestion = (id: string, data: QuestionUpdatePayload) =>
  request.put<never, Question>(`/questions/${id}`, data)

export const apiDeleteQuestion = (id: string) => request.delete<never, void>(`/questions/${id}`)

/** 加入错题本（已存在则 wrongCount+1、mastered 复位） */
export const apiAddWrong = (questionId: string) =>
  request.post<never, WrongQuestion>('/wrong-questions', { questionId })

/** reveal=false 时后端把每题的 answer/analysis 置 null —— 重练队列专用，防透题 */
export const apiWrongList = (params?: { subjectId?: string; limit?: number; reveal?: boolean }) =>
  request.get<never, WrongQuestion[]>('/wrong-questions', { params })

export const apiSetWrongMastered = (questionId: string, mastered: boolean) =>
  request.patch<never, WrongQuestion>(`/wrong-questions/${questionId}/mastered`, { mastered })

// ============ 刷题 / 判分 / 考点（功能A/B）============

/** 随机取题（整个题库 / 按科目 / 按章节）；下发形状不含 answer/analysis */
export const apiRandomQuestions = (q: RandomQuery) =>
  request.get<never, PracticeQuestion[]>('/questions/random', { params: { ...q } })

/** 提交作答：客观题服务端秒判；主观题同步走 AI 判分（最坏 ~90s，超时放宽照 apiGeneratePlan 先例） */
export const apiGradeQuestion = (p: GradePayload) =>
  request.post<never, GradeResult>('/questions/grade', p, { timeout: 120000 })

/** AI 识别单题考点（同步 LLM 调用；批量 = 前端循环调它，逐题计费逐题容错） */
export const apiRecognizePoints = (id: string) =>
  request.post<never, Question>(`/questions/${id}/recognize-points`, undefined, { timeout: 120000 })

/** 考点去重清单（题库筛选下拉的选项源；subjectId 空 = 全库） */
export const apiKnowledgePoints = (subjectId?: string) =>
  request.get<never, string[]>('/questions/knowledge-points', { params: subjectId ? { subjectId } : {} })
