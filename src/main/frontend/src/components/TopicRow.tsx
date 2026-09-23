import { useEffect, useRef } from 'react'
import type { InterestTopic } from '../api/interest'
import { useCreateInterestTopic } from '../hooks/useInterest'
import { formatSigned } from '../utils/interest'

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

const WEIGHT_MIN = -50
const WEIGHT_MAX = 50

function weightClass(weight: number): string {
  if (weight > 0) return 'weight-positive'
  if (weight < 0) return 'weight-negative'
  return 'weight-zero'
}

/** A whole number from −50 to +50, or null (D-10). */
function parseWeight(text: string): number | null {
  if (!/^-?\d+$/.test(text.trim())) return null
  const n = Number(text)
  return n >= WEIGHT_MIN && n <= WEIGHT_MAX ? n : null
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

export function TopicRow({ row, onChange, onSaved, onDiscard }: TopicRowProps) {
  const create = useCreateInterestTopic()
  const nameRef = useRef<HTMLInputElement>(null)
  // A draft only mounts when "+ Add topic" appends it, so focus its first input once.
  const focusOnMount = useRef(row.saved === null)

  useEffect(() => {
    if (focusOnMount.current) nameRef.current?.focus()
  }, [])

  const isDraft = row.saved === null
  const dirty = isDraft
  const label = topicLabel(row)

  const handleSave = () => {
    const input = { name: row.name.trim(), description: row.description.trim(), weight: row.weight }
    create.mutate(input, { onSuccess: onSaved })
  }

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
          onChange={(e) => onChange({ ...row, name: e.target.value })}
        />
        <input
          className="dialog-input interests-topic-description"
          aria-label="Topic description"
          maxLength={500}
          placeholder="e.g. The Rust programming language and its ecosystem"
          value={row.description}
          onChange={(e) => onChange({ ...row, description: e.target.value })}
        />
        {dirty && <span className="interests-unsaved-tag">Unsaved</span>}
      </div>
      <div className="interests-topic-line line-2">
        <WeightControl
          weightText={row.weightText}
          weight={row.weight}
          onChange={(weightText, weight) => onChange({ ...row, weightText, weight })}
        />
        <div className="interests-row-actions">
          {dirty && (
            <button
              className="btn-primary"
              aria-label={`Save topic: ${label}`}
              disabled={create.isPending}
              onClick={handleSave}
            >
              {create.isPending ? 'Saving…' : 'Save topic'}
            </button>
          )}
          {isDraft && (
            <button className="toolbar-btn" aria-label={`Discard draft: ${label}`} onClick={onDiscard}>
              Discard draft
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
