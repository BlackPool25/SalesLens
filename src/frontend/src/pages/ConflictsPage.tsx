import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Filter, Search, ChevronLeft, ChevronRight, Check } from 'lucide-react'
import { apiClient } from '../lib/api-client'
import { ConflictCard } from '../components/ConflictCard'
import { ConfirmDialog } from '../components/ConfirmDialog'
import type { ConflictRecord } from '../types/conflict'

interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
  first: boolean
  last: boolean
  empty: boolean
}

export function ConflictsPage() {
  const queryClient = useQueryClient()
  
  // Filters state
  const [entityType, setEntityType] = useState('')
  const [fieldName, setFieldName] = useState('')
  const [status, setStatus] = useState('OPEN')
  const [sourceId, setSourceId] = useState('')
  
  // Pagination state
  const [page, setPage] = useState(0)
  const size = 10

  // Dialog state
  const [suppressId, setSuppressId] = useState<string | null>(null)

  // Fetch conflicts
  const { data, isLoading, isError } = useQuery({
    queryKey: ['conflicts', { entityType, fieldName, status, sourceId, page, size }],
    queryFn: async () => {
      const params = new URLSearchParams()
      if (entityType) params.append('entityType', entityType)
      if (fieldName) params.append('fieldName', fieldName)
      if (status && status !== 'ALL') params.append('status', status)
      if (sourceId) params.append('sourceId', sourceId)
      params.append('page', page.toString())
      params.append('size', size.toString())

      const response = await apiClient.get<PageResponse<ConflictRecord>>(`/api/v1/conflicts?${params.toString()}`)
      return response.data
    }
  })

  // Resolve mutation
  const resolveMutation = useMutation({
    mutationFn: async ({ id, chosenValue }: { id: string, chosenValue: string }) => {
      await apiClient.put(`/api/v1/conflicts/${id}/resolve`, { chosenValue })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['conflicts'] })
    }
  })

  // Suppress mutation
  const suppressMutation = useMutation({
    mutationFn: async (id: string) => {
      await apiClient.put(`/api/v1/conflicts/${id}/suppress`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['conflicts'] })
      setSuppressId(null)
    }
  })

  const handleResolve = (id: string, chosenValue: string) => {
    resolveMutation.mutate({ id, chosenValue })
  }

  const handleSuppressConfirm = () => {
    if (suppressId) {
      suppressMutation.mutate(suppressId)
    }
  }

  return (
    <div className="flex flex-col h-full max-w-5xl mx-auto py-8 px-6 gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-h1 text-text-primary">Data Conflicts</h1>
          <p className="text-body text-text-secondary mt-1">
            Review and resolve conflicting values across data sources.
          </p>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="flex flex-wrap items-center gap-4 rounded-xl bg-white p-4 shadow-sm ring-1 ring-border-default">
        <div className="flex items-center gap-2 text-text-secondary">
          <Filter className="h-4 w-4" />
          <span className="text-label-sm">Filters</span>
        </div>
        
        <div className="h-6 w-px bg-border-default mx-2" />

        <select
          value={status}
          onChange={(e) => { setStatus(e.target.value); setPage(0); }}
          className="h-9 rounded-md border border-border-default bg-surface-base px-3 text-body-sm focus:border-accent-primary focus:outline-none focus:ring-1 focus:ring-accent-primary"
        >
          <option value="ALL">All Statuses</option>
          <option value="OPEN">Open</option>
          <option value="RESOLVED">Resolved</option>
          <option value="SUPPRESSED">Suppressed</option>
        </select>

        <select
          value={entityType}
          onChange={(e) => { setEntityType(e.target.value); setPage(0); }}
          className="h-9 rounded-md border border-border-default bg-surface-base px-3 text-body-sm focus:border-accent-primary focus:outline-none focus:ring-1 focus:ring-accent-primary"
        >
          <option value="">All Entities</option>
          <option value="customers">Customers</option>
          <option value="products">Products</option>
          <option value="orders">Orders</option>
        </select>

        <div className="relative flex-1 min-w-[200px]">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-text-tertiary" />
          <input
            type="text"
            placeholder="Filter by field name..."
            value={fieldName}
            onChange={(e) => { setFieldName(e.target.value); setPage(0); }}
            className="h-9 w-full rounded-md border border-border-default bg-surface-base pl-9 pr-3 text-body-sm focus:border-accent-primary focus:outline-none focus:ring-1 focus:ring-accent-primary"
          />
        </div>

        <select
          value={sourceId}
          onChange={(e) => { setSourceId(e.target.value); setPage(0); }}
          className="h-9 rounded-md border border-border-default bg-surface-base px-3 text-body-sm focus:border-accent-primary focus:outline-none focus:ring-1 focus:ring-accent-primary"
        >
          <option value="">All Sources</option>
          {/* In a real app, these would be populated from an API */}
          <option value="source-a">Source A</option>
          <option value="source-b">Source B</option>
        </select>
      </div>

      {/* Content */}
      <div className="flex-1 flex flex-col gap-4 min-h-0">
        {isLoading ? (
          <div className="flex-1 flex items-center justify-center">
            <div className="h-8 w-8 animate-spin rounded-full border-4 border-border-default border-t-accent-primary" />
          </div>
        ) : isError ? (
          <div className="flex-1 flex items-center justify-center text-semantic-error">
            Failed to load conflicts.
          </div>
        ) : data?.content.length === 0 ? (
          <div className="flex-1 flex flex-col items-center justify-center rounded-xl border border-dashed border-border-default bg-surface-base/50 p-8 text-center">
            <div className="rounded-full bg-surface-elevated p-3 mb-4">
              <Check className="h-6 w-6 text-semantic-success" />
            </div>
            <h3 className="text-h3 text-text-primary mb-1">No conflicts found</h3>
            <p className="text-body text-text-secondary">
              All data is currently synchronized and conflict-free.
            </p>
          </div>
        ) : (
          <div className="flex flex-col gap-4">
            {data?.content.map((conflict) => (
              <ConflictCard
                key={conflict.id}
                conflict={conflict}
                onResolve={handleResolve}
                onSuppress={(id) => setSuppressId(id)}
              />
            ))}
          </div>
        )}
      </div>

      {/* Pagination */}
      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-between border-t border-border-default pt-4 mt-auto">
          <span className="text-meta text-text-secondary">
            Showing {page * size + 1} to {Math.min((page + 1) * size, data.totalElements)} of {data.totalElements} results
          </span>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setPage(p => Math.max(0, p - 1))}
              disabled={page === 0}
              className="flex h-8 w-8 items-center justify-center rounded-md border border-border-default bg-white text-text-secondary hover:bg-surface-hover disabled:opacity-50 disabled:hover:bg-white transition-colors"
            >
              <ChevronLeft className="h-4 w-4" />
            </button>
            <span className="text-label-sm text-text-primary px-2">
              Page {page + 1} of {data.totalPages}
            </span>
            <button
              onClick={() => setPage(p => Math.min(data.totalPages - 1, p + 1))}
              disabled={page >= data.totalPages - 1}
              className="flex h-8 w-8 items-center justify-center rounded-md border border-border-default bg-white text-text-secondary hover:bg-surface-hover disabled:opacity-50 disabled:hover:bg-white transition-colors"
            >
              <ChevronRight className="h-4 w-4" />
            </button>
          </div>
        </div>
      )}

      {/* Suppress Confirmation Dialog */}
      <ConfirmDialog
        isOpen={suppressId !== null}
        title="Suppress Conflict"
        message="Are you sure you want to suppress this conflict? It will be hidden from the open conflicts list and will not be resolved."
        confirmLabel="Suppress"
        onConfirm={handleSuppressConfirm}
        onCancel={() => setSuppressId(null)}
      />
    </div>
  )
}
