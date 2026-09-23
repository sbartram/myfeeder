import { useEffect, useRef, useState } from 'react'
import type { InterestTopic } from '../api/interest'
import {
  useCreateInterestTopic,
  useDeleteInterestTopic,
  useUpdateInterestTopic,
} from '../hooks/useInterest'
import {
  formatSigned,
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

interface TopicRowProps {
  row: TopicRowState
  onChange: (row: TopicRowState) => void
  onSaved: (topic: InterestTopic) => void
  onDiscard: () => void
  onDeleted: () => void
}

/**
 * A controlled topic row. Each row owns its mutations, so saving or deleting one row shows
 * "Saving…" and errors in that row only (D-08).
 */
export function TopicRow({ row, onChange, onSaved, onDiscard, onDeleted }: TopicRowProps) {
  const create = useCreateInterestTopic()
  const update = useUpdateInterestTopic()
  const remove = useDeleteInterestTopic()
  const [negated, setNegated] = useState(false)
  const [nameBlurredEmpty, setNameBlurredEmpty] = useState(false)
  const [descriptionBlurredEmpty, setDescriptionBlurredEmpty] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const typed = useRef({ name: false, description: false })
  const nameRef = useRef<HTMLInputElement>(null)
  // A draft only mounts when "+ Add topic" appends it, so focus its first input once.
  const focusOnMount = useRef(row.saved === null)

  useEffect(() => {
    if (focusOnMount.current) nameRef.current?.focus()
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
      create.mutate(input, { onSuccess: onSaved })
    } else {
      update.mutate({ id: row.id, input }, { onSuccess: onSaved })
    }
  }

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
      {errors.map((message) => (
        <div key={message} className="dialog-error">
          {message}
        </div>
      ))}
    </div>
  )
}
