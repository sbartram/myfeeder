export interface Feed {
  id: number
  url: string
  title: string
  description: string | null
  siteUrl: string | null
  feedType: 'RSS' | 'ATOM' | 'JSON_FEED'
  pollIntervalMinutes: number
  lastPolledAt: string | null
  lastSuccessfulPollAt: string | null
  errorCount: number
  lastError: string | null
  etag: string | null
  lastModifiedHeader: string | null
  createdAt: string
  folderId: number | null
}

export interface Article {
  id: number
  feedId: number
  guid: string
  title: string
  url: string
  author: string | null
  content: string | null
  summary: string | null
  imageUrl: string | null
  publishedAt: string | null
  fetchedAt: string
  read: boolean
  starred: boolean
  /**
   * 0-100 display score from the server blend (D-18); null or absent when unscored.
   * Optional so existing fixtures still type-check; callers treat undefined like null.
   */
  interestScore?: number | null
  /** The exact score explanation; only GET /api/articles/{id} sends it, and only for a scored article. */
  interestBreakdown?: InterestBreakdown
  /**
   * The thumbs vote; null or absent when there is none. Only GET /api/articles/{id} and the vote
   * responses send it; optional so existing fixtures still type-check.
   */
  feedback?: ArticleFeedback | null
  /**
   * The engagement kinds, in server order; only GET /api/articles/{id} sends it ([] when none);
   * optional so existing fixtures still type-check (D-05).
   */
  engagement?: EngagementKind[]
}

/** How the user engaged with an article (server enum names). */
export type EngagementKind = 'OPEN_ORIGINAL' | 'STAR' | 'BOARD' | 'RAINDROP'

/** Which bound, if any, held a topic's learned adjustment back on the last vote. */
export type LearnedLimit = 'NONE' | 'LEARNED_CAP' | 'SIGN_CLAMP' | 'WEIGHT_RANGE'

/** An article's thumbs vote; topics are the narrowed picks (empty unless narrowed). */
export interface ArticleFeedback {
  vote: 1 | -1
  narrowed: boolean
  topics: { topicId: number; name: string }[]
}

/** One topic's effective weight before and after a vote, as the server computed it. */
export interface TopicEffect {
  topicId: number
  name: string
  before: number
  after: number
  baseWeight: number
  learned: number
  limit: LearnedLimit
}

/** The PUT/DELETE /api/articles/{id}/feedback response; effects are in ascending topicId order. */
export interface FeedbackResult {
  article: Article
  scored: boolean
  effects: TopicEffect[]
}

/** The profile's contribution; levelIndex 0-4 indexes PROFILE_LEVEL_LABELS. */
export interface ProfileBreakdownRow {
  kind: 'PROFILE'
  levelIndex: number
  exact: number
  points: number
}

/** A matched topic's contribution: noul is the match, hinge the part that counts. */
export interface TopicBreakdownRow {
  kind: 'TOPIC'
  topicId: number
  name: string
  noul: number
  hinge: number
  weight: number
  exact: number
  points: number
  /** The topic's base weight, without the learned part (D-11). */
  baseWeight?: number
  /** The applied learned part of weight (D-11). */
  learnedWeight?: number
}

export type BreakdownRow = ProfileBreakdownRow | TopicBreakdownRow

export interface NonMatchingTopic {
  topicId: number
  name: string
  noul: number
}

/**
 * Server-computed score explanation (plan 05-03). rows arrive in display order and their integer
 * points sum exactly to total; display equals the badge. The client renders, never recomputes.
 */
export interface InterestBreakdown {
  raw: number
  total: number
  display: number
  rows: BreakdownRow[]
  nonMatching: NonMatchingTopic[]
}

export interface Folder {
  id: number
  name: string
  displayOrder: number
  createdAt: string
}

export interface Board {
  id: number
  name: string
  description: string | null
  createdAt: string
}

export interface PaginatedArticles {
  items: Article[]
  nextCursor: number | null
}

// The Priority cursor is an opaque string (the served sort tuple), passed back verbatim as `before`.
export interface PriorityPage {
  items: Article[]
  nextCursor: string | null
}

export interface ExtractedContent {
  title: string
  contentHtml: string
}

export interface ArticleFilters {
  feedId?: number
  read?: boolean
  starred?: boolean
  sort?: 'asc' | 'desc'
}
