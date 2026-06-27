import { twMerge } from 'tailwind-merge';

export interface JobStatusBadgeProps {
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'PARTIAL' | 'FAILED';
  className?: string;
}

const statusConfig = {
  PENDING: 'bg-slate-400 text-white',
  RUNNING: 'bg-amber-500 text-white animate-pulse',
  COMPLETED: 'bg-emerald-500 text-white',
  PARTIAL: 'bg-amber-600 text-white',
  FAILED: 'bg-rose-500 text-white',
};

export function JobStatusBadge({ status, className }: JobStatusBadgeProps) {
  return (
    <span
      className={twMerge(
        'inline-flex items-center justify-center rounded px-2 py-0.5 text-meta font-medium',
        statusConfig[status],
        className
      )}
    >
      {status}
    </span>
  );
}
