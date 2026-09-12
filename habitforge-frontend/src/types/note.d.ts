export interface Note {
  id: string
  subjectId: string
  chapterId: string | null
  title: string
  /** Markdown 原文 */
  content: string
  createdAt: string
  updatedAt: string
}

/** 列表项：content 截断为摘要 */
export interface NoteSummary {
  id: string
  subjectId: string
  chapterId: string | null
  title: string
  excerpt: string
  updatedAt: string
}

export interface NoteImage {
  id: string
  objectKey: string
  originalName: string
  contentType: string
  fileSize: number
  width: number | null
  height: number | null
  sortOrder: number
}

export interface NoteCreatePayload {
  subjectId: string
  chapterId?: string
  /** ≤200 */
  title: string
  /** Markdown，可空 */
  content?: string
}

/** 各字段可选，不传 = 不改 */
export interface NoteUpdatePayload {
  chapterId?: string | null
  title?: string
  content?: string
}
