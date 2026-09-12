import request from '@/api/request'
import type {
  Subject,
  Chapter,
  ChapterStatus,
  StudyOverview,
  SubjectCreatePayload,
  SubjectUpdatePayload,
  ChapterCreatePayload,
  ChapterUpdatePayload
} from '@/types/study'

// ============ 科目 ============

export const apiSubjects = () => request.get<never, Subject[]>('/subjects')

export const apiSubjectDetail = (id: string) => request.get<never, Subject>(`/subjects/${id}`)

export const apiCreateSubject = (data: SubjectCreatePayload) =>
  request.post<never, Subject>('/subjects', data)

export const apiUpdateSubject = (id: string, data: SubjectUpdatePayload) =>
  request.put<never, Subject>(`/subjects/${id}`, data)

/** 逻辑删除 */
export const apiDeleteSubject = (id: string) => request.delete<never, void>(`/subjects/${id}`)

// ============ 章节 ============

/** 平铺返回该科目全部层级章节，前端按 parentId 组树 */
export const apiChaptersBySubject = (subjectId: string) =>
  request.get<never, Chapter[]>(`/chapters/subject/${subjectId}`)

export const apiCreateChapter = (data: ChapterCreatePayload) =>
  request.post<never, Chapter>('/chapters', data)

export const apiUpdateChapter = (id: string, data: ChapterUpdatePayload) =>
  request.put<never, Chapter>(`/chapters/${id}`, data)

/** 置 DONE 后端级联子孙；首次完成 +20 积分（重复置 DONE 不重复加分） */
export const apiChapterStatus = (id: string, status: ChapterStatus) =>
  request.patch<never, Chapter>(`/chapters/${id}/status`, { status })

/** 物理删除，DB 级联删子树 */
export const apiDeleteChapter = (id: string) => request.delete<never, void>(`/chapters/${id}`)

// ============ 总览 ============

export const apiStudyOverview = () => request.get<never, StudyOverview>('/study/overview')
