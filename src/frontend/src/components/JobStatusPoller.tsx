export function shouldPollJobs(jobs: Array<{ status: string }> | undefined): boolean {
  if (!jobs) return false;
  return jobs.some(j => j.status === 'RUNNING');
}
