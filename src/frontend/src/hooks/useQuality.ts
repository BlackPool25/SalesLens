import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/lib/api-client'

// ─── Types ──────────────────────────────────────────────────────────────────

export interface QualityScoreDto {
  sourceId: string
  scoreCompleteness: number
  scoreValidity: number
  scoreUniqueness: number
  scoreConsistency: number
  scoreTimeliness: number
  scoreAccuracy: number
  overallScore: number
  letterGrade: 'A' | 'B' | 'C' | 'D' | 'F'
  totalRecordsScored: number
  averageScore: number
  openIssues: number
  createdAt: string
}

export interface QualityIssueDto {
  id: string
  sourceFieldName: string
  ruleCode: string
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  dimension:
    | 'COMPLETENESS'
    | 'VALIDITY'
    | 'UNIQUENESS'
    | 'CONSISTENCY'
    | 'TIMELINESS'
    | 'ACCURACY'
  message: string
  status: 'OPEN' | 'ACKNOWLEDGED'
  createdAt: string
}

export interface QualityIssuesFilters {
  sourceId?: string
  severity?: string
  dimension?: string
  status?: string
  page?: number
  size?: number
}

/** Shape returned by Spring Data Page<T> JSON serialisation. */
interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
  first: boolean
  last: boolean
}

// ─── Query keys ─────────────────────────────────────────────────────────────

export const qualityKeys = {
  all: ['quality'] as const,
  scores: () => [...qualityKeys.all, 'scores'] as const,
  score: (sourceId: string) => [...qualityKeys.scores(), sourceId] as const,
  issues: () => [...qualityKeys.all, 'issues'] as const,
  issueList: (filters: QualityIssuesFilters) =>
    [...qualityKeys.issues(), filters] as const,
}

// ─── Hooks ──────────────────────────────────────────────────────────────────

/**
 * Fetch the latest quality score snapshot for a given source.
 * Polls every 30 s while the source is selected.
 */
export function useQualityScores(sourceId: string) {
  return useQuery({
    queryKey: qualityKeys.score(sourceId),
    queryFn: async () => {
      const { data } = await apiClient.get<PageResponse<QualityScoreDto>>(
        '/api/v1/quality/scores',
        { params: { sourceId } },
      )
      return data
    },
    enabled: !!sourceId,
    refetchInterval: 30_000,
  })
}

/**
 * Fetch paginated quality issues with optional filtering.
 * Results stay fresh for 30 s before a background refetch is triggered.
 */
export function useQualityIssues(filters: QualityIssuesFilters) {
  return useQuery({
    queryKey: qualityKeys.issueList(filters),
    queryFn: async () => {
      const params: Record<string, string | number> = {}
      if (filters.sourceId) params.sourceId = filters.sourceId
      if (filters.severity) params.severity = filters.severity
      if (filters.dimension) params.dimension = filters.dimension
      if (filters.status) params.status = filters.status
      if (filters.page !== undefined) params.page = filters.page
      if (filters.size !== undefined) params.size = filters.size

      const { data } = await apiClient.get<PageResponse<QualityIssueDto>>(
        '/api/v1/quality/issues',
        { params },
      )
      return data
    },
    staleTime: 30_000,
  })
}

/**
 * Acknowledge a quality issue by its ID.
 * Invalidates the issue list on success so the table re-fetches.
 */
export function useAcknowledgeIssue() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (issueId: string) => {
      const { data } = await apiClient.put<QualityIssueDto>(
        `/api/v1/quality/issues/${issueId}/acknowledge`,
      )
      return data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: qualityKeys.issues() })
    },
  })
}
