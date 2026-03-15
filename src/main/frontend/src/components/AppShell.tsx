import { useCallback, useRef, type ReactNode, type MouseEvent } from 'react'
import { useUIStore } from '../stores/uiStore'
import { useTheme } from '../hooks/useTheme'

interface AppShellProps {
  feedPanel: ReactNode
  articleList: ReactNode
  readingPane: ReactNode
}

export function AppShell({ feedPanel, articleList, readingPane }: AppShellProps) {
  useTheme()
  const panelWidths = useUIStore((s) => s.panelWidths)
  const setPanelWidths = useUIStore((s) => s.setPanelWidths)
  const keyboardFocus = useUIStore((s) => s.keyboardFocus)
  const containerRef = useRef<HTMLDivElement>(null)

  const startResize = useCallback(
    (index: 0 | 1) => (e: MouseEvent) => {
      e.preventDefault()
      const startX = e.clientX
      const startWidths = [...panelWidths] as [number, number]

      const onMouseMove = (moveEvent: globalThis.MouseEvent) => {
        const delta = moveEvent.clientX - startX
        const newWidths = [...startWidths] as [number, number]
        newWidths[index] = Math.max(150, Math.min(400, startWidths[index] + delta))
        setPanelWidths(newWidths)
      }

      const onMouseUp = () => {
        document.removeEventListener('mousemove', onMouseMove)
        document.removeEventListener('mouseup', onMouseUp)
      }

      document.addEventListener('mousemove', onMouseMove)
      document.addEventListener('mouseup', onMouseUp)
    },
    [panelWidths, setPanelWidths]
  )

  const focusClass = (panel: string) =>
    keyboardFocus === panel ? 'panel panel-focused' : 'panel'

  return (
    <div className="app-shell" ref={containerRef}>
      <div className={focusClass('feeds')} style={{ width: panelWidths[0] }}>
        {feedPanel}
      </div>
      <div className="divider" onMouseDown={startResize(0)} />
      <div className={focusClass('articles')} style={{ width: panelWidths[1] }}>
        {articleList}
      </div>
      <div className="divider" onMouseDown={startResize(1)} />
      <div className={focusClass('reading')} style={{ flex: 1 }}>
        {readingPane}
      </div>
    </div>
  )
}
