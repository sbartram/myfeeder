import { useEffect, useState } from 'react'
import { usePreferences } from '../stores/preferencesStore'
import { integrationsApi, type RaindropCollection } from '../api/integrations'
import { themeList } from '../themes'

interface SettingsDialogProps {
  open: boolean
  onClose: () => void
}

type RaindropState =
  | { phase: 'loading' }
  | { phase: 'not-configured' }
  | { phase: 'ready'; collections: RaindropCollection[]; savedId: number | null; selectedId: number | null }
  | { phase: 'error'; message: string }

function readSavedCollectionId(configJson: string): number | null {
  try {
    const parsed = JSON.parse(configJson) as { collectionId?: number }
    return typeof parsed.collectionId === 'number' ? parsed.collectionId : null
  } catch {
    return null
  }
}

export function SettingsDialog({ open, onClose }: SettingsDialogProps) {
  const prefs = usePreferences()
  const [raindrop, setRaindrop] = useState<RaindropState>({ phase: 'loading' })

  useEffect(() => {
    if (!open) return
    let cancelled = false

    async function load() {
      try {
        const status = await integrationsApi.getRaindropStatus()
        if (cancelled) return
        if (!status.configured) {
          setRaindrop({ phase: 'not-configured' })
          return
        }
        const [collections, all] = await Promise.all([
          integrationsApi.listRaindropCollections(),
          integrationsApi.getAll(),
        ])
        if (cancelled) return
        const existing = all.find((c) => c.type === 'RAINDROP')
        const savedId = existing ? readSavedCollectionId(existing.config) : null
        const inList = savedId != null && collections.some((c) => c.id === savedId)
        setRaindrop({
          phase: 'ready',
          collections,
          savedId,
          selectedId: inList ? savedId : (collections[0]?.id ?? null),
        })
      } catch (e) {
        if (!cancelled) {
          setRaindrop({ phase: 'error', message: e instanceof Error ? e.message : String(e) })
        }
      }
    }

    setRaindrop({ phase: 'loading' })
    void load()
    return () => {
      cancelled = true
    }
  }, [open])

  if (!open) return null

  const handleSaveRaindrop = async () => {
    if (raindrop.phase !== 'ready' || raindrop.selectedId == null) return
    await integrationsApi.upsertRaindrop({ collectionId: raindrop.selectedId })
  }

  const renderRaindropSection = () => {
    if (raindrop.phase === 'loading') {
      return <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>Loading…</div>
    }
    if (raindrop.phase === 'not-configured') {
      return (
        <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>
          Raindrop is not configured by the administrator. Set <code>myfeeder.raindrop.api-token</code> in deployment values to enable.
        </div>
      )
    }
    if (raindrop.phase === 'error') {
      return (
        <div style={{ fontSize: 13, color: 'var(--text-error, crimson)' }}>
          Failed to load Raindrop settings: {raindrop.message}
        </div>
      )
    }
    const inList = raindrop.savedId != null && raindrop.collections.some((c) => c.id === raindrop.savedId)
    return (
      <>
        <label style={{ display: 'block', fontSize: 13, color: 'var(--text-muted)' }}>
          Raindrop collection
          <select
            aria-label="Raindrop collection"
            className="dialog-input"
            value={raindrop.selectedId ?? ''}
            onChange={(e) =>
              setRaindrop({ ...raindrop, selectedId: Number(e.target.value) })
            }
            style={{ marginTop: 4 }}
          >
            {!inList && raindrop.savedId != null && (
              <option value="" disabled>
                (saved collection no longer exists — pick again)
              </option>
            )}
            {raindrop.collections.map((c) => (
              <option key={c.id} value={c.id}>
                {c.title}
              </option>
            ))}
          </select>
        </label>
        <button className="btn-primary" onClick={handleSaveRaindrop} style={{ marginTop: 8 }}>
          Save Raindrop Config
        </button>
      </>
    )
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
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: 'var(--text-muted)', marginTop: 8 }}>
            <input
              type="checkbox"
              checked={prefs.hideReadFeeds}
              onChange={(e) => prefs.setHideReadFeeds(e.target.checked)}
            />
            Hide feeds with no unread articles
          </label>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: 'var(--text-muted)', marginTop: 8 }}>
            <input
              type="checkbox"
              checked={prefs.hideReadArticles}
              onChange={(e) => prefs.setHideReadArticles(e.target.checked)}
            />
            Hide read articles
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
          {renderRaindropSection()}
        </div>

        <div className="dialog-actions">
          <button className="btn-secondary" onClick={onClose}>Close</button>
        </div>
      </div>
    </div>
  )
}
