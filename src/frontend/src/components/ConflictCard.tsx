import { useState } from 'react'
import { Check, X, ChevronDown, ChevronUp, ShieldAlert } from 'lucide-react'
import { cn } from '../lib/cn'
import type { ConflictRecord } from '../types/conflict'

interface ConflictCardProps {
  conflict: ConflictRecord
  onResolve: (id: string, value: string) => void
  onSuppress: (id: string) => void
}

export function ConflictCard({ conflict, onResolve, onSuppress }: ConflictCardProps) {
  const [isExpanded, setIsExpanded] = useState(false)
  const [isResolving, setIsResolving] = useState(false)
  const [resolveValue, setResolveValue] = useState('')

  const handleResolveClick = () => {
    setIsResolving(true)
    // Pre-fill with one of the values, or empty
    setResolveValue(String(conflict.valueA || ''))
  }

  const submitResolve = () => {
    if (resolveValue.trim()) {
      onResolve(conflict.id, resolveValue)
      setIsResolving(false)
    }
  }

  const cancelResolve = () => {
    setIsResolving(false)
    setResolveValue('')
  }

  const statusColors = {
    OPEN: 'bg-semantic-warning/10 text-semantic-warning ring-semantic-warning/20',
    RESOLVED: 'bg-semantic-success/10 text-semantic-success ring-semantic-success/20',
    SUPPRESSED: 'bg-text-muted/20 text-text-secondary ring-text-muted/30',
  }

  return (
    <div className="rounded-xl bg-white shadow-sm ring-1 ring-border-default overflow-hidden flex flex-col">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-border-default bg-surface-base px-4 py-3">
        <div className="flex items-center gap-3">
          <span className="inline-flex items-center rounded-md bg-accent-subtle px-2 py-1 text-label-sm text-accent-primary ring-1 ring-inset ring-accent-primary/20">
            {conflict.entityType}
          </span>
          <span className="text-label text-text-primary">{conflict.fieldName}</span>
          <span className="text-meta text-text-tertiary font-mono">{conflict.entityId.substring(0, 8)}...</span>
        </div>
        <div className="flex items-center gap-3">
          <span className="text-meta text-text-secondary">
            {new Date(conflict.createdAt).toLocaleDateString()}
          </span>
          <span className={cn(
            "inline-flex items-center rounded-full px-2 py-1 text-label-sm ring-1 ring-inset",
            statusColors[conflict.status]
          )}>
            {conflict.status}
          </span>
        </div>
      </div>

      {/* Comparison Grid */}
      <div className="relative grid grid-cols-2 divide-x divide-border-default bg-white">
        {/* VS Badge */}
        <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-10 flex h-8 w-8 items-center justify-center rounded-full bg-surface-elevated ring-4 ring-white">
          <span className="text-label-sm text-text-secondary font-bold">VS</span>
        </div>

        {/* Source A */}
        <div className="p-5 flex flex-col gap-3">
          <div className="flex items-center justify-between">
            <span className="inline-flex items-center rounded-md bg-surface-elevated px-2 py-1 text-label-sm text-text-secondary">
              {conflict.sourceAName || conflict.sourceAId.substring(0, 8)}
            </span>
            {conflict.sourceATrustScore !== undefined && (
              <span className="text-meta text-text-tertiary flex items-center gap-1">
                <ShieldAlert className="h-3 w-3" />
                Trust: {(conflict.sourceATrustScore * 100).toFixed(0)}%
              </span>
            )}
          </div>
          <div className="rounded-lg bg-semantic-warning/5 p-4 ring-1 ring-semantic-warning/20">
            <span className="text-data-lg text-text-primary break-all">
              {String(conflict.valueA ?? 'null')}
            </span>
          </div>
        </div>

        {/* Source B */}
        <div className="p-5 flex flex-col gap-3">
          <div className="flex items-center justify-between">
            <span className="inline-flex items-center rounded-md bg-surface-elevated px-2 py-1 text-label-sm text-text-secondary">
              {conflict.sourceBName || conflict.sourceBId.substring(0, 8)}
            </span>
            {conflict.sourceBTrustScore !== undefined && (
              <span className="text-meta text-text-tertiary flex items-center gap-1">
                <ShieldAlert className="h-3 w-3" />
                Trust: {(conflict.sourceBTrustScore * 100).toFixed(0)}%
              </span>
            )}
          </div>
          <div className="rounded-lg bg-semantic-error/5 p-4 ring-1 ring-semantic-error/20">
            <span className="text-data-lg text-text-primary break-all">
              {String(conflict.valueB ?? 'null')}
            </span>
          </div>
        </div>
      </div>

      {/* Footer Actions */}
      <div className="flex items-center justify-between border-t border-border-default bg-surface-base px-4 py-3">
        <div className="flex items-center gap-4">
          <button
            onClick={() => setIsExpanded(!isExpanded)}
            className="flex items-center gap-1 text-label-sm text-text-secondary hover:text-text-primary transition-colors"
          >
            {isExpanded ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
            {isExpanded ? 'Hide Details' : 'View Details'}
          </button>
          <span className="text-meta text-text-tertiary">
            Strategy: {conflict.resolutionStrategy}
          </span>
        </div>

        {conflict.status === 'OPEN' && (
          <div className="flex items-center gap-2">
            {isResolving ? (
              <div className="flex items-center gap-2">
                <input
                  type="text"
                  value={resolveValue}
                  onChange={(e) => setResolveValue(e.target.value)}
                  className="h-8 rounded-md border border-border-default bg-white px-3 text-body-sm focus:border-accent-primary focus:outline-none focus:ring-1 focus:ring-accent-primary"
                  placeholder="Enter resolved value..."
                  autoFocus
                />
                <button
                  onClick={submitResolve}
                  className="flex h-8 w-8 items-center justify-center rounded-md bg-semantic-success text-white hover:bg-emerald-600 transition-colors"
                >
                  <Check className="h-4 w-4" />
                </button>
                <button
                  onClick={cancelResolve}
                  className="flex h-8 w-8 items-center justify-center rounded-md bg-surface-hover text-text-secondary hover:text-text-primary transition-colors"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
            ) : (
              <>
                <button
                  onClick={() => onSuppress(conflict.id)}
                  className="rounded-md px-3 py-1.5 text-label-sm text-text-secondary hover:bg-surface-hover transition-colors"
                >
                  Suppress
                </button>
                <button
                  onClick={handleResolveClick}
                  className="rounded-md bg-accent-primary px-3 py-1.5 text-label-sm text-white hover:bg-accent-hover transition-colors shadow-sm"
                >
                  Resolve
                </button>
              </>
            )}
          </div>
        )}
      </div>

      {/* Expanded Details */}
      {isExpanded && (
        <div className="border-t border-border-default bg-surface-elevated p-4">
          <pre className="text-meta text-text-secondary overflow-x-auto">
            {JSON.stringify(conflict, null, 2)}
          </pre>
        </div>
      )}
    </div>
  )
}
