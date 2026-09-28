import type { Article } from '../types'
import { matchedTopics } from '../utils/feedback'
import type { TopicDraft } from './InterestsDialog'

interface FeedbackNoticeProps {
  article: Article
  onCreateTopic?: (draft: TopicDraft) => void
}

/**
 * The no-match line (D-18, D-19): shown while the open article has a vote, is scored and matched
 * no topic. "Create topic from article" hands a draft to the Interests dialog (D-20): the title,
 * trimmed and cut to 500 characters, with +20 after 👍 or −20 after 👎. Nothing is saved here and
 * the vote, the article and its score are untouched; the article is never re-judged (D-21).
 */
export function FeedbackNotice({ article, onCreateTopic }: FeedbackNoticeProps) {
  const feedback = article.feedback
  if (!feedback || article.interestScore == null || matchedTopics(article).length !== 0) return null

  const createTopic = () =>
    onCreateTopic?.({
      // 500 is the server's description limit (InterestService.MAX_DESCRIPTION_CHARS).
      description: article.title.trim().slice(0, 500),
      weight: feedback.vote === 1 ? 20 : -20,
    })

  return (
    <div className="feedback-notice" role="status">
      No topics matched —{' '}
      <button className="feedback-create" onClick={createTopic}>
        Create topic from article
      </button>
    </div>
  )
}
