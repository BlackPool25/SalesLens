import { useState, useMemo } from 'react';
import { useSources } from '@/hooks/useSources';
import { useQualityScores } from '@/hooks/useQuality';
import { QualityScoreRing } from '@/components/QualityScoreRing';
import { QualityIssuesSection } from '@/pages/QualityIssuesSection';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  LineChart,
  Line
} from 'recharts';

export function QualityDashboardPage() {
  const [selectedSourceId, setSelectedSourceId] = useState<string>('');
  const { data: sourcesData, isLoading: isLoadingSources } = useSources(0, 100);
  
  const { data: qualityData, isLoading: isLoadingQuality } = useQualityScores(selectedSourceId);

  // qualityData is PageResponse<QualityScoreDto>
  const scores = qualityData?.content || [];
  const latestScore = scores.length > 0 ? scores[0] : null;

  const dimensionData = useMemo(() => {
    if (!latestScore) return [];
    return [
      { name: 'Completeness', score: latestScore.scoreCompleteness },
      { name: 'Validity', score: latestScore.scoreValidity },
      { name: 'Uniqueness', score: latestScore.scoreUniqueness },
      { name: 'Consistency', score: latestScore.scoreConsistency },
      { name: 'Timeliness', score: latestScore.scoreTimeliness },
      { name: 'Accuracy', score: latestScore.scoreAccuracy },
    ];
  }, [latestScore]);

  const trendData = useMemo(() => {
    if (!scores.length) return [];
    // Sort by createdAt ascending for the line chart
    return [...scores].reverse().map(s => ({
      date: new Date(s.createdAt).toLocaleDateString(),
      score: s.overallScore
    }));
  }, [scores]);

  return (
    <div className="flex flex-col gap-6 p-6 h-full overflow-y-auto">
      <div className="flex items-center justify-between">
        <h1 className="text-h2 text-text-primary">Quality Overview</h1>
        
        <div className="flex items-center gap-3">
          <label htmlFor="source-select" className="text-label-sm text-text-secondary">
            Source
          </label>
          <select
            id="source-select"
            className="h-9 px-3 py-1 bg-surface-base border border-border-emphasis rounded-md text-body-sm focus:outline-none focus:ring-2 focus:ring-accent-primary focus:border-accent-primary"
            value={selectedSourceId}
            onChange={(e) => setSelectedSourceId(e.target.value)}
            disabled={isLoadingSources}
          >
            <option value="">Select a source...</option>
            {sourcesData?.content.map((source) => (
              <option key={source.id} value={source.id}>
                {source.name}
              </option>
            ))}
          </select>
        </div>
      </div>

      {!selectedSourceId ? (
        <div className="flex flex-col items-center justify-center py-20 bg-surface-elevated border border-border-default rounded-lg">
          <p className="text-body text-text-secondary">Please select a source to view quality metrics.</p>
        </div>
      ) : isLoadingQuality ? (
        <div className="flex flex-col gap-6 animate-pulse">
          <div className="h-32 bg-surface-elevated rounded-lg"></div>
          <div className="grid grid-cols-4 gap-4">
            {[1, 2, 3, 4].map(i => <div key={i} className="h-24 bg-surface-elevated rounded-lg"></div>)}
          </div>
          <div className="grid grid-cols-2 gap-6">
            <div className="h-80 bg-surface-elevated rounded-lg"></div>
            <div className="h-80 bg-surface-elevated rounded-lg"></div>
          </div>
        </div>
      ) : !latestScore ? (
        <div className="flex flex-col items-center justify-center py-20 bg-surface-elevated border border-border-default rounded-lg">
          <p className="text-body text-text-secondary">No quality data available for this source.</p>
        </div>
      ) : (
        <>
          {/* Hero Section */}
          <div className="flex items-center gap-8 p-8 bg-surface-base border border-border-default rounded-lg">
            <QualityScoreRing 
              grade={latestScore.letterGrade} 
              score={latestScore.overallScore} 
              size="lg" 
            />
            <div className="flex flex-col">
              <h2 className="text-h1 text-text-primary">
                {(latestScore.overallScore * 100).toFixed(1)}%
              </h2>
              <p className="text-body text-text-secondary">Overall Quality Score</p>
            </div>
          </div>

          {/* Summary Stats */}
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            <div className="p-4 bg-surface-base border border-border-default rounded-lg flex flex-col gap-1">
              <span className="text-label-sm text-text-secondary">Total Records Scored</span>
              <span className="text-data-lg text-text-primary">{latestScore.totalRecordsScored?.toLocaleString() || 0}</span>
            </div>
            <div className="p-4 bg-surface-base border border-border-default rounded-lg flex flex-col gap-1">
              <span className="text-label-sm text-text-secondary">Average Score</span>
              <span className="text-data-lg text-text-primary">{((latestScore.averageScore || latestScore.overallScore) * 100).toFixed(1)}%</span>
            </div>
            <div className="p-4 bg-surface-base border border-border-default rounded-lg flex flex-col gap-1">
              <span className="text-label-sm text-text-secondary">Current Grade</span>
              <span className="text-data-lg text-text-primary">{latestScore.letterGrade}</span>
            </div>
            <div className="p-4 bg-surface-base border border-border-default rounded-lg flex flex-col gap-1">
              <span className="text-label-sm text-text-secondary">Open Issues</span>
              <span className="text-data-lg text-text-primary">{latestScore.openIssues || 0}</span>
            </div>
          </div>

          {/* Charts */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div className="p-6 bg-surface-base border border-border-default rounded-lg flex flex-col gap-4">
              <h3 className="text-h3 text-text-primary">Quality Dimensions</h3>
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={dimensionData} layout="vertical">
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border-default)" />
                  <XAxis type="number" domain={[0, 1]} tickFormatter={(v) => `${(v * 100).toFixed(0)}%`} />
                  <YAxis type="category" dataKey="name" width={150} />
                  <Tooltip formatter={(v: number) => `${(v * 100).toFixed(1)}%`} />
                  <Bar dataKey="score" fill="var(--chart-1)" radius={[0, 4, 4, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>

            <div className="p-6 bg-surface-base border border-border-default rounded-lg flex flex-col gap-4">
              <h3 className="text-h3 text-text-primary">Quality Trend</h3>
              <ResponsiveContainer width="100%" height={250}>
                <LineChart data={trendData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border-default)" />
                  <XAxis dataKey="date" />
                  <YAxis domain={[0, 1]} tickFormatter={(v) => `${(v * 100).toFixed(0)}%`} />
                  <Tooltip formatter={(v: number) => `${(v * 100).toFixed(1)}%`} />
                  <Line type="monotone" dataKey="score" stroke="var(--chart-1)" strokeWidth={2} dot={{ r: 4 }} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </div>

          {/* Issues Section */}
          <div className="flex flex-col gap-4 mt-4">
            <h3 className="text-h2 text-text-primary">Quality Issues</h3>
            <QualityIssuesSection sourceId={selectedSourceId} />
          </div>
        </>
      )}
    </div>
  );
}
