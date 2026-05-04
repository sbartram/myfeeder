import { useState } from 'react'
import { useSubscribeFeed } from '../hooks/useFeeds'
import { useFolders } from '../hooks/useFolders'

interface AddFeedDialogProps {
  open: boolean
  onClose: () => void
}

export function AddFeedDialog({ open, onClose }: AddFeedDialogProps) {
  const [url, setUrl] = useState('')
  const [folderId, setFolderId] = useState<string>('')
  const subscribeFeed = useSubscribeFeed()
  const { data: folders = [] } = useFolders()

  if (!open) return null

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!url.trim()) return
    subscribeFeed.mutate(
      { url: url.trim(), folderId: folderId === '' ? null : Number(folderId) },
      {
        onSuccess: () => {
          setUrl('')
          setFolderId('')
          onClose()
        },
      },
    )
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
          {folders.length > 0 && (
            <select
              className="dialog-input"
              value={folderId}
              onChange={(e) => setFolderId(e.target.value)}
              style={{ marginTop: 8 }}
            >
              <option value="">No folder</option>
              {folders.map((f) => (
                <option key={f.id} value={f.id}>{f.name}</option>
              ))}
            </select>
          )}
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
