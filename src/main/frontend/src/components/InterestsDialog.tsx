import { useEffect, useRef, useState } from 'react'
import type { InterestProfile, InterestStatus, InterestTopic } from '../api/interest'
import {
  useInterestProfile,
  useInterestStatus,
  useInterestTopics,
  useRescoreCount,
  useRescoreUnread,
  useSaveInterestProfile,
} from '../hooks/useInterest'
import { useArticle } from '../hooks/useArticles'
import { useUIStore } from '../stores/uiStore'
import { isTopicDirty } from '../utils/interest'
import { TopicRow, type PreviewBlock, type TopicRowState } from './TopicRow'

const PROFILE_MAX = 2000

const PROFILE_TIPS = [
  'Say what you want to read about.',
  'Be concrete: name technologies, people, projects and companies.',
  'Avoid negations. For things you dislike, add a topic with a negative weight.',
  'A few plain sentences work better than a list of keywords.',
]

const PROFILE_PLACEHOLDER =
  "I'm a backend engineer. I want in-depth articles about Java, Spring Boot and PostgreSQL performance, running Kubernetes at home, and practical uses of LLMs in developer tools. I like release notes for tools I use and post-mortems of real outages."

const OPEN_BREAKER_STATES = ['OPEN', 'FORCED_OPEN']

const TOPICS_MAX = 25

const TOPICS_HELP =
  'Describe each topic as what an article is primarily about. The weight sets how much a match moves the article: +50 boosts it strongly, −50 buries it.'

/** What the close guard names: "the profile", "1 topic", "N topics" or "the profile and N topics". */
function describeUnsaved(profileDirty: boolean, dirtyTopics: number): string {
  const topics = dirtyTopics === 1 ? '1 topic' : `${dirtyTopics} topics`
  if (profileDirty && dirtyTopics > 0) return `the profile and ${topics}`
  if (profileDirty) return 'the profile'
  return topics
}

interface InterestsDialogProps {
  open: boolean
  onClose: () => void
}

export function InterestsDialog({ open, onClose }: InterestsDialogProps) {
  if (!open) return null
  return <InterestsDialogBody onClose={onClose} />
}

function InterestsDialogBody({ onClose }: { onClose: () => void }) {
  const profile = useInterestProfile()
  const topics = useInterestTopics()
  const status = useInterestStatus()
  const [profileDirty, setProfileDirty] = useState(false)
  const [dirtyTopics, setDirtyTopics] = useState(0)
  const [confirmingClose, setConfirmingClose] = useState(false)

  // Esc is deliberately not bound: the global handler clears the preview target.
  const requestClose = () => {
    if (profileDirty || dirtyTopics > 0) {
      setConfirmingClose(true)
    } else {
      onClose()
    }
  }

  let content
  const loadError = profile.error ?? topics.error
  if (loadError) {
    content = (
      <div className="dialog-error">
        Couldn't load your interests: {loadError.message}. Close this dialog and open it again to retry.
      </div>
    )
  } else if (!profile.isSuccess || !topics.isSuccess) {
    content = <p className="interests-loading">Loading interests…</p>
  } else {
    content = (
      <>
        <InterestNotices status={status.data} />
        <ProfileEditor
          profile={profile.data}
          coldStart={status.data?.coldStart === true}
          onDirtyChange={setProfileDirty}
        />
        <TopicsSection
          topics={topics.data}
          status={status.data}
          statusFailed={status.isError}
          onDirtyCountChange={setDirtyTopics}
        />
        <RescoreFooter />
      </>
    )
  }

  return (
    <div className="dialog-overlay" onClick={requestClose}>
      <div
        className="dialog interests-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="interests-title"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 id="interests-title">Interests</h2>
        <p className="dialog-subtitle">
          Tell myfeeder what you want to read. Articles are ranked by how well they match your profile and topics.
        </p>
        <div className="interests-body">{content}</div>
        {confirmingClose ? (
          <div className="dialog-actions interests-confirm">
            <span>
              Discard unsaved changes? You have unsaved edits to {describeUnsaved(profileDirty, dirtyTopics)}.
            </span>
            <span className="interests-confirm-actions">
              <button className="btn-secondary" onClick={() => setConfirmingClose(false)}>
                Keep editing
              </button>
              <button className="btn-primary interests-danger" onClick={onClose}>
                Discard changes
              </button>
            </span>
          </div>
        ) : (
          <div className="dialog-actions">
            <button className="btn-secondary" onClick={requestClose}>Close</button>
          </div>
        )}
      </div>
    </div>
  )
}

