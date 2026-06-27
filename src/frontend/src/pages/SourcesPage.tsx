import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Plus, AlertCircle, RefreshCw, ChevronLeft, ChevronRight, X } from 'lucide-react'
import { useSources, useCreateSource } from '@/hooks/useSources'
import { QualityScoreRing } from '@/components/QualityScoreRing'
import { cn } from '@/lib/cn'

const sourceSchema = z.object({
  name: z.string().min(1, "Name is required"),
  sourceType: z.enum(["CSV_FILE", "EXCEL_FILE", "JDBC_POSTGRES", "JDBC_MYSQL", "KAFKA_STREAM"]),
  trustScore: z.number().min(0).max(1).step(0.1),
  active: z.boolean(),
  connectionConfig: z.string().refine(val => {
    if (!val) return true;
    try {
      JSON.parse(val);
      return true;
    } catch {
      return false;
    }
  }, "Must be valid JSON")
});

type SourceFormValues = z.infer<typeof sourceSchema>;

const typeColors: Record<string, { bg: string, label: string }> = {
  CSV_FILE: { bg: '#475569', label: 'CSV' },
  EXCEL_FILE: { bg: '#0d9488', label: 'Excel' },
  JDBC_POSTGRES: { bg: '#059669', label: 'Postgres' },
  JDBC_MYSQL: { bg: '#d97706', label: 'MySQL' },
  KAFKA_STREAM: { bg: '#7c3aed', label: 'Kafka' },
};

function getGrade(score: number): 'A' | 'B' | 'C' | 'D' | 'F' {
  if (score >= 0.9) return 'A';
  if (score >= 0.8) return 'B';
  if (score >= 0.7) return 'C';
  if (score >= 0.6) return 'D';
  return 'F';
}

