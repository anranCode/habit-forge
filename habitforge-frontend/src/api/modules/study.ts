import request from '@/api/request'
import type {
  Subject,
  Chapter,
  ChapterStatus,
  StudyOverview,
  SubjectCreatePayload,
  SubjectUpdatePayload,
  ChapterCreatePayload,
  ChapterUpdatePayload,
  StudySession,
  StudySessionManualPayload,
  DailyStudyTime,
  StudyTimeSummary
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
// ============ 学习时长（P0 计时） ============

/** 开始计时；已有进行中的计时后端返 7010 */
export const apiStartStudy = (data: { subjectId?: string; chapterId?: string } = {}) =>
  request.post<never, StudySession>('/study/sessions/start', data)

/** 结束计时并回填分钟（超过 12 小时返 7012，需删除后手动补录） */
export const apiEndStudy = (id: string) =>
  request.post<never, StudySession>(`/study/sessions/${id}/end`)

/** 手动补录（漏计时/离线学习） */
export const apiManualStudy = (data: StudySessionManualPayload) =>
  request.post<never, StudySession>('/study/sessions', data)

/** 进行中的计时，无则 data=null（刷新页面后据此恢复计时） */
export const apiActiveStudy = () => request.get<never, StudySession | null>('/study/sessions/active')

/** 某日全部记录（不传 date = 今天） */
export const apiStudySessions = (date?: string) =>
  request.get<never, StudySession[]>('/study/sessions', { params: { date } })

export const apiDeleteStudySession = (id: string) =>
  request.delete<never, void>(`/study/sessions/${id}`)

/** 某日学习时长汇总（不传 date = 今天） */
export const apiStudyTimeSummary = (date?: string) =>
  request.get<never, StudyTimeSummary>('/study/time/summary', { params: { date } })

/** 区间内每日时长（无记录的日子补 0） */
export const apiStudyTimeDaily = (from: string, to: string) =>
  request.get<never, DailyStudyTime[]>('/study/time/daily', { params: { from, to } })
