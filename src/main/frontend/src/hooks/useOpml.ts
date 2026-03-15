import { useMutation, useQueryClient } from '@tanstack/react-query'
import { opmlApi } from '../api/opml'
import { useToastStore } from '../components/Toast'

export function useImportOpml() {
  const qc = useQueryClient()
  const addToast = useToastStore((s) => s.addToast)

  return useMutation({
    mutationFn: (file: File) => opmlApi.importFeeds(file),
    onSuccess: (result) => {
      qc.invalidateQueries({ queryKey: ['feeds'] })
      qc.invalidateQueries({ queryKey: ['folders'] })
      addToast(
        `Imported ${result.created} new feeds, updated ${result.updated} existing`,
        'success',
      )
    },
    onError: () => {
      addToast('Failed to import OPML file')
    },
  })
}

export async function exportOpml() {
  try {
    await opmlApi.export()
  } catch {
    useToastStore.getState().addToast('Failed to export feeds')
  }
}
