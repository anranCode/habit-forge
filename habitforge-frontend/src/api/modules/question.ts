import request from '@/api/request'
import type {
  Question,
  QuestionCreatePayload,
  QuestionUpdatePayload,
  QuestionQuery,
  PageResult,
  WrongQuestion
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

export const apiWrongList = (params?: { subjectId?: string; limit?: number }) =>
  request.get<never, WrongQuestion[]>('/wrong-questions', { params })

/** 错题练习登记：correct 连对 2 次自动 mastered */
export const apiPracticeWrong = (questionId: string, correct: boolean) =>
  request.post<never, WrongQuestion>(`/wrong-questions/${questionId}/practice`, { correct })

export const apiSetWrongMastered = (questionId: string, mastered: boolean) =>
  request.patch<never, WrongQuestion>(`/wrong-questions/${questionId}/mastered`, { mastered })
