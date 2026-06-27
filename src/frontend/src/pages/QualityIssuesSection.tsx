import { useState } from 'react';
import { useQualityIssues, useAcknowledgeIssue } from '@/hooks/useQuality';
import type { QualityIssuesFilters } from '@/hooks/useQuality';
import { AlertCircle, CheckCircle2, Filter } from 'lucide-react';

interface QualityIssuesSectionProps {
  sourceId?: string;
}

export function QualityIssuesSection({ sourceId }: QualityIssuesSectionProps) {
  // Local state for filter inputs
  const [localSeverity, setLocalSeverity] = useState<string>('');
  const [localDimension, setLocalDimension] = useState<string>('');
  const [localStatus, setLocalStatus] = useState<string>('');

  // Applied filters state that triggers the query
  const [appliedFilters, setAppliedFilters] = useState<QualityIssuesFilters>({
    sourceId,
    page: 0,
    size: 10,
  });

  const { data, isLoading, isError } = useQualityIssues(appliedFilters);
  const acknowledgeMutation = useAcknowledgeIssue();

  const handleApplyFilters = () => {
    setAppliedFilters({
      sourceId,
      severity: localSeverity || undefined,
      dimension: localDimension || undefined,
      status: localStatus || undefined,
      page: 0, // Reset to first page on new filters
      size: 10,
    });
  };

  const handlePageChange = (newPage: number) => {
    setAppliedFilters((prev) => ({ ...prev, page: newPage }));
  };

  const handleAcknowledge = (issueId: string) => {
    acknowledgeMutation.mutate(issueId);
  };

  const getSeverityBadgeClass = (severity: string) => {
    switch (severity) {
      case 'LOW':
        return 'bg-slate-100 text-slate-700 border-slate-200';
      case 'MEDIUM':
        return 'bg-amber-50 text-amber-700 border-amber-200';
      case 'HIGH':
        return 'bg-rose-50 text-rose-700 border-rose-200';
      case 'CRITICAL':
        return 'bg-rose-100 text-rose-800 border-rose-300 font-bold';
      default:
        return 'bg-slate-100 text-slate-700 border-slate-200';
    }
  };

  const getDimensionColor = (dimension: string) => {
    switch (dimension) {
      case 'COMPLETENESS': return 'text-blue-600';
      case 'VALIDITY': return 'text-emerald-600';
      case 'UNIQUENESS': return 'text-purple-600';
      case 'CONSISTENCY': return 'text-amber-600';
      case 'TIMELINESS': return 'text-cyan-600';
      case 'ACCURACY': return 'text-indigo-600';
      default: return 'text-slate-600';
    }
  };

  return (
    <div className="flex flex-col gap-4">
      {/* Filter Bar */}
      <div className="flex flex-wrap items-end gap-4 p-4 bg-white border border-slate-200 rounded-lg shadow-sm">
        <div className="flex flex-col gap-1.5">
          <label className="text-label-sm text-slate-500">Severity</label>
          <select
            className="h-9 px-3 py-1 bg-white border border-slate-300 rounded-md text-body-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
            value={localSeverity}
            onChange={(e) => setLocalSeverity(e.target.value)}
          >
            <option value="">ALL</option>
            <option value="LOW">LOW</option>
            <option value="MEDIUM">MEDIUM</option>
            <option value="HIGH">HIGH</option>
            <option value="CRITICAL">CRITICAL</option>
          </select>
        </div>

        <div className="flex flex-col gap-1.5">
          <label className="text-label-sm text-slate-500">Dimension</label>
          <select
            className="h-9 px-3 py-1 bg-white border border-slate-300 rounded-md text-body-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
            value={localDimension}
            onChange={(e) => setLocalDimension(e.target.value)}
          >
            <option value="">ALL</option>
            <option value="COMPLETENESS">Completeness</option>
            <option value="VALIDITY">Validity</option>
            <option value="UNIQUENESS">Uniqueness</option>
            <option value="CONSISTENCY">Consistency</option>
            <option value="TIMELINESS">Timeliness</option>
            <option value="ACCURACY">Accuracy</option>
          </select>
        </div>

        <div className="flex flex-col gap-1.5">
          <label className="text-label-sm text-slate-500">Status</label>
          <select
            className="h-9 px-3 py-1 bg-white border border-slate-300 rounded-md text-body-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
            value={localStatus}
            onChange={(e) => setLocalStatus(e.target.value)}
          >
            <option value="">ALL</option>
            <option value="OPEN">OPEN</option>
            <option value="ACKNOWLEDGED">ACKNOWLEDGED</option>
          </select>
        </div>

        <button
          onClick={handleApplyFilters}
          className="h-9 px-4 py-2 bg-indigo-600 text-white text-label-sm rounded-md hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 flex items-center gap-2 transition-colors"
        >
          <Filter className="w-4 h-4" />
          Apply Filters
        </button>
      </div>

      {/* Table Section */}
      <div className="bg-white border border-slate-200 rounded-lg shadow-sm overflow-hidden flex flex-col">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50">
                <th className="px-4 py-3 text-label-sm text-slate-500 font-medium">Source Field</th>
                <th className="px-4 py-3 text-label-sm text-slate-500 font-medium">Rule Code</th>
                <th className="px-4 py-3 text-label-sm text-slate-500 font-medium">Severity</th>
                <th className="px-4 py-3 text-label-sm text-slate-500 font-medium">Dimension</th>
                <th className="px-4 py-3 text-label-sm text-slate-500 font-medium">Message</th>
                <th className="px-4 py-3 text-label-sm text-slate-500 font-medium">Status</th>
                <th className="px-4 py-3 text-label-sm text-slate-500 font-medium">Created</th>
                <th className="px-4 py-3 text-label-sm text-slate-500 font-medium text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200">
              {isLoading ? (
                // Skeleton loading state
                Array.from({ length: 5 }).map((_, i) => (
                  <tr key={i}>
                    <td className="px-4 py-4"><div className="h-4 bg-slate-200 rounded w-24 animate-pulse"></div></td>
                    <td className="px-4 py-4"><div className="h-4 bg-slate-200 rounded w-20 animate-pulse"></div></td>
                    <td className="px-4 py-4"><div className="h-5 bg-slate-200 rounded-full w-16 animate-pulse"></div></td>
                    <td className="px-4 py-4"><div className="h-4 bg-slate-200 rounded w-24 animate-pulse"></div></td>
                    <td className="px-4 py-4"><div className="h-4 bg-slate-200 rounded w-48 animate-pulse"></div></td>
                    <td className="px-4 py-4"><div className="h-4 bg-slate-200 rounded w-16 animate-pulse"></div></td>
                    <td className="px-4 py-4"><div className="h-4 bg-slate-200 rounded w-24 animate-pulse"></div></td>
                    <td className="px-4 py-4 text-right"><div className="h-8 bg-slate-200 rounded w-24 animate-pulse ml-auto"></div></td>
                  </tr>
                ))
              ) : isError ? (
                <tr>
                  <td colSpan={8} className="px-4 py-8 text-center text-rose-600 text-body">
                    Failed to load quality issues. Please try again.
                  </td>
                </tr>
              ) : data?.content.length === 0 ? (
                <tr>
                  <td colSpan={8} className="px-4 py-12 text-center text-slate-500 text-body">
                    <div className="flex flex-col items-center justify-center gap-2">
                      <CheckCircle2 className="w-8 h-8 text-slate-400" />
                      <p>No issues match the selected filters</p>
                    </div>
                  </td>
                </tr>
              ) : (
                data?.content.map((issue) => (
                  <tr key={issue.id} className="hover:bg-slate-50 transition-colors">
                    <td className="px-4 py-3 text-body-sm font-medium text-slate-900">
                      {issue.sourceFieldName}
                    </td>
                    <td className="px-4 py-3 text-data text-slate-600">
                      {issue.ruleCode}
                    </td>
                    <td className="px-4 py-3">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium border ${getSeverityBadgeClass(issue.severity)}`}>
                        {issue.severity}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-body-sm font-medium">
                      <span className={getDimensionColor(issue.dimension)}>
                        {issue.dimension}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <div 
                        className="text-body-sm text-slate-600 max-w-xs truncate"
                        title={issue.message}
                      >
                        {issue.message}
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-1.5">
                        {issue.status === 'OPEN' ? (
                          <AlertCircle className="w-4 h-4 text-amber-500" />
                        ) : (
                          <CheckCircle2 className="w-4 h-4 text-emerald-500" />
                        )}
                        <span className="text-body-sm text-slate-700">
                          {issue.status}
                        </span>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-data text-slate-500">
                      {new Date(issue.createdAt).toLocaleDateString()}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <button
                        onClick={() => handleAcknowledge(issue.id)}
                        disabled={issue.status === 'ACKNOWLEDGED' || acknowledgeMutation.isPending}
                        className="inline-flex items-center justify-center px-3 py-1.5 border border-slate-300 shadow-sm text-label-sm font-medium rounded-md text-slate-700 bg-white hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                      >
                        {issue.status === 'ACKNOWLEDGED' ? 'Acknowledged' : 'Acknowledge'}
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {data && data.totalPages > 0 && (
          <div className="px-4 py-3 border-t border-slate-200 bg-slate-50 flex items-center justify-between">
            <div className="text-body-sm text-slate-600">
              Showing <span className="font-medium">{data.number * data.size + 1}</span> to{' '}
              <span className="font-medium">
                {Math.min((data.number + 1) * data.size, data.totalElements)}
              </span>{' '}
              of <span className="font-medium">{data.totalElements}</span> results
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => handlePageChange(data.number - 1)}
                disabled={data.first}
                className="px-3 py-1.5 border border-slate-300 rounded-md text-label-sm bg-white text-slate-700 hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                Previous
              </button>
              <span className="text-body-sm text-slate-600 px-2">
                Page {data.number + 1} of {data.totalPages}
              </span>
              <button
                onClick={() => handlePageChange(data.number + 1)}
                disabled={data.last}
                className="px-3 py-1.5 border border-slate-300 rounded-md text-label-sm bg-white text-slate-700 hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
