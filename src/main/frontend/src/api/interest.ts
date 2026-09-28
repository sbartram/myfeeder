import { apiGet, apiPost, apiPut, apiDelete } from './client'
import type { LearnedLimit } from '../types'

export interface InterestProfile {
  id: number
  profileText: string
  version: number
  updatedAt: string
}

export interface InterestTopic {
  id: number
  name: string
  description: string
  weight: number
  version: number
  createdAt: string
  updatedAt: string
}

/**
 * One topic's learned adjustment from GET /api/interest/topics/learned (FDBK-07). The server
 * computes every number; the client prints them and never adds base and learned.
 */
export interface TopicLearned {
  topicId: number
  baseWeight: number
  learned: number
  effectiveWeight: number
  limit: LearnedLimit
}

/**
 * Badge tier thresholds served on /api/interest/status (D-13): a score at or above high is high,
 * at or above neutral is neutral, else low.
 */
export interface TierThresholds {
  high: number
  neutral: number
}

export interface InterestStatus {
  configured: boolean
  breakerState: string
  coldStart: boolean
  eligibleUnscored: number
  failed: number
  tiers?: TierThresholds
}

export interface RescoreCount {
  count: number
  windowDays: number
}

export interface TopicInput {
  name: string
  description: string
  weight: number
}

export interface TopicPreviewRequest {
  articleId: number
  description: string
  topicId: number | null
}

export interface TopicPreview {
  noul: number
  model: string
}

export const interestApi = {
  getStatus: () => apiGet<InterestStatus>('/interest/status'),
  getProfile: () => apiGet<InterestProfile>('/interest/profile'),
  saveProfile: (profileText: string) =>
    apiPut<InterestProfile>('/interest/profile', { profileText }),
  listTopics: () => apiGet<InterestTopic[]>('/interest/topics'),
  getLearned: () => apiGet<TopicLearned[]>('/interest/topics/learned'),
  createTopic: (input: TopicInput) => apiPost<InterestTopic>('/interest/topics', input),
  updateTopic: (id: number, input: TopicInput) =>
    apiPut<InterestTopic>(`/interest/topics/${id}`, input),
  deleteTopic: (id: number) => apiDelete(`/interest/topics/${id}`),
  preview: (request: TopicPreviewRequest) =>
    apiPost<TopicPreview>('/interest/preview', request),
  getRescoreCount: () => apiGet<RescoreCount>('/interest/rescore'),
  rescore: () => apiPost<RescoreCount>('/interest/rescore', { confirm: true }),
}
