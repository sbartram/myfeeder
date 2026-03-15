import { useEffect } from 'react'
import { usePreferences } from '../stores/preferencesStore'
import { themes } from '../themes'

export function useTheme() {
  const themeId = usePreferences((s) => s.theme)

  useEffect(() => {
    const theme = themes[themeId] ?? themes.midnight
    for (const [key, value] of Object.entries(theme.vars)) {
      document.documentElement.style.setProperty(key, value)
    }
  }, [themeId])
}
