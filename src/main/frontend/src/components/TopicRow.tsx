import { useEffect, useRef, useState } from 'react'
import type { InterestTopic, TopicLearned } from '../api/interest'
import { ApiError } from '../api/client'
import { formatDelta } from '../utils/feedback'
import {
  useCreateInterestTopic,
  useDeleteInterestTopic,
  usePreviewTopic,
  useUpdateInterestTopic,
} from '../hooks/useInterest'
import {
  formatPreviewText,
  formatSigned,
  hinge,
  isNegated,
  isTopicDirty,
  parseWeight,
  WEIGHT_MAX,
  WEIGHT_MIN,
} from '../utils/interest'

/**
 * One topic row as the dialog holds it. `key` is a stable client key (`t-<id>` for loaded
 * topics, `d-<n>` for drafts) that survives a draft being saved, so React keeps the same
 * row instance. `saved` is the row's own baseline (null for a draft); `weightText` is what
 * the numeric input shows, and `weight` the last valid whole number.
 */
export type TopicRowState = {
  key: string
  id: number | null
  name: string
  description: string
  weightText: string
  weight: number
  saved: { name: string; description: string; weight: number } | null
  /** The article this draft was created from; sent once, when the draft is first saved. */
  sourceArticleId?: number
}

/** Why Preview is unavailable for every row; InterestsDialog computes at most one (D-14). */
export type PreviewBlockKind = 'not-configured' | 'breaker' | 'no-article' | 'status-pending' | 'status-unknown'

export type PreviewBlock = { kind: PreviewBlockKind; reason: string }

/** The last successful preview, with the inputs it was judged on. */
type PreviewResult = { noul: number; description: string; articleId: number }

/** Blocks that outrank a blank description in the D-14 order. */
const LEADING_BLOCKS: PreviewBlockKind[] = ['not-configured', 'breaker', 'no-article']

function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1)
}

/**
 * Why this row's Preview is disabled, or null when it can run. First match wins: not configured,
 * circuit open, no article, blank description, status pending, status unknown (D-14, D-06).
 */
function previewDisabledReason(
  block: PreviewBlock | null,
  articleId: number | null,
  descriptionBlank: boolean,
): string | null {
  const effective: PreviewBlock | null =
    block ?? (articleId === null ? { kind: 'no-article', reason: 'open an article in the reading pane first.' } : null)
  if (effective && LEADING_BLOCKS.includes(effective.kind)) return capitalize(effective.reason)
  if (descriptionBlank) return 'Write a description first.'
  if (effective?.kind === 'status-pending') return 'Checking Jev status…'
  if (effective?.kind === 'status-unknown') return capitalize(effective.reason)
  return null
}

/** The UI-SPEC failure copy for a preview error, chosen by HTTP status and ProblemDetail title. */
function previewErrorMessage(error: Error): string {
  if (error instanceof ApiError) {
    if (error.status === 503 && error.title === 'Jev not configured') {
      return 'Preview failed: no TypeSafe API key is configured.'
    }
    if (error.status === 503 || error.status === 429 || error.status >= 500) {
      return 'Preview failed: Jev is unavailable right now. Try Preview topic again in a minute.'
    }
    if (error.status === 400 || error.status === 422) {
      return `Preview failed: Jev couldn't judge this article (${error.message}). Try rewording the description.`
    }
  }
  return `Preview failed: ${error.message}. Try Preview topic again.`
}

const NEGATION_DEBOUNCE_MS = 400

const NEGATION_WARNING =
  '⚠ This looks negated. Describe the subject positively (e.g. "about crypto") and give it a negative weight instead.'

function weightClass(weight: number): string {
  if (weight > 0) return 'weight-positive'
  if (weight < 0) return 'weight-negative'
  return 'weight-zero'
}

interface WeightControlProps {
  weightText: string
  weight: number
  onChange: (weightText: string, weight: number) => void
}

