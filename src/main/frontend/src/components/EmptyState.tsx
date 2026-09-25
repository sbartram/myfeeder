interface EmptyStateProps {
  message: string
  detail?: string
  action?: { label: string; onClick: () => void }
}

export function EmptyState({ message, detail, action }: EmptyStateProps) {
  return (
    <div className="empty-state">
      <p>{message}</p>
      {detail && <p className="empty-state-detail">{detail}</p>}
      {action && (
        <button className="btn-primary" onClick={action.onClick} style={{ marginTop: 12 }}>
          {action.label}
        </button>
      )}
    </div>
  )
}
