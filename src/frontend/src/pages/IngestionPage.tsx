import { useState, useCallback, useRef, useEffect } from 'react'
import { Upload, File as FileIcon, RefreshCw, ChevronLeft, ChevronRight, AlertCircle, CheckCircle2 } from 'lucide-react'
import { useSources } from '../hooks/useSources'
import { useJobs } from '../hooks/useJobs'
import { useUploadCsv, useUploadExcel } from '../hooks/useIngestion'
import { JobStatusBadge } from '../components/JobStatusBadge'
import { shouldPollJobs } from '../components/JobStatusPoller'

export function IngestionPage() {
  // Source selection
  const [selectedSourceId, setSelectedSourceId] = useState<string>('')
  
  // File upload state
  const [file, setFile] = useState<File | null>(null)
  const [isDragging, setIsDragging] = useState(false)
  const [uploadError, setUploadError] = useState<string | null>(null)
  const [uploadSuccess, setUploadSuccess] = useState<{ jobId: string } | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  // Pagination state for jobs
  const [page, setPage] = useState(0)
  const size = 10

  // Queries
  const { data: sourcesData, isLoading: isLoadingSources } = useSources(0, 100) // Fetch up to 100 sources for dropdown
  
  // We need to fetch jobs first to determine if we should poll
  // But we can't conditionally call hooks, so we use a state or just pass the result of shouldPollJobs
  // Wait, useJobs takes refetchInterval. We can pass a function or a value.
  // Let's just call useJobs without refetchInterval first? No, we can pass the refetchInterval dynamically.
  // Actually, we can just use the previous data to determine if we should poll.
  
  // To do this cleanly, we can use a wrapper or just pass the interval based on the current data.
  // React Query's useQuery will re-evaluate refetchInterval on every render.
  
  // We can't easily get the previous data before calling the hook, but React Query's useQuery
  // returns the data, and if we pass a function to refetchInterval, it receives the query.
  // But our custom hook `useJobs` takes `number | false`.
  // Let's use a state for the poll interval and update it in a useEffect.
  const [pollInterval, setPollInterval] = useState<number | false>(false)
  
  const { 
    data: jobsData, 
    isLoading: isLoadingJobs, 
    isError: isErrorJobs,
    refetch: refetchJobs,
    isRefetching
  } = useJobs(page, size, pollInterval)

  // Update poll interval based on jobs data
  useEffect(() => {
    const shouldPoll = shouldPollJobs(jobsData?.content)
    setPollInterval(shouldPoll ? 5000 : false)
  }, [jobsData?.content])
  
  // Mutations
  const uploadCsvMutation = useUploadCsv()
  const uploadExcelMutation = useUploadExcel()

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setIsDragging(true)
  }, [])

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setIsDragging(false)
  }, [])

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setIsDragging(false)
    setUploadError(null)
    setUploadSuccess(null)
    
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      const droppedFile = e.dataTransfer.files[0]
      validateAndSetFile(droppedFile)
    }
  }, [])

  const handleFileChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setUploadError(null)
    setUploadSuccess(null)
    
    if (e.target.files && e.target.files.length > 0) {
      validateAndSetFile(e.target.files[0])
    }
  }, [])

  const validateAndSetFile = (selectedFile: File) => {
    const validTypes = [
      'text/csv', 
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      'application/vnd.ms-excel'
    ]
    const validExtensions = ['.csv', '.xlsx']
    
    const fileExtension = selectedFile.name.substring(selectedFile.name.lastIndexOf('.')).toLowerCase()
    
    if (validTypes.includes(selectedFile.type) || validExtensions.includes(fileExtension)) {
      setFile(selectedFile)
    } else {
      setUploadError('Invalid file type. Please upload a CSV or Excel (.xlsx) file.')
      setFile(null)
    }
  }

  const handleUpload = async () => {
    if (!file || !selectedSourceId) return

    setUploadError(null)
    setUploadSuccess(null)

    try {
      const isCsv = file.name.toLowerCase().endsWith('.csv') || file.type === 'text/csv'
      
      let result
      if (isCsv) {
        result = await uploadCsvMutation.mutateAsync({ file, sourceId: selectedSourceId })
      } else {
        result = await uploadExcelMutation.mutateAsync({ file, sourceId: selectedSourceId })
      }
      
      setUploadSuccess({ jobId: result.id || result.jobId || 'Unknown' })
      setFile(null)
      if (fileInputRef.current) {
        fileInputRef.current.value = ''
      }
    } catch (error: any) {
      setUploadError(error.response?.data?.message || error.message || 'Failed to upload file')
    }
  }

  const isUploading = uploadCsvMutation.isPending || uploadExcelMutation.isPending

  return (
    <div className="flex flex-col h-full max-w-5xl mx-auto py-8 px-6 gap-8">
      <div>
        <h1 className="text-h1 text-text-primary">Ingestion</h1>
        <p className="text-body text-text-secondary mt-1">
          Upload data files and monitor ingestion jobs.
        </p>
      </div>

      {/* Upload Section */}
      <div className="flex flex-col gap-4 rounded-xl bg-white p-6 shadow-sm ring-1 ring-border-default">
        <h2 className="text-h3 text-text-primary">Upload Data</h2>
        
        <div className="flex flex-col gap-2">
          <label htmlFor="source-select" className="text-label-sm text-text-secondary">
            Target Data Source
          </label>
          <select
            id="source-select"
            value={selectedSourceId}
            onChange={(e) => setSelectedSourceId(e.target.value)}
            className="h-10 rounded-md border border-border-default bg-surface-base px-3 text-body focus:border-accent-primary focus:outline-none focus:ring-1 focus:ring-accent-primary max-w-md"
            disabled={isLoadingSources}
          >
            <option value="">Select a source...</option>
            {(sourcesData?.content || [])
              .filter(s => s.sourceType === 'CSV_FILE' || s.sourceType === 'EXCEL_FILE')
              .map(source => (
              <option key={source.id} value={source.id}>
                {source.name} ({source.sourceType})
              </option>
            ))}
          </select>
        </div>

        <div 
          className={`mt-2 flex flex-col items-center justify-center rounded-xl border-2 border-dashed p-10 transition-colors ${
            isDragging 
              ? 'border-accent-primary bg-accent-primary/5' 
              : 'border-border-default bg-surface-base hover:bg-surface-hover'
          } ${!selectedSourceId ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
          onDragOver={selectedSourceId ? handleDragOver : undefined}
          onDragLeave={selectedSourceId ? handleDragLeave : undefined}
          onDrop={selectedSourceId ? handleDrop : undefined}
          onClick={() => selectedSourceId && fileInputRef.current?.click()}
        >
          <input 
            type="file" 
            ref={fileInputRef} 
            onChange={handleFileChange} 
            className="hidden" 
            accept=".csv,.xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,text/csv"
            disabled={!selectedSourceId || isUploading}
          />
          
          <div className="rounded-full bg-surface-elevated p-4 mb-4 shadow-sm ring-1 ring-border-default">
            <Upload className="h-6 w-6 text-text-secondary" />
          </div>
          
          {file ? (
            <div className="flex flex-col items-center text-center">
              <div className="flex items-center gap-2 text-text-primary font-medium">
                <FileIcon className="h-4 w-4 text-accent-primary" />
                {file.name}
              </div>
              <p className="text-meta text-text-secondary mt-1">
                {(file.size / 1024 / 1024).toFixed(2)} MB
              </p>
            </div>
          ) : (
            <div className="flex flex-col items-center text-center">
              <p className="text-body font-medium text-text-primary">
                Drop CSV or Excel file here, or click to browse
              </p>
              <p className="text-meta text-text-secondary mt-1">
                Supports .csv and .xlsx files
              </p>
            </div>
          )}
        </div>

        {uploadError && (
          <div className="flex items-center gap-2 rounded-md bg-semantic-error/10 p-3 text-semantic-error text-body-sm">
            <AlertCircle className="h-4 w-4 shrink-0" />
            <p>{uploadError}</p>
          </div>
        )}

        {uploadSuccess && (
          <div className="flex items-center gap-2 rounded-md bg-semantic-success/10 p-3 text-semantic-success text-body-sm">
            <CheckCircle2 className="h-4 w-4 shrink-0" />
            <p>File uploaded successfully. Job ID: {uploadSuccess.jobId}</p>
          </div>
        )}

        <div className="flex justify-end mt-2">
          <button
            onClick={handleUpload}
            disabled={!file || !selectedSourceId || isUploading}
            className="flex h-10 items-center justify-center rounded-md bg-accent-primary px-6 text-label-md text-white hover:bg-accent-hover disabled:opacity-50 disabled:hover:bg-accent-primary transition-colors"
          >
            {isUploading ? (
              <>
                <div className="mr-2 h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white" />
                Uploading...
              </>
            ) : (
              'Upload File'
            )}
          </button>
        </div>
      </div>

      {/* Jobs Table Section */}
      <div className="flex flex-col gap-4 rounded-xl bg-white p-6 shadow-sm ring-1 ring-border-default">
        <div className="flex items-center justify-between">
          <h2 className="text-h3 text-text-primary">Recent Jobs</h2>
          <button
            onClick={() => refetchJobs()}
            disabled={isRefetching}
            className="flex h-8 items-center gap-2 rounded-md border border-border-default bg-white px-3 text-label-sm text-text-secondary hover:bg-surface-hover disabled:opacity-50 transition-colors"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${isRefetching ? 'animate-spin' : ''}`} />
            Refresh
          </button>
        </div>

        <div className="overflow-x-auto rounded-lg border border-border-default">
          <table className="w-full text-left text-body-sm">
            <thead className="bg-surface-base text-label-sm text-text-secondary border-b border-border-default">
              <tr>
                <th className="px-4 py-3 font-medium">Job ID</th>
                <th className="px-4 py-3 font-medium">Source</th>
                <th className="px-4 py-3 font-medium">Status</th>
                <th className="px-4 py-3 font-medium">Records (R/T/P/F/L/C)</th>
                <th className="px-4 py-3 font-medium">Created At</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border-default bg-white">
              {isLoadingJobs ? (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center">
                    <div className="flex justify-center">
                      <div className="h-6 w-6 animate-spin rounded-full border-2 border-border-default border-t-accent-primary" />
                    </div>
                  </td>
                </tr>
              ) : isErrorJobs ? (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-semantic-error">
                    Failed to load jobs.
                  </td>
                </tr>
              ) : !jobsData?.content || jobsData?.content?.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-text-secondary">
                    No ingestion jobs yet.
                  </td>
                </tr>
              ) : (
                (jobsData?.content || []).map((job) => (
                  <tr key={job.id} className="hover:bg-surface-hover/50 transition-colors">
                    <td className="px-4 py-3 font-mono text-meta text-text-secondary" title={job.id}>
                      {job.id.substring(0, 8)}...
                    </td>
                    <td className="px-4 py-3 text-text-primary font-medium">
                      {job.sourceName}
                    </td>
                    <td className="px-4 py-3">
                      <JobStatusBadge status={job.status} />
                    </td>
                    <td className="px-4 py-3 text-meta text-text-secondary">
                      {job.recordsRead} / {job.recordsTransformed} / {job.recordsPassed} / {job.recordsFailed} / {job.recordsLoaded} / {job.recordsConflicted}
                    </td>
                    <td className="px-4 py-3 text-text-secondary">
                      {new Date(job.createdAt).toLocaleString()}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {jobsData && jobsData.totalPages > 1 && (
          <div className="flex items-center justify-between pt-2">
            <span className="text-meta text-text-secondary">
              Showing {page * size + 1} to {Math.min((page + 1) * size, jobsData.totalElements)} of {jobsData.totalElements} results
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
                Page {page + 1} of {jobsData.totalPages}
              </span>
              <button
                onClick={() => setPage(p => Math.min(jobsData.totalPages - 1, p + 1))}
                disabled={page >= jobsData.totalPages - 1}
                className="flex h-8 w-8 items-center justify-center rounded-md border border-border-default bg-white text-text-secondary hover:bg-surface-hover disabled:opacity-50 disabled:hover:bg-white transition-colors"
              >
                <ChevronRight className="h-4 w-4" />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
