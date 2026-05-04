interface ShortcutOverlayProps {
  open: boolean
  onClose: () => void
}

const shortcuts = [
  { key: 'j / k', action: 'Next / previous article' },
  { key: 'n / p', action: 'Next / previous feed' },
  { key: 'Enter', action: 'Open article' },
  { key: 'o', action: 'Open original URL' },
  { key: 'm', action: 'Toggle read / unread' },
  { key: 's', action: 'Toggle star' },
  { key: 'b', action: 'Add to board' },
  { key: 'v', action: 'Save to Raindrop' },
  { key: 'r', action: 'Refresh current feed' },
  { key: 'Shift+A', action: 'Mark all read in feed' },
  { key: '/', action: 'Focus search' },
  { key: 'Escape', action: 'Clear selection / close' },
  { key: 'g then a', action: 'Go to All Articles' },
  { key: 'g then s', action: 'Go to Starred' },
  { key: 'g then b', action: 'Go to Boards' },
  { key: 'Tab', action: 'Cycle panel focus' },
  { key: '+ / -', action: 'Increase / decrease focused panel font size' },
  { key: '?', action: 'Show this overlay' },
]

export function ShortcutOverlay({ open, onClose }: ShortcutOverlayProps) {
  if (!open) return null

  return (
    <div className="dialog-overlay" onClick={onClose}>
      <div className="dialog shortcut-overlay" onClick={(e) => e.stopPropagation()}>
        <h2>Keyboard Shortcuts</h2>
        <div className="shortcut-list">
          {shortcuts.map(({ key, action }) => (
            <div key={key} className="shortcut-row">
              <kbd className="shortcut-key">{key}</kbd>
              <span className="shortcut-action">{action}</span>
            </div>
          ))}
        </div>
        <div className="dialog-actions">
          <button className="btn-secondary" onClick={onClose}>Close</button>
        </div>
      </div>
    </div>
  )
}
