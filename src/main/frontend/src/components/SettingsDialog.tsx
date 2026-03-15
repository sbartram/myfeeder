import { useState } from 'react'
import { usePreferences } from '../stores/preferencesStore'
import { integrationsApi } from '../api/integrations'
import { themeList } from '../themes'

interface SettingsDialogProps {
  open: boolean
  onClose: () => void
}

export function SettingsDialog({ open, onClose }: SettingsDialogProps) {
  const prefs = usePreferences()
  const [apiToken, setApiToken] = useState('')
  const [collectionId, setCollectionId] = useState('')

  if (!open) return null

  const handleSaveRaindrop = async () => {
    await integrationsApi.upsertRaindrop({
      apiToken,
      collectionId: Number(collectionId),
    })
  }

  return (
    <div className="dialog-overlay" onClick={onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()} style={{ width: 500 }}>
        <h2>Settings</h2>

        <div style={{ marginBottom: 20 }}>
          <h3 style={{ fontSize: 14, marginBottom: 8 }}>Appearance</h3>
          <label style={{ display: 'block', fontSize: 13, color: 'var(--text-muted)' }}>
            Theme
            <select
              className="dialog-input"
              value={prefs.theme}
              onChange={(e) => prefs.setTheme(e.target.value)}
              style={{ marginTop: 4 }}
            >
              {themeList.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name} ({t.type})
                </option>
              ))}
            </select>
          </label>
        </div>

        <div style={{ marginBottom: 20 }}>
          <h3 style={{ fontSize: 14, marginBottom: 8 }}>Reading</h3>
          <label style={{ display: 'block', marginBottom: 8, fontSize: 13, color: 'var(--text-muted)' }}>
            Auto-mark as read delay (ms, 0 to disable)
            <input
              className="dialog-input"
              type="number"
              value={prefs.autoMarkReadDelay}
              onChange={(e) => prefs.setAutoMarkReadDelay(Number(e.target.value))}
              style={{ marginTop: 4 }}
            />
          </label>
          <label style={{ display: 'block', fontSize: 13, color: 'var(--text-muted)' }}>
            Sort order
            <select
              className="dialog-input"
              value={prefs.articleSortOrder}
              onChange={(e) => prefs.setArticleSortOrder(e.target.value as 'newest-first' | 'oldest-first')}
              style={{ marginTop: 4 }}
            >
              <option value="newest-first">Newest first</option>
              <option value="oldest-first">Oldest first</option>
            </select>
          </label>
        </div>

        <div style={{ marginBottom: 20 }}>
          <h3 style={{ fontSize: 14, marginBottom: 8 }}>Raindrop.io</h3>
          <input
            className="dialog-input"
            placeholder="API Token"
            value={apiToken}
            onChange={(e) => setApiToken(e.target.value)}
            style={{ marginBottom: 8 }}
          />
          <input
            className="dialog-input"
            placeholder="Collection ID"
            value={collectionId}
            onChange={(e) => setCollectionId(e.target.value)}
          />
          <button className="btn-primary" onClick={handleSaveRaindrop} style={{ marginTop: 8 }}>
            Save Raindrop Config
          </button>
        </div>

        <div className="dialog-actions">
          <button className="btn-secondary" onClick={onClose}>Close</button>
        </div>
      </div>
    </div>
  )
}
