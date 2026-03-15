import { useState } from 'react'
import { useSubscribeFeed } from '../hooks/useFeeds'

interface AddFeedDialogProps {
  open: boolean
  onClose: () => void
}

export function AddFeedDialog({ open, onClose }: AddFeedDialogProps) {
  const [url, setUrl] = useState('')
  const subscribeFeed = useSubscribeFeed()

  if (!open) return null

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!url.trim()) return
    subscribeFeed.mutate(url.trim(), {
      onSuccess: () => {
        setUrl('')
        onClose()
      },
    })
  }

  return (
    <div className="dialog-overlay" onClick={onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <h2>Add Feed</h2>
        <form onSubmit={handleSubmit}>
          <input
            className="dialog-input"
            type="url"
            placeholder="https://example.com/feed.xml"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            autoFocus
          />
          {subscribeFeed.isError && (
            <p className="dialog-error">Failed to subscribe. Check the URL and try again.</p>
          )}
          <div className="dialog-actions">
            <button type="button" className="btn-secondary" onClick={onClose}>Cancel</button>
            <button type="submit" className="btn-primary" disabled={subscribeFeed.isPending}>
              {subscribeFeed.isPending ? 'Subscribing...' : 'Subscribe'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
