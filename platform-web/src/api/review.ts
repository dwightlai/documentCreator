import { http, type ApiEnvelope } from './http'

export interface ReviewTask {
  id: string
  sourceSystem?: string
  businessId?: string
  sourceFileName?: string
  writingTaskId: string
  documentId: string
  documentVersion?: number
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

export interface ReviewContentBlock {
  anchor: string
  kind: 'title' | 'paragraph' | 'tableRow'
  paragraphNumber: number
  originalText: string
  currentText: string
  locked: boolean
  style?: {alignment:string;firstLineTwips:number;leftTwips:number;beforeTwips:number;afterTwips:number;fontFamily?:string;fontPt:number;bold:boolean;italic:boolean;color?:string}
}
export interface ReviewContent {
  sourceSystem: string
  title: string
  reviewVersion: number
  currentVersion: number
  needsReview: boolean
  blocks: ReviewContentBlock[]
}

export async function getReviewContent(taskId: string) {
  const response = await http.get<ApiEnvelope<ReviewContent>>(`/review-tasks/${taskId}/content`)
  return response.data.data
}
export async function saveReviewContent(taskId: string, anchor: string, text: string, expectedVersion: number) {
  const response = await http.put<ApiEnvelope<ReviewContent>>(`/review-tasks/${taskId}/content`, { anchor, text, expectedVersion })
  return response.data.data
}
export async function downloadEditedReviewDocument(taskId: string, filename: string) {
  const response = await http.get<Blob>(`/review-tasks/${taskId}/edited-document`, { responseType: 'blob' })
  const url = URL.createObjectURL(response.data)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename.replace(/\.docx$/i, '') + '-审校修改.docx'
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
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
export async function deleteReviewTask(id: string) {
  await http.delete(`/review-tasks/${id}`)
}
export interface ReviewPlan { id: string; planCode: string; planName: string; applicableScenarios: string; reviewLevels: string[]; ruleSetVersion?: string; knowledgeScope?: string[]; allowExternalModel?: boolean; riskThreshold: string; checkAttachments: boolean; allowAutoFix: boolean; generateReport: boolean; custom: boolean; lockVersion: number }
export type ReviewPlanInput = Pick<ReviewPlan,'planCode'|'planName'|'applicableScenarios'|'reviewLevels'|'ruleSetVersion'|'knowledgeScope'|'allowExternalModel'|'riskThreshold'|'checkAttachments'|'allowAutoFix'|'generateReport'>
export async function createReviewPlan(payload: ReviewPlanInput) {
  const response = await http.post<ApiEnvelope<ReviewPlan>>('/review-plans', payload)
  return response.data.data
}
export async function updateReviewPlan(id: string, payload: ReviewPlanInput & { lockVersion: number }) {
  const response = await http.put<ApiEnvelope<ReviewPlan>>(`/review-plans/${id}`, payload)
  return response.data.data
}
export async function deleteReviewPlan(id: string) { await http.delete(`/review-plans/${id}`) }

export async function listReviewPlans() {
  const response = await http.get<ApiEnvelope<ReviewPlan[]>>('/review-plans')
  return response.data.data
}

export async function initiateReview(writingTaskId: string, reviewPlanId?: string) {
  const response = await http.post<ApiEnvelope<ReviewTask>>(`/writing-tasks/${writingTaskId}/initiate-review`, reviewPlanId ? { reviewPlanId } : undefined)
  return response.data.data
}
export async function detectReviewDocumentType(file: File) {
  const form = new FormData()
  form.append('file', file)
  const response = await http.post<ApiEnvelope<string>>('/review-tasks/detect-type', form)
  return response.data.data
}
export async function uploadReviewDocument(file: File, outputTemplateId?: string) {
  const form = new FormData()
  form.append('file', file)
  if (outputTemplateId) form.append('outputTemplateId', outputTemplateId)
  const response = await http.post<ApiEnvelope<ReviewTask>>('/review-tasks/upload', form)
  return response.data.data
}
export async function reUploadReviewDocument(reviewTaskId: string, file: File) {
  const form = new FormData()
  form.append('file', file)
  const response = await http.post<ApiEnvelope<ReviewTask>>(`/review-tasks/${reviewTaskId}/re-upload`, form)
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