/**
 * Status notices in fixed order: not configured, paused, cold start. Nothing is shown while
 * the status is loading or failed; editing never depends on it (D-06), and cold start comes
 * only from the server (D-05).
 */
function InterestNotices({ status }: { status: InterestStatus | undefined }) {
  if (!status) return null
  const notConfigured = status.configured === false
  const paused = OPEN_BREAKER_STATES.includes(status.breakerState)
  const coldStart = status.coldStart === true
  if (!notConfigured && !paused && !coldStart) return null

  return (
    <div className="interests-notices">
      {notConfigured && (
        <div className="interests-notice">
          <strong>Scoring isn't set up yet.</strong> No TypeSafe API key is configured, so articles
          won't be scored. You can still write your profile and topics now; they'll be used once the
          administrator sets <code>MYFEEDER_TYPESAFE_API_KEY</code>. Preview is unavailable until then.
        </div>
      )}
      {paused && (
        <div className="interests-notice">
          <strong>Jev is temporarily unavailable.</strong> Preview is paused until it recovers. Saving
          still works.
        </div>
      )}
      {coldStart && (
        <div className="interests-notice cold-start">
          <strong>Start here.</strong> Nothing is ranked until you write a profile or add at least one
          topic.
        </div>
      )}
    </div>
  )
}

/** "1 article" or "N articles". */
function articles(count: number): string {
  return count === 1 ? '1 article' : `${count} articles`
}

/**
 * The dialog footer: the note about newly arriving articles and the "Re-score unread" action
 * (D-01). The confirmation mounts only while confirming, so it always counts afresh (D-02).
 */
function RescoreFooter() {
  const [confirming, setConfirming] = useState(false)
  const [started, setStarted] = useState<number | null>(null)

  const openConfirm = () => {
    setStarted(null)
    setConfirming(true)
  }

  return (
    <>
      <div className="interests-rescore">
        <span className="interests-note">Profile and topic changes apply to newly arriving articles.</span>
        {!confirming && (
          <button className="btn-secondary" onClick={openConfirm}>
            Re-score unread
          </button>
        )}
      </div>
      {confirming && (
        <RescoreConfirm
          onCancel={() => setConfirming(false)}
          onDone={(count) => {
            setConfirming(false)
            setStarted(count)
          }}
        />
      )}
      {started !== null && (
        <p className="interests-waiting">Re-scoring started for {articles(started)}.</p>
      )}
    </>
  )
}

interface RescoreConfirmProps {
  onCancel: () => void
  onDone: (count: number) => void
}

/**
 * Inline, themed confirmation (D-02). "Counting articles…" shows while the count is fetching,
 * including the refetch on every mount, so a cached number is never shown or confirmed.
 */
function RescoreConfirm({ onCancel, onDone }: RescoreConfirmProps) {
  const count = useRescoreCount()
  const rescore = useRescoreUnread()

  if (count.isFetching || count.isPending) {
    return <p className="interests-waiting">Counting articles…</p>
  }
  if (count.isError) {
    return (
      <div className="dialog-actions interests-confirm">
        <span className="dialog-error">Couldn't count the articles to re-judge: {count.error.message}.</span>
        <span className="interests-confirm-actions">
          <button className="btn-secondary" onClick={onCancel}>
            Cancel
          </button>
        </span>
      </div>
    )
  }

  const { count: n, windowDays } = count.data
  if (n === 0) {
    return (
      <div className="dialog-actions interests-confirm">
        <span>Nothing to re-judge: no scored unread articles from the last {windowDays} days.</span>
        <span className="interests-confirm-actions">
          <button className="btn-secondary" onClick={onCancel}>
            OK
          </button>
        </span>
      </div>
    )
  }

  const confirm = () => rescore.mutate(undefined, { onSuccess: (result) => onDone(result.count) })

  return (
    <>
      <div className="dialog-actions interests-confirm">
        <span>
          Re-judge {n} unread {n === 1 ? 'article' : 'articles'} from the last {windowDays} days? Existing scores are replaced as they're re-scored.
        </span>
        <span className="interests-confirm-actions">
          <button className="btn-secondary" onClick={onCancel} disabled={rescore.isPending}>
            Cancel
          </button>
          <button className="btn-primary" onClick={confirm} disabled={rescore.isPending}>
            {rescore.isPending ? 'Re-scoring…' : 'Re-score'}
          </button>
        </span>
      </div>
      {rescore.isError && (
        <div className="dialog-error">Couldn't start the re-score: {rescore.error.message}</div>
      )}
    </>
  )
}

