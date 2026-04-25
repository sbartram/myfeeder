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
  articles: Article[]
  nextCursor: number | null
}

export interface ArticleFilters {
  feedId?: number
  read?: boolean
  starred?: boolean
  sort?: 'asc' | 'desc'
}