/** Slider and numeric input sharing one value, plus the sign label (D-10). */
export function WeightControl({ weightText, weight, onChange }: WeightControlProps) {
  const sign = weightClass(weight)
  return (
    <div className="interests-weight">
      <div className="interests-weight-slider">
        <input
          type="range"
          className={sign}
          min={WEIGHT_MIN}
          max={WEIGHT_MAX}
          step={1}
          aria-label="Topic weight"
          value={weight}
          onChange={(e) => onChange(e.target.value, Number(e.target.value))}
        />
        <div className="interests-scale-hint">
          <span>−50 bury</span>
          <span>0</span>
          <span>+50 boost</span>
        </div>
      </div>
      <input
        type="number"
        className="dialog-input interests-weight-number"
        min={WEIGHT_MIN}
        max={WEIGHT_MAX}
        step={1}
        aria-label="Topic weight value"
        value={weightText}
        onChange={(e) => {
          // Keep whatever was typed; only a valid whole number moves the slider (never clamp).
          const text = e.target.value
          onChange(text, parseWeight(text) ?? weight)
        }}
      />
      <span className={`interests-weight-sign ${sign}`}>{formatSigned(weight)}</span>
    </div>
  )
}

/** The name, else the description cut to 40 characters, else "new topic". */
function topicLabel(row: TopicRowState): string {
  const name = row.name.trim()
  if (name) return name
  const description = row.description.trim()
  if (description) return description.length > 40 ? `${description.slice(0, 40)}…` : description
  return 'new topic'
}

interface TopicPreviewResultProps {
  pending: boolean
  error: Error | null
  result: PreviewResult | null
  stale: boolean
  weight: number
}

/**
 * The preview slot under the row (D-13). The math is recomputed from the stored noul and the
 * row's current weight, so a weight change updates the points without a new call.
 */
function TopicPreviewResult({ pending, error, result, stale, weight }: TopicPreviewResultProps) {
  let content = null
  if (pending) {
    content = <span className="interests-preview-pending">Asking Jev…</span>
  } else if (error) {
    content = <div className="dialog-error">{previewErrorMessage(error)}</div>
  } else if (result) {
    const m = hinge(result.noul)
    if (m > 0) {
      const points = m * weight
      content = (
        <>
          {`Match ${Math.round(result.noul * 100)}% → counts ${Math.round(m * 100)}% × ${formatSigned(weight)} = `}
          <span className={`interests-preview-points ${weightClass(points)}`}>{formatSigned(points, 1)}</span>
          {' pts'}
        </>
      )
    } else {
      content = <span className="interests-preview-nomatch">{formatPreviewText(result.noul, weight)}</span>
    }
    if (stale) {
      content = (
        <>
          {content}
          <p>Description or article changed. Preview again to update.</p>
        </>
      )
    }
  }
  const showStale = stale && !pending && !error && result !== null
  return (
    <div className={showStale ? 'interests-preview-result stale' : 'interests-preview-result'} aria-live="polite">
      {content}
    </div>
  )
}

interface TopicRowProps {
  row: TopicRowState
  onChange: (row: TopicRowState) => void
  onSaved: (topic: InterestTopic) => void
  onDiscard: () => void
  onDeleted: () => void
  /** The article open in the reading pane, which Preview judges the row against. */
  articleId?: number | null
  previewBlock?: PreviewBlock | null
  /** This saved topic's learned values from the server, or undefined (loading, failed, a draft). */
  learned?: TopicLearned
}

/**
 * The read-only learned line under a saved row's weight (FDBK-07, D-14, D-15). It prints the
 * server's learned value split into its votes and engaged parts, then the effective weight; the
 * only client arithmetic is rounding. A part that rounds to zero tenths is omitted, and so are the
 * parentheses when both are. The votes part reads "at max" / "at min" at the learned cap; the
 * engaged part reads "at max" when engagement is at its cap, whichever limit is reported. While
 * the base weight has an unsaved edit, the effective weight isn't known, so the line says it
 * updates on save.
 */
