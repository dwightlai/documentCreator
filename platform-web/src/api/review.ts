import { http, type ApiEnvelope } from './http'

export interface ReviewTask {
  id: string
  writingTaskId: string
  documentId: string
  documentTypeCode: string
  status: string
  initiatorId: string
  totalIssues: number
  pendingIssues: number
  createdAt: string
  updatedAt: string
}
export interface ReviewIssue {
  id: string
  issueNumber: string
  issueCategory: string
  riskLevel: string
  pageNumber: number
  paragraphNumber: number
  charPositionStart: number
  charPositionEnd: number
  paragraphId: string
  originalText: string
  suggestedFix: string
  issueExplanation: string
  ruleBasis: string
  confidenceScore: number
  processingStatus: string
  processingNote?: string
  detectionMethod: string
  mergedGroupId?: string
  autoFixAllowed: boolean
}

export type ReviewAction = 'accept' | 'ignore' | 'modify' | 'transfer'

export interface ReviewReport {
  id: string
  reportVersion: number
  totalIssues: number
  pendingIssues: number
  conclusion: string
  generatedAt: string
}

export async function listReviewTasks() {
  const response = await http.get<ApiEnvelope<ReviewTask[]>>('/review-tasks')
  return response.data.data
}
export interface ReviewPlan { id: string; planCode: string; planName: string; applicableScenarios: string; reviewLevels: string[] }

export async function listReviewPlans() {
  const response = await http.get<ApiEnvelope<ReviewPlan[]>>('/review-plans')
  return response.data.data
}

export async function initiateReview(writingTaskId: string, reviewPlanId?: string) {
  const response = await http.post<ApiEnvelope<ReviewTask>>(`/writing-tasks/${writingTaskId}/initiate-review`, reviewPlanId ? { reviewPlanId } : undefined)
  return response.data.data
}
export async function listReviewIssues(reviewTaskId: string) {
  const response = await http.get<ApiEnvelope<ReviewIssue[]>>(`/review-tasks/${reviewTaskId}/issues`)
  return response.data.data
}
export async function processIssue(reviewTaskId: string, issueNumber: string, action: ReviewAction, note?: string) {
  const response = await http.patch<ApiEnvelope<ReviewIssue>>(`/review-tasks/${reviewTaskId}/issues/${issueNumber}/${action}`, { note })
  return response.data.data
}

export async function batchProcessIssues(reviewTaskId: string, action: 'accept' | 'ignore', issueNumbers: string[], note?: string) {
  const response = await http.post<ApiEnvelope<ReviewIssue[]>>(`/review-tasks/${reviewTaskId}/issues/batch-${action}`, { issueNumbers, note })
  return response.data.data
}

export async function reReview(reviewTaskId: string) {
  const response = await http.post<ApiEnvelope<ReviewTask>>(`/review-tasks/${reviewTaskId}/re-review`)
  return response.data.data
}

export async function generateReviewReport(reviewTaskId: string) {
  const response = await http.post<ApiEnvelope<ReviewReport>>(`/review-tasks/${reviewTaskId}/reports`)
  return response.data.data
}

export async function downloadReviewReport(reviewTaskId: string, version: number, format: 'PDF' | 'DOCX') {
  const response = await http.post<Blob>(`/review-tasks/${reviewTaskId}/reports/${version}/export`, undefined, {
    params: { format }, responseType: 'blob',
  })
  const url = URL.createObjectURL(response.data)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `review-report-v${version}.${format.toLowerCase()}`
  anchor.click()
  URL.revokeObjectURL(url)
}
