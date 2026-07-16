import { http, type ApiEnvelope } from './http'

export interface AsyncJob {
  id: string
  jobType: string
  businessId?: string
  status: 'QUEUED' | 'RUNNING' | 'RETRY_WAIT' | 'COMPLETED' | 'FAILED' | 'DEAD_LETTER' | 'CANCELLED'
  progress: number
  resultSummary?: string
  errorMessage?: string
  retryCount: number
  maxRetries: number
  cancelRequested: boolean
  createdAt: string
  startedAt?: string
  finishedAt?: string
}

export interface JobEvent {
  id: number
  event: 'start' | 'delta' | 'progress' | 'completed' | 'error'
  data: string
  occurredAt: string
}

const data = async <T>(request: Promise<{ data: ApiEnvelope<T> }>) => (await request).data.data
export const listJobs = (status?: string) => data(http.get<ApiEnvelope<AsyncJob[]>>('/jobs', { params: { status: status || undefined } }))
export const getJobEvents = (jobId: string, afterEventId = 0) => data(http.get<ApiEnvelope<JobEvent[]>>(`/jobs/${jobId}/events/history`, { params: { afterEventId } }))
export const cancelJob = (jobId: string) => data(http.delete<ApiEnvelope<AsyncJob>>(`/jobs/${jobId}`, { headers: { 'Idempotency-Key': `cancel-${jobId}` } }))
export const retryJob = (jobId: string) => data(http.post<ApiEnvelope<AsyncJob>>(`/jobs/${jobId}/retry`, undefined, { headers: { 'Idempotency-Key': `retry-${jobId}-${Date.now()}` } }))
export const submitHealthCheck = () => data(http.post<ApiEnvelope<AsyncJob>>('/jobs', { jobType: 'SYSTEM_HEALTH_CHECK', businessId: `manual-${Date.now()}` }, { headers: { 'Idempotency-Key': `health-${crypto.randomUUID()}` } }))
