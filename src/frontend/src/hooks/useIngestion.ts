import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/lib/api-client'
import { jobsKeys } from './useJobs'

// --- Hooks ---

export function useUploadCsv() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({
      file,
      sourceId,
    }: {
      file: File
      sourceId: string
    }) => {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('sourceId', sourceId)
      const { data } = await apiClient.post('/api/v1/ingest/csv', formData)
      return data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: jobsKeys.lists() })
    },
  })
}

export function useUploadExcel() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({
      file,
      sourceId,
    }: {
      file: File
      sourceId: string
    }) => {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('sourceId', sourceId)
      const { data } = await apiClient.post('/api/v1/ingest/excel', formData)
      return data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: jobsKeys.lists() })
    },
  })
}
