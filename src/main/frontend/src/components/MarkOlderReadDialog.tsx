import { useState } from 'react'

interface MarkOlderReadDialogProps {
  open: boolean
  feedName: string
  onConfirm: (days: number) => void
  onClose: () => void
}

const PRESETS = [1, 3, 7, 14]

export function MarkOlderReadDialog({ open, feedName, onConfirm, onClose }: MarkOlderReadDialogProps) {
  const [days, setDays] = useState<number | ''>('')

  if (!open) return null

  const handlePreset = (value: number) => {
    setDays(value)
  }

  const handleCustomChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value
    setDays(val === '' ? '' : Math.max(1, parseInt(val, 10) || 1))
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (days !== '' && days >= 1) {
      onConfirm(days)
      setDays('')
    }
  }

  const handleClose = () => {
    setDays('')
    onClose()
  }

  return (
    <div className="dialog-overlay" onClick={handleClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <h2>Mark older articles as read</h2>
        <p className="dialog-subtitle">
          Mark unread articles older than the selected number of days in <strong>{feedName}</strong>
        </p>
        <form onSubmit={handleSubmit}>
          <div className="preset-buttons">
            {PRESETS.map((p) => (
              <button
                key={p}
                type="button"
                className={`preset-btn ${days === p ? 'active' : ''}`}
                onClick={() => handlePreset(p)}
              >
                {p} days
              </button>
            ))}
          </div>
          <input
            className="dialog-input"
            type="number"
            min="1"
            step="1"
            placeholder="Custom"
            value={days}
            onChange={handleCustomChange}
          />
          <div className="dialog-actions">
            <button type="button" className="btn-secondary" onClick={handleClose}>Cancel</button>
            <button type="submit" className="btn-primary" disabled={days === '' || days < 1}>
              Mark as read
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
