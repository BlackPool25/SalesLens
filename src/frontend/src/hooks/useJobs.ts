import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/lib/api-client'

// --- Types ---

export interface JobResponse {
  id: string
  sourceId: string
  sourceName: string
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'PARTIAL' | 'FAILED'
  recordsRead: number
  recordsTransformed: number
  recordsPassed: number
  recordsFailed: number
  recordsLoaded: number
  recordsConflicted: number
  createdAt: string
  updatedAt: string
}

interface PaginatedResponse<T> {
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

export const jobsKeys = {
  all: ['jobs'] as const,
  lists: () => [...jobsKeys.all, 'list'] as const,
  list: (filters: { page: number; size: number }) =>
    [...jobsKeys.lists(), filters] as const,
  details: () => [...jobsKeys.all, 'detail'] as const,
  detail: (id: string) => [...jobsKeys.details(), id] as const,
}

// --- Hooks ---

export function useJobs(page = 0, size = 20, refetchInterval: number | false = false) {
  return useQuery({
    queryKey: jobsKeys.list({ page, size }),
    queryFn: async () => {
      const { data } = await apiClient.get<PaginatedResponse<JobResponse>>(
        `/api/v1/jobs?page=${page}&size=${size}&sort=createdAt`,
      )
      return data
    },
    refetchInterval,
  })
}

export function useJob(id: string) {
  return useQuery({
    queryKey: jobsKeys.detail(id),
    queryFn: async () => {
      const { data } = await apiClient.get<JobResponse>(`/api/v1/jobs/${id}`)
      return data
    },
    enabled: !!id,
  })
}