interface ProfileEditorProps {
  profile: InterestProfile
  coldStart: boolean
  onDirtyChange: (dirty: boolean) => void
}

/** Seeds its local state once from the loaded profile, so refetches never overwrite typed text. */
function ProfileEditor({ profile, coldStart, onDirtyChange }: ProfileEditorProps) {
  const [text, setText] = useState(profile.profileText)
  const [baseline, setBaseline] = useState(profile.profileText)
  const [savedOnce, setSavedOnce] = useState(false)
  const save = useSaveInterestProfile()
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const focusedForColdStart = useRef(false)

  const dirty = text !== baseline

  useEffect(() => {
    onDirtyChange(dirty)
  }, [dirty, onDirtyChange])

  useEffect(() => {
    if (coldStart && !focusedForColdStart.current) {
      focusedForColdStart.current = true
      textareaRef.current?.focus()
    }
  }, [coldStart])

  const atLimit = text.length >= PROFILE_MAX
  const counter = atLimit
    ? '2,000 / 2,000 · limit reached'
    : `${text.length.toLocaleString('en-US')} / 2,000`

  const handleSave = () => {
    save.mutate(text, {
      onSuccess: (saved) => {
        setBaseline(saved.profileText)
        setSavedOnce(true)
      },
    })
  }

  return (
    <section className="interests-section">
      <h3>Profile</h3>
      <ul className="interests-tips">
        {PROFILE_TIPS.map((tip) => (
          <li key={tip}>{tip}</li>
        ))}
      </ul>
      <textarea
        ref={textareaRef}
        className="dialog-input interests-profile"
        aria-label="Interest profile"
        rows={8}
        maxLength={2000}
        placeholder={PROFILE_PLACEHOLDER}
        value={text}
        onChange={(e) => setText(e.target.value)}
      />
      <div className="interests-meta">
        <span className={dirty ? 'interests-save-state dirty' : 'interests-save-state'}>
          {dirty ? 'Unsaved changes' : savedOnce ? 'Saved' : ''}
        </span>
        <span className={atLimit ? 'interests-counter at-limit' : 'interests-counter'}>{counter}</span>
      </div>
      <div className="interests-actions">
        <button className="btn-primary" onClick={handleSave} disabled={!dirty || save.isPending}>
          {save.isPending ? 'Saving…' : 'Save profile'}
        </button>
      </div>
      {save.isError && (
        <div className="dialog-error">
          Couldn't save the profile: {save.error.message}. Your text is still here. Try Save profile again.
        </div>
      )}
    </section>
  )
}

function seedRows(topics: InterestTopic[]): TopicRowState[] {
  return [...topics]
    .sort((a, b) => a.id - b.id)
    .map((t) => ({
      key: `t-${t.id}`,
      id: t.id,
      name: t.name,
      description: t.description,
      weightText: String(t.weight),
      weight: t.weight,
      saved: { name: t.name, description: t.description, weight: t.weight },
    }))
}

/**
 * The one reason Preview is unavailable for every row, or null (D-14, D-06). First match wins:
 * not configured, circuit open, no article, status still loading, status failed.
 */
function computePreviewBlock(
  status: InterestStatus | undefined,
  statusFailed: boolean,
  selectedArticleId: number | null,
): PreviewBlock | null {
  if (status?.configured === false) {
    return { kind: 'not-configured', reason: 'no TypeSafe API key is configured.' }
  }
  if (status && OPEN_BREAKER_STATES.includes(status.breakerState)) {
    return { kind: 'breaker', reason: 'Jev is temporarily unavailable. Try again in a minute.' }
  }
  if (selectedArticleId === null) {
    return { kind: 'no-article', reason: 'open an article in the reading pane first.' }
  }
  if (!status && !statusFailed) return { kind: 'status-pending', reason: '' }
  if (!status) return { kind: 'status-unknown', reason: "couldn't check Jev status." }
  return null
}