export function SourcesPage() {
  const [page, setPage] = useState(0);
  const size = 10;
  const [isModalOpen, setIsModalOpen] = useState(false);

  const { data, isLoading, isError, error, refetch, isRefetching } = useSources(page, size);
  const createSource = useCreateSource();

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors }
  } = useForm<SourceFormValues>({
    resolver: zodResolver(sourceSchema),
    defaultValues: {
      name: '',
      sourceType: 'CSV_FILE',
      trustScore: 1.0,
      active: true,
      connectionConfig: '{\n  \n}'
    }
  });

  const onSubmit = async (values: SourceFormValues) => {
    try {
      await createSource.mutateAsync({
        ...values,
        connectionConfig: values.connectionConfig || '{}'
      });
      setIsModalOpen(false);
      reset();
    } catch (err) {
      console.error("Failed to create source", err);
    }
  };

  const openModal = () => {
    reset();
    setIsModalOpen(true);
  };

  return (
    <div className="flex flex-col h-full max-w-5xl mx-auto py-8 px-6 gap-8">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-h1 text-text-primary">Sources</h1>
          <p className="text-body text-text-secondary mt-1">
            Manage your data sources and connections.
          </p>
        </div>
        <button
          onClick={openModal}
          className="flex h-10 items-center justify-center gap-2 rounded-md bg-accent-primary px-4 text-label-md text-white hover:bg-accent-hover transition-colors"
        >
          <Plus className="h-4 w-4" />
          Register New Source
        </button>
      </div>

      <div className="flex flex-col gap-4 rounded-xl bg-white p-6 ring-1 ring-border-default">
        <div className="flex items-center justify-between">
          <h2 className="text-h3 text-text-primary">Registered Sources</h2>
          <button
            onClick={() => refetch()}
            disabled={isRefetching}
            className="flex h-8 items-center gap-2 rounded-md border border-border-default bg-white px-3 text-label-sm text-text-secondary hover:bg-surface-hover disabled:opacity-50 transition-colors"
          >
            <RefreshCw className={cn("h-3.5 w-3.5", isRefetching && "animate-spin")} />
            Refresh
          </button>
        </div>

        {isError ? (
          <div className="flex flex-col items-center justify-center py-12 gap-4 rounded-lg border border-border-default bg-surface-base">
            <div className="flex items-center gap-2 text-semantic-error">
              <AlertCircle className="h-5 w-5" />
              <p className="text-body font-medium">Failed to load sources</p>
            </div>
            <p className="text-body-sm text-text-secondary">
              {error instanceof Error ? error.message : 'An unknown error occurred'}
            </p>
            <button
              onClick={() => refetch()}
              className="mt-2 flex h-9 items-center justify-center rounded-md border border-border-default bg-white px-4 text-label-sm text-text-primary hover:bg-surface-hover transition-colors"
            >
              Retry
            </button>
          </div>
        ) : (
          <div className="overflow-x-auto rounded-lg border border-border-default">
            <table className="w-full text-left text-body-sm">
              <thead className="bg-surface-base text-label-sm text-text-secondary border-b border-border-default">
                <tr>
                  <th className="px-4 py-3 font-medium">Name</th>
                  <th className="px-4 py-3 font-medium">Type</th>
                  <th className="px-4 py-3 font-medium">Status</th>
                  <th className="px-4 py-3 font-medium">Trust Score</th>
                  <th className="px-4 py-3 font-medium">Quality</th>
                  <th className="px-4 py-3 font-medium">Last Sync</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border-default bg-white">
                {isLoading ? (
                  Array.from({ length: 5 }).map((_, i) => (
                    <tr key={i}>
                      <td className="px-4 py-4"><div className="h-4 w-32 animate-pulse rounded bg-surface-hover" /></td>
                      <td className="px-4 py-4"><div className="h-5 w-16 animate-pulse rounded-full bg-surface-hover" /></td>
                      <td className="px-4 py-4"><div className="h-5 w-16 animate-pulse rounded-full bg-surface-hover" /></td>
                      <td className="px-4 py-4"><div className="h-4 w-12 animate-pulse rounded bg-surface-hover" /></td>
                      <td className="px-4 py-4"><div className="h-8 w-8 animate-pulse rounded-full bg-surface-hover" /></td>
                      <td className="px-4 py-4"><div className="h-4 w-24 animate-pulse rounded bg-surface-hover" /></td>
                    </tr>
                  ))
                ) : !data?.content || data.content.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="px-4 py-12 text-center">
                      <div className="flex flex-col items-center justify-center gap-3">
                        <p className="text-body font-medium text-text-primary">No sources registered</p>
                        <p className="text-body-sm text-text-secondary">Get started by registering your first data source.</p>
                        <button
                          onClick={openModal}
                          className="mt-2 flex h-9 items-center justify-center rounded-md bg-accent-primary px-4 text-label-sm text-white hover:bg-accent-hover transition-colors"
                        >
                          Register your first source
                        </button>
                      </div>
                    </td>
                  </tr>
                ) : (
                  data.content.map((source) => (
                    <tr key={source.id} className="hover:bg-surface-hover/50 transition-colors">
                      <td className="px-4 py-3 text-text-primary font-medium">
                        {source.name}
                      </td>
                      <td className="px-4 py-3">
                        <span 
                          className="inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-medium text-white"
                          style={{ backgroundColor: typeColors[source.sourceType]?.bg || '#64748b' }}
                        >
                          {typeColors[source.sourceType]?.label || source.sourceType}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <span className={cn(
                          "inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-medium",
                          source.active ? "bg-semantic-success/10 text-semantic-success" : "bg-surface-hover text-text-secondary"
                        )}>
                          {source.active ? 'Active' : 'Inactive'}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-data text-text-secondary">
                        {source.trustScore.toFixed(2)}
                      </td>
                      <td className="px-4 py-3">
                        <QualityScoreRing 
                          grade={getGrade(source.trustScore)} 
                          score={source.trustScore} 
                          size="sm" 
                        />
                      </td>
                      <td className="px-4 py-3 text-text-secondary">
                        {source.lastSyncAt ? new Date(source.lastSyncAt).toLocaleString() : 'Never'}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination */}
        {data && data.totalPages > 1 && (
          <div className="flex items-center justify-between pt-2">
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
      </div>

      {/* Register Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 p-4">
          <div className="w-full max-w-md rounded-xl bg-white ring-1 ring-border-default">
            <div className="flex items-center justify-between border-b border-border-default px-6 py-4">
              <h2 className="text-h3 text-text-primary">Register New Source</h2>
              <button 
                onClick={() => setIsModalOpen(false)}
                className="rounded-md p-1 text-text-secondary hover:bg-surface-hover transition-colors"
              >
                <X className="h-5 w-5" />
              </button>
            </div>
            
            <form onSubmit={handleSubmit(onSubmit)} className="p-6">
              <div className="flex flex-col gap-4">
                <div className="flex flex-col gap-1.5">
                  <label htmlFor="name" className="text-label-sm text-text-primary">Name</label>
                  <input
                    id="name"
                    {...register('name')}
                    className={cn(
                      "h-10 rounded-md border bg-surface-base px-3 text-body focus:outline-none focus:ring-1",
                      errors.name ? "border-semantic-error focus:border-semantic-error focus:ring-semantic-error" : "border-border-default focus:border-accent-primary focus:ring-accent-primary"
                    )}
                    placeholder="e.g. Salesforce CRM"
                  />
                  {errors.name && <span className="text-meta text-semantic-error">{errors.name.message}</span>}
                </div>

                <div className="flex flex-col gap-1.5">
                  <label htmlFor="sourceType" className="text-label-sm text-text-primary">Source Type</label>
                  <select
                    id="sourceType"
                    {...register('sourceType')}
                    className="h-10 rounded-md border border-border-default bg-surface-base px-3 text-body focus:border-accent-primary focus:outline-none focus:ring-1 focus:ring-accent-primary"
                  >
                    <option value="CSV_FILE">CSV File</option>
                    <option value="EXCEL_FILE">Excel File</option>
                    <option value="JDBC_POSTGRES">PostgreSQL</option>
                    <option value="JDBC_MYSQL">MySQL</option>
                    <option value="KAFKA_STREAM">Kafka Stream</option>
                  </select>
                  {errors.sourceType && <span className="text-meta text-semantic-error">{errors.sourceType.message}</span>}
                </div>

                <div className="flex flex-col gap-1.5">
                  <label htmlFor="trustScore" className="text-label-sm text-text-primary">Trust Score (0.0 - 1.0)</label>
                  <input
                    id="trustScore"
                    type="number"
                    step="0.1"
                    min="0"
                    max="1"
                    {...register('trustScore', { valueAsNumber: true })}
                    className={cn(
                      "h-10 rounded-md border bg-surface-base px-3 text-body focus:outline-none focus:ring-1",
                      errors.trustScore ? "border-semantic-error focus:border-semantic-error focus:ring-semantic-error" : "border-border-default focus:border-accent-primary focus:ring-accent-primary"
                    )}
                  />
                  {errors.trustScore && <span className="text-meta text-semantic-error">{errors.trustScore.message}</span>}
                </div>

                <div className="flex items-center gap-2 pt-1">
                  <input
                    id="active"
                    type="checkbox"
                    {...register('active')}
                    className="h-4 w-4 rounded border-border-default text-accent-primary focus:ring-accent-primary"
                  />
                  <label htmlFor="active" className="text-label-sm text-text-primary">Active</label>
                </div>

                <div className="flex flex-col gap-1.5">
                  <label htmlFor="connectionConfig" className="text-label-sm text-text-primary">Connection Config (JSON)</label>
                  <textarea
                    id="connectionConfig"
                    {...register('connectionConfig')}
                    rows={4}
                    className={cn(
                      "rounded-md border bg-surface-base p-3 font-mono text-body-sm focus:outline-none focus:ring-1",
                      errors.connectionConfig ? "border-semantic-error focus:border-semantic-error focus:ring-semantic-error" : "border-border-default focus:border-accent-primary focus:ring-accent-primary"
                    )}
                    placeholder='{"key": "value"}'
                  />
                  {errors.connectionConfig && <span className="text-meta text-semantic-error">{errors.connectionConfig.message}</span>}
                </div>
              </div>

              <div className="mt-8 flex justify-end gap-3">
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="flex h-10 items-center justify-center rounded-md border border-border-default bg-white px-4 text-label-sm text-text-primary hover:bg-surface-hover transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={createSource.isPending}
                  className="flex h-10 items-center justify-center rounded-md bg-accent-primary px-6 text-label-sm text-white hover:bg-accent-hover disabled:opacity-50 transition-colors"
                >
                  {createSource.isPending ? (
                    <>
                      <div className="mr-2 h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white" />
                      Saving...
                    </>
                  ) : (
                    'Register Source'
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
