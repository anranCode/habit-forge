import request from '@/api/request'
import type {
  Note,
  NoteSummary,
  NoteImage,
  NoteCreatePayload,
  NoteUpdatePayload
} from '@/types/note'

export const apiCreateNote = (data: NoteCreatePayload) => request.post<never, Note>('/notes', data)

/** 笔记列表（摘要），可按科目/章节/关键词过滤 */
export const apiNotes = (params?: { subjectId?: string; chapterId?: string; keyword?: string }) =>
  request.get<never, NoteSummary[]>('/notes', { params })

export const apiNote = (id: string) => request.get<never, Note>(`/notes/${id}`)

export const apiUpdateNote = (id: string, data: NoteUpdatePayload) =>
  request.put<never, Note>(`/notes/${id}`, data)

export const apiDeleteNote = (id: string) => request.delete<never, void>(`/notes/${id}`)

export const apiUploadNoteImage = (id: string, file: File) => {
  const form = new FormData()
  form.append('file', file)
  return request.post<never, NoteImage>(`/notes/${id}/images`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}

export const apiNoteImages = (id: string) => request.get<never, NoteImage[]>(`/notes/${id}/images`)

export const apiDeleteNoteImage = (noteId: string, imageId: string) =>
  request.delete<never, void>(`/notes/${noteId}/images/${imageId}`)
