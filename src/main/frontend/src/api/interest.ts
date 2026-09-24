import { apiGet, apiPost, apiPut, apiDelete } from './client'

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

export interface InterestStatus {
  configured: boolean
  breakerState: string
  coldStart: boolean
  eligibleUnscored: number
  failed: number
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
  createTopic: (input: TopicInput) => apiPost<InterestTopic>('/interest/topics', input),
  updateTopic: (id: number, input: TopicInput) =>
    apiPut<InterestTopic>(`/interest/topics/${id}`, input),
  deleteTopic: (id: number) => apiDelete(`/interest/topics/${id}`),
  preview: (request: TopicPreviewRequest) =>
    apiPost<TopicPreview>('/interest/preview', request),
  getRescoreCount: () => apiGet<RescoreCount>('/interest/rescore'),
  rescore: () => apiPost<RescoreCount>('/interest/rescore'),
}
