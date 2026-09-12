import request from '@/api/request'
import type {
  Flashcard,
  FlashcardCreatePayload,
  FlashcardUpdatePayload,
  FlashcardStats,
  ReviewQueueResponse,
  ReviewResultResponse,
  ReviewRating
} from '@/types/flashcard'

export const apiCreateFlashcard = (data: FlashcardCreatePayload) =>
  request.post<never, Flashcard>('/flashcards', data)

/** 复习队列：到期在前、新卡补齐；可按科目过滤 */
export const apiReviewQueue = (params?: { subjectId?: string; limit?: number }) =>
  request.get<never, ReviewQueueResponse>('/flashcards/queue', { params })

export const apiReviewFlashcard = (id: string, rating: ReviewRating) =>
  request.post<never, ReviewResultResponse>(`/flashcards/${id}/review`, { rating })

export const apiFlashcardStats = () => request.get<never, FlashcardStats>('/flashcards/stats')

export const apiUpdateFlashcard = (id: string, data: FlashcardUpdatePayload) =>
  request.put<never, Flashcard>(`/flashcards/${id}`, data)

export const apiDeleteFlashcard = (id: string) => request.delete<never, void>(`/flashcards/${id}`)
