import { http, type ApiEnvelope } from './http'

export type LifecycleStatus = '草稿' | '测试' | '审核' | '生效' | '停用'

export interface ModelConfig {
  id: string
  configCode: string
  configName: string
  provider: string
  modelName: string
  modelVersion?: string
  scene: string
  dataLevel: string
  maxContextTokens?: number
  timeoutMs: number
  maxRetries: number
  costLimitDaily?: number
  sensitiveWordFilter: boolean
  outputValidation: boolean
  active: boolean
  endpointUrl?: string
  credentialRef?: string
  inputCostPer1k?: number
  outputCostPer1k?: number
  qpsLimit?: number
  dailyTokenLimit?: number
  lockVersion: number
  updatedAt: string
}

export interface ModelConfigInput {
  configCode: string
  configName: string
  provider: string
  modelName: string
  modelVersion: string
  scene: string
  dataLevel: string
  maxContextTokens: number
  timeoutMs: number
  maxRetries: number
  costLimitDaily: number
  sensitiveWordFilter: boolean
  outputValidation: boolean
  active: boolean
  endpointUrl: string
  credentialRef: string
  inputCostPer1k: number
  outputCostPer1k: number
  qpsLimit: number
  dailyTokenLimit: number
  lockVersion?: number
}

export interface PromptTemplate {
  id: string
  templateCode: string
  templateName: string
  applicableScene: string
  documentTypeCode?: string
  applicableModels: string[]
  systemRole: string
  documentTypeMethod?: string
  inputFields: Record<string, unknown>
  materialCitationRule?: string
  antiFabricationRule: string
  outputFormat: string
  riskConstraint?: string
  modelParameters: Record<string, unknown>
  testSampleIds: string[]
  currentVersion: number
  lifecycleStatus: LifecycleStatus
  lockVersion: number
  updatedAt: string
}

export interface PromptTemplateInput {
  templateCode: string
  templateName: string
  applicableScene: string
  documentTypeCode: string
  applicableModels: string[]
  systemRole: string
  documentTypeMethod: string
  inputFields: Record<string, unknown>
  materialCitationRule: string
  antiFabricationRule: string
  outputFormat: string
  riskConstraint: string
  modelParameters: Record<string, unknown>
  testSampleIds: string[]
  changeDescription: string
  lockVersion?: number
}

export interface AuditLog {
  id: string
  auditType: string
  operatorId: string
  operatorRole: string
  targetType: string
  targetId: string
  action: string
  detail?: string
  dataLevel?: string
  ipAddress?: string
  occurredAt: string
}

export interface DocumentTypeConfig {
  id: string
  typeCode: string
  typeName: string
  documentCategory: '法定公文' | '事务文书'
  applicableScenario?: string
  direction?: '上行' | '下行' | '平行' | '内部'
  requiredFields: string[]
  recommendedStructure: string[]
  forbiddenPatterns: string[]
  closingPatterns: string[]
  currentVersion: number
  lifecycleStatus: LifecycleStatus
  lockVersion: number
  updatedAt: string
}

export interface DocumentTypeInput {
  typeCode: string
  typeName: string
  documentCategory: '法定公文' | '事务文书'
  applicableScenario: string
  direction: '上行' | '下行' | '平行' | '内部'
  requiredFields: string[]
  recommendedStructure: string[]
  forbiddenPatterns: string[]
  closingPatterns: string[]
  changeDescription: string
}

export interface ConfigurationTestResult {
  passed: boolean
  errors?: string[]
  warnings?: string[]
  renderedPrompt?: string
  unresolvedPlaceholders?: string[]
  promptHash?: string
}

const data = async <T>(request: Promise<{ data: ApiEnvelope<T> }>) => (await request).data.data

export const listModelConfigs = () => data(http.get<ApiEnvelope<ModelConfig[]>>('/model-configs'))
export const createModelConfig = (input: ModelConfigInput) => data(http.post<ApiEnvelope<ModelConfig>>('/model-configs', input))

export const listPromptTemplates = () => data(http.get<ApiEnvelope<PromptTemplate[]>>('/prompt-templates'))
export const createPromptTemplate = (input: PromptTemplateInput) => data(http.post<ApiEnvelope<PromptTemplate>>('/prompt-templates', input))
export const testPromptTemplate = (id: string, variables: Record<string, unknown>) => data(http.post<ApiEnvelope<ConfigurationTestResult>>(`/prompt-templates/${id}/test`, { variables }))
export const submitPromptTemplate = (id: string) => data(http.post<ApiEnvelope<PromptTemplate>>(`/prompt-templates/${id}/submit-for-review`))
export const publishPromptTemplate = (id: string) => data(http.post<ApiEnvelope<PromptTemplate>>(`/prompt-templates/${id}/publish`))
export const deactivatePromptTemplate = (id: string) => data(http.patch<ApiEnvelope<PromptTemplate>>(`/prompt-templates/${id}/deactivate`))

export const listDocumentTypes = () => data(http.get<ApiEnvelope<DocumentTypeConfig[]>>('/document-type-configs'))
export const createDocumentType = (input: DocumentTypeInput) => data(http.post<ApiEnvelope<DocumentTypeConfig>>('/document-type-configs', input))
export const testDocumentType = (code: string) => data(http.post<ApiEnvelope<ConfigurationTestResult>>(`/document-type-configs/${encodeURIComponent(code)}/test`))
export const submitDocumentType = (code: string) => data(http.post<ApiEnvelope<DocumentTypeConfig>>(`/document-type-configs/${encodeURIComponent(code)}/submit-for-review`))
export const publishDocumentType = (code: string) => data(http.post<ApiEnvelope<DocumentTypeConfig>>(`/document-type-configs/${encodeURIComponent(code)}/publish`))
export const deactivateDocumentType = (code: string) => data(http.patch<ApiEnvelope<DocumentTypeConfig>>(`/document-type-configs/${encodeURIComponent(code)}/deactivate`))

export const listAuditLogs = (auditType?: string, targetId?: string) => data(http.get<ApiEnvelope<AuditLog[]>>('/audit-logs', { params: { auditType: auditType || undefined, targetId: targetId || undefined, limit: 200 } }))

/** §31.3 提交门禁（单位级配置） */
export interface SubmissionGateConfig { requireReview: boolean; requireLatestReview: boolean; blockOnSevere: boolean; requireImportantProcessed: boolean; requireReport: boolean; requiredSubmitRole?: string | null }
export const getSubmissionGate = () => data(http.get<ApiEnvelope<SubmissionGateConfig>>('/submission-gate'))
export const updateSubmissionGate = (config: SubmissionGateConfig) => data(http.put<ApiEnvelope<SubmissionGateConfig>>('/submission-gate', config))