function LearnedLine({ learned, baseEdited }: { learned: TopicLearned; baseEdited: boolean }) {
  const votes = learned.thumbsLearned ?? 0
  const engaged = learned.engagementLearned ?? 0
  const engagedAtMax = learned.engagementAtCap ?? learned.limit === 'ENGAGEMENT_CAP'
  const parts: string[] = []
  if (Math.round(votes * 10) !== 0) {
    const cap = learned.limit === 'LEARNED_CAP' ? (votes > 0 ? ' at max' : ' at min') : ''
    parts.push(`votes ${formatDelta(votes)}${cap}`)
  }
  if (Math.round(engaged * 10) !== 0) {
    parts.push(`engaged ${formatDelta(engaged)}${engagedAtMax ? ' at max' : ''}`)
  }
  const split = parts.length > 0 ? ` (${parts.join(', ')})` : ''
  const effSuffix =
    learned.limit === 'SIGN_CLAMP'
      ? " (can't cross 0)"
      : learned.limit === 'WEIGHT_RANGE'
        ? learned.effectiveWeight > 0
          ? ' (at the +50 limit)'
          : ' (at the −50 limit)'
        : ''
  const value = (text: string) => <span className="interests-learned-value">{text}</span>

  if (baseEdited) {
    return (
      <p className="interests-learned">
        Learned {value(formatSigned(learned.learned, 1))}
        {split} · Effective weight updates when you save
      </p>
    )
  }
  if (Math.round(learned.learned * 10) === 0) {
    return (
      <p className="interests-learned">
        No learned adjustment yet · Effective weight {value(formatSigned(learned.effectiveWeight))}
        {effSuffix}
      </p>
    )
  }
  return (
    <p className="interests-learned">
      Learned {value(formatSigned(learned.learned, 1))}
      {split} · Effective weight {value(formatSigned(learned.effectiveWeight, 1))}
      {effSuffix}
    </p>
  )
}

/**
 * A controlled topic row. Each row owns its mutations, so saving or deleting one row shows
 * "Saving…" and errors in that row only (D-08).
 */
