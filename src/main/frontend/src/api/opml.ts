const BASE_URL = '/api'

export interface OpmlImportResult {
  created: number
  updated: number
  total: number
}

export const opmlApi = {
  importFeeds: async (file: File): Promise<OpmlImportResult> => {
    const formData = new FormData()
    formData.append('file', file)
    const res = await fetch(`${BASE_URL}/opml/import`, {
      method: 'POST',
      body: formData,
    })
    if (!res.ok) throw new Error(`OPML import failed: ${res.status}`)
    return res.json()
  },

  export: async (): Promise<void> => {
    const res = await fetch(`${BASE_URL}/opml/export`)
    if (!res.ok) throw new Error(`OPML export failed: ${res.status}`)
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'myfeeder-export.opml'
    a.click()
    URL.revokeObjectURL(url)
  },
}
