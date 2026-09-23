import { useState } from 'react'
import type { InterestProfile } from '../api/interest'
import { useInterestProfile, useSaveInterestProfile } from '../hooks/useInterest'

const PROFILE_MAX = 2000

const PROFILE_TIPS = [
  'Say what you want to read about.',
  'Be concrete: name technologies, people, projects and companies.',
  'Avoid negations. For things you dislike, add a topic with a negative weight.',
  'A few plain sentences work better than a list of keywords.',
]

const PROFILE_PLACEHOLDER =
  "I'm a backend engineer. I want in-depth articles about Java, Spring Boot and PostgreSQL performance, running Kubernetes at home, and practical uses of LLMs in developer tools. I like release notes for tools I use and post-mortems of real outages."

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

  let content
  if (profile.isPending) {
    content = <p className="interests-loading">Loading interests…</p>
  } else if (profile.isError) {
    content = (
      <div className="dialog-error">
        Couldn't load your interests: {profile.error.message}. Close this dialog and open it again to retry.
      </div>
    )
  } else {
    content = (
      <>
        <ProfileEditor profile={profile.data} />
        <p className="interests-note">Profile and topic changes apply to newly arriving articles.</p>
      </>
    )
  }

  return (
    <div className="dialog-overlay" onClick={onClose}>
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
        <div className="dialog-actions">
          <button className="btn-secondary" onClick={onClose}>Close</button>
        </div>
      </div>
    </div>
  )
}

/** Seeds its local state once from the loaded profile, so refetches never overwrite typed text. */
function ProfileEditor({ profile }: { profile: InterestProfile }) {
  const [text, setText] = useState(profile.profileText)
  const [baseline, setBaseline] = useState(profile.profileText)
  const [savedOnce, setSavedOnce] = useState(false)
  const save = useSaveInterestProfile()

  const dirty = text !== baseline
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