export function TopicRow({
  row,
  onChange,
  onSaved,
  onDiscard,
  onDeleted,
  articleId = null,
  previewBlock = null,
  learned,
}: TopicRowProps) {
  const create = useCreateInterestTopic()
  const update = useUpdateInterestTopic()
  const remove = useDeleteInterestTopic()
  const preview = usePreviewTopic()
  const [previewResult, setPreviewResult] = useState<PreviewResult | null>(null)
  const [negated, setNegated] = useState(false)
  const [nameBlurredEmpty, setNameBlurredEmpty] = useState(false)
  const [descriptionBlurredEmpty, setDescriptionBlurredEmpty] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const typed = useRef({ name: false, description: false })
  const nameRef = useRef<HTMLInputElement>(null)
  // A draft only mounts when it's appended ("+ Add topic" or "Create topic from article"), so
  // focus its first input once and scroll it into view.
  const focusOnMount = useRef(row.saved === null)

  useEffect(() => {
    if (!focusOnMount.current) return
    nameRef.current?.focus()
    nameRef.current?.scrollIntoView?.({ block: 'nearest' })
  }, [])

  // D-09: advice only. It never changes the text and never affects Save.
  useEffect(() => {
    const timer = setTimeout(() => setNegated(isNegated(row.description)), NEGATION_DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [row.description])

  const isDraft = row.saved === null
  const dirty = isTopicDirty(row)
  const label = topicLabel(row)
  const weightValid = parseWeight(row.weightText) !== null
  const nameBlank = row.name.trim() === ''
  const descriptionBlank = row.description.trim() === ''
  const saving = create.isPending || update.isPending
  const canSave = dirty && weightValid && !nameBlank && !descriptionBlank && !saving
  const saveError = create.error ?? update.error

  const handleSave = () => {
    const input = { name: row.name.trim(), description: row.description.trim(), weight: row.weight }
    if (row.id === null) {
      create.mutate(
        row.sourceArticleId === undefined ? input : { ...input, sourceArticleId: row.sourceArticleId },
        { onSuccess: onSaved },
      )
    } else {
      update.mutate({ id: row.id, input }, { onSuccess: onSaved })
    }
  }

  // One request per click and never a retry (D-14): mutate runs only from this handler, never
  // from an effect or a timer. The draft text is sent as-is and nothing is persisted (D-12).
  const disabledReason = previewDisabledReason(previewBlock, articleId, descriptionBlank)
  const handlePreview = () => {
    if (articleId === null || disabledReason !== null) return
    const description = row.description.trim()
    preview.mutate(
      { articleId, description, topicId: row.id },
      { onSuccess: (result) => setPreviewResult({ noul: result.noul, description, articleId }) },
    )
  }
  // A weight-only change recomputes the math; a new description or article makes it stale.
  const previewStale =
    previewResult !== null &&
    (previewResult.description !== row.description.trim() || previewResult.articleId !== articleId)

  const handleDelete = () => {
    if (row.id !== null) remove.mutate(row.id, { onSuccess: onDeleted })
  }

  const errors: string[] = []
  if (!weightValid) errors.push('Weight must be a whole number from −50 to +50.')
  if (nameBlurredEmpty && nameBlank) errors.push('Write a short name before saving.')
  if (descriptionBlurredEmpty && descriptionBlank) errors.push('Write a description before saving.')
  if (saveError) {
    errors.push(
      `Couldn't save this topic: ${saveError.message}. Your changes are still here. Try Save topic again.`,
    )
  }
  if (remove.error) errors.push(`Couldn't delete this topic: ${remove.error.message}. Try again.`)

  return (
    <div className={dirty ? 'interests-topic-row dirty' : 'interests-topic-row'}>
      <div className="interests-topic-line">
        <input
          ref={nameRef}
          className="dialog-input interests-topic-name"
          aria-label="Topic name"
          maxLength={40}
          placeholder="e.g. Rust"
          value={row.name}
          onChange={(e) => {
            typed.current.name = true
            if (e.target.value.trim() !== '') setNameBlurredEmpty(false)
            onChange({ ...row, name: e.target.value })
          }}
          onBlur={() => setNameBlurredEmpty(typed.current.name && nameBlank)}
        />
        <input
          className="dialog-input interests-topic-description"
          aria-label="Topic description"
          maxLength={500}
          placeholder="e.g. The Rust programming language and its ecosystem"
          value={row.description}
          onChange={(e) => {
            typed.current.description = true
            if (e.target.value.trim() !== '') setDescriptionBlurredEmpty(false)
            onChange({ ...row, description: e.target.value })
          }}
          onBlur={() => setDescriptionBlurredEmpty(typed.current.description && descriptionBlank)}
        />
        {dirty && <span className="interests-unsaved-tag">Unsaved</span>}
      </div>
      {negated && (
        <div className="interests-warning" role="status">
          {NEGATION_WARNING}
        </div>
      )}
      <div className="interests-topic-line line-2">
        <WeightControl
          weightText={row.weightText}
          weight={row.weight}
          onChange={(weightText, weight) => onChange({ ...row, weightText, weight })}
        />
        {confirmingDelete ? (
          <div className="interests-row-actions interests-delete-confirm">
            <span>Delete this topic? Its scores and feedback are removed too. This can't be undone.</span>
            <button
              className="btn-secondary"
              aria-label={`Keep topic: ${label}`}
              onClick={() => setConfirmingDelete(false)}
            >
              Keep topic
            </button>
            <button
              className="btn-primary interests-danger"
              aria-label={`Confirm delete topic: ${label}`}
              disabled={remove.isPending}
              onClick={handleDelete}
            >
              {remove.isPending ? 'Deleting…' : 'Delete topic'}
            </button>
          </div>
        ) : (
          <div className="interests-row-actions">
            <button
              className="btn-secondary"
              aria-label={`Preview topic: ${label}`}
              disabled={preview.isPending || disabledReason !== null}
              title={disabledReason ?? undefined}
              onClick={handlePreview}
            >
              {preview.isPending ? 'Previewing…' : 'Preview topic'}
            </button>
            {dirty && (
              <button
                className="btn-primary"
                aria-label={`Save topic: ${label}`}
                disabled={!canSave}
                onClick={handleSave}
              >
                {saving ? 'Saving…' : 'Save topic'}
              </button>
            )}
            {isDraft ? (
              <button className="toolbar-btn" aria-label={`Discard draft: ${label}`} onClick={onDiscard}>
                Discard draft
              </button>
            ) : (
              <button
                className="toolbar-btn"
                aria-label={`Delete topic: ${label}`}
                onClick={() => setConfirmingDelete(true)}
              >
                Delete topic
              </button>
            )}
          </div>
        )}
      </div>
      {row.saved !== null && learned && (
        <LearnedLine learned={learned} baseEdited={row.weight !== row.saved.weight} />
      )}
      {errors.map((message) => (
        <div key={message} className="dialog-error">
          {message}
        </div>
      ))}
      <TopicPreviewResult
        pending={preview.isPending}
        error={preview.error}
        result={previewResult}
        stale={previewStale}
        weight={row.weight}
      />
    </div>
  )
}
