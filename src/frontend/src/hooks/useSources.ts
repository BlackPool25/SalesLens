import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/lib/api-client'

// --- Types ---

export interface DataSourceResponse {
  id: string
  name: string
  sourceType: string
  trustScore: number
  active: boolean
  createdAt: string
}

export interface CreateSourceRequest {
  name: string
  sourceType: string
  trustScore: number
  active: boolean
  connectionConfig: string
}

export interface PaginatedResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
  first: boolean
  last: boolean
  empty: boolean
}

// --- Query keys ---

export const sourcesKeys = {
  all: ['sources'] as const,
  lists: () => [...sourcesKeys.all, 'list'] as const,
  list: (filters: { page: number; size: number }) =>
    [...sourcesKeys.lists(), filters] as const,
  details: () => [...sourcesKeys.all, 'detail'] as const,
  detail: (id: string) => [...sourcesKeys.details(), id] as const,
}

// --- Hooks ---

export function useSources(page = 0, size = 20) {
  return useQuery({
    queryKey: sourcesKeys.list({ page, size }),
    queryFn: async () => {
      const { data } = await apiClient.get<PaginatedResponse<DataSourceResponse>>(
        `/datasources/get-all-sources?page=${page}&size=${size}&sort=createdAt`,
      )
      return data
    },
  })
}

export function useSource(id: string) {
  return useQuery({
    queryKey: sourcesKeys.detail(id),
    queryFn: async () => {
      const { data } = await apiClient.get<DataSourceResponse>(
        `/datasources/get-by-id?id=${id}`,
      )
      return data
    },
    enabled: !!id,
  })
}

export function useCreateSource() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (request: CreateSourceRequest) => {
      const { data } = await apiClient.post<DataSourceResponse>(
        '/datasources/create-source',
        request,
      )
      return data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: sourcesKeys.lists() })
    },
  })
}