interface TopicsSectionProps {
  topics: InterestTopic[]
  status: InterestStatus | undefined
  statusFailed: boolean
  onDirtyCountChange: (count: number) => void
}

/**
 * Seeds its rows once from the loaded topics. After that, each row changes only through its own
 * callbacks, so a refetch or another row's save never overwrites unsaved edits (Pitfall 7).
 */
function TopicsSection({ topics, status, statusFailed, onDirtyCountChange }: TopicsSectionProps) {
  const [rows, setRows] = useState<TopicRowState[]>(() => seedRows(topics))
  // The preview target is read live and never written here (D-07, D-12).
  const selectedArticleId = useUIStore((s) => s.selectedArticleId)
  const article = useArticle(selectedArticleId)
  const previewBlock = computePreviewBlock(status, statusFailed, selectedArticleId)
  const draftCounter = useRef(0)

  const updateRow = (key: string, update: (row: TopicRowState) => TopicRowState) =>
    setRows((current) => current.map((r) => (r.key === key ? update(r) : r)))
  const removeRow = (key: string) => setRows((current) => current.filter((r) => r.key !== key))

  const addDraft = () => {
    draftCounter.current += 1
    setRows((current) => [
      ...current,
      {
        key: `d-${draftCounter.current}`,
        id: null,
        name: '',
        description: '',
        weightText: '20',
        weight: 20,
        saved: null,
      },
    ])
  }

  // The draft becomes a saved row in place: same key, same position, no re-sort (D-08).
  const markSaved = (key: string, topic: InterestTopic) =>
    updateRow(key, () => ({
      key,
      id: topic.id,
      name: topic.name,
      description: topic.description,
      weightText: String(topic.weight),
      weight: topic.weight,
      saved: { name: topic.name, description: topic.description, weight: topic.weight },
    }))

  // A blank draft never blocks closing; a draft with any text, or an edited saved row, does.
  const dirtyCount = rows.filter((r) =>
    r.saved === null ? r.name.trim() !== '' || r.description.trim() !== '' : isTopicDirty(r),
  ).length

  useEffect(() => {
    onDirtyCountChange(dirtyCount)
  }, [dirtyCount, onDirtyCountChange])

  const atMax = rows.length >= TOPICS_MAX

  return (
    <section className="interests-section">
      <div className="interests-topics-head">
        <h3>Topics</h3>
        <span className="interests-topic-count">
          {rows.length} / {TOPICS_MAX}
        </span>
      </div>
      <p className="interests-help">{TOPICS_HELP}</p>
      {previewBlock && previewBlock.kind !== 'status-pending' ? (
        <p className="interests-preview-target">Preview unavailable: {previewBlock.reason}</p>
      ) : article.data ? (
        <p className="interests-preview-target" title={article.data.title}>
          Previewing against: <span className="interests-preview-title">{article.data.title}</span>
        </p>
      ) : (
        <p className="interests-preview-target">Previewing against: loading article…</p>
      )}
      {rows.length === 0 ? (
        <>
          <p className="interests-empty-heading">No topics yet.</p>
          <p className="interests-help">
            Add a topic for each subject you want boosted or buried. You can also rank with the
            profile alone.
          </p>
        </>
      ) : (
        <div className="interests-topic-list">
          {rows.map((row) => (
            <TopicRow
              key={row.key}
              row={row}
              onChange={(next) => updateRow(row.key, () => next)}
              onSaved={(topic) => markSaved(row.key, topic)}
              onDiscard={() => removeRow(row.key)}
              onDeleted={() => removeRow(row.key)}
              articleId={selectedArticleId}
              previewBlock={previewBlock}
            />
          ))}
        </div>
      )}
      <button
        className="btn-secondary interests-add-topic"
        onClick={addDraft}
        disabled={atMax}
        title={atMax ? 'You have 25 topics, the maximum. Delete one to add another.' : undefined}
      >
        + Add topic
      </button>
    </section>
  )
}
