import { http, type ApiEnvelope } from './http'

export interface ReviewRule {
  id: string
  ruleCode: string
  ruleName: string
  category: string
  applicableDocumentTypes: string[]
  riskLevel: string
  detectionMethod: string
  ruleExpression: string
  basis?: string
  lifecycleStatus: '草稿' | '测试' | '审核' | '生效' | '停用'
  effectiveDate?: string
  expiryDate?: string
  currentVersion: number
  enabled: boolean
  lockVersion: number
  updatedAt: string
}

export interface Dictionary {
  id: string
  dictCode: string
  dictName: string
  dictType: string
  sourceDescription?: string
  effectiveDate?: string
  expiryDate?: string
  currentVersion: number
  entryCount: number
  lockVersion: number
  updatedAt: string
}

export interface DictionaryTerm {
  id: string
  dictionaryId: string
  termText: string
  standardForm?: string
  aliases: string[]
  categoryTag?: string
  riskLevel?: string
  sourceReference?: string
  usageNote?: string
  lockVersion: number
  updatedAt: string
}

export interface CreateRulePayload {
  ruleCode: string
  ruleName: string
  category: string
  applicableDocumentTypes: string[]
  riskLevel: string
  detectionMethod: string
  ruleExpression: string
  basis: string
  changeDescription: string
}

async function data<T>(request: Promise<{ data: ApiEnvelope<T> }>) {
  return (await request).data.data
}

export const listRules = () => data(http.get<ApiEnvelope<ReviewRule[]>>('/rules'))
export interface OfficialReviewPackInfo { code:string;version:string;title:string;scope:string;ruleCount:number;automaticRuleCount:number;termCount:number;installed:boolean }
export const getOfficialReviewPack = () => data(http.get<ApiEnvelope<OfficialReviewPackInfo>>('/standards/review-pack'))
export const installOfficialReviewPack = () => data(http.post<ApiEnvelope<{installed:boolean;dictionaries:number;terms:number;rules:number}>>('/standards/review-pack/install'))
export interface OfflineEvidencePackInfo {
  code: string; version: string; preparedOn: string; sha256: string; tenantOverride: boolean
  institutions: number; positions: number; politicalExpressions: number; policies: number; verifiedEntries: number
}
export const getOfflineEvidencePack = () => data(http.get<ApiEnvelope<OfflineEvidencePackInfo>>('/review/offline-evidence'))
export const exportOfflineEvidencePack = () => data(http.get<ApiEnvelope<unknown>>('/review/offline-evidence/export'))
export interface OfflineSourceSnapshot { id: string; sourceUrl: string; sha256: string; text: string }
export const listOfflineEvidenceSnapshots = () => data(http.get<ApiEnvelope<OfflineSourceSnapshot[]>>('/review/offline-evidence/snapshots'))
export const listOfflineEvidenceVersions = () => data(http.get<ApiEnvelope<OfflineEvidencePackInfo[]>>('/review/offline-evidence/history'))
export const importOfflineEvidencePack = (file: File) => {
  const body = new FormData(); body.append('file', file)
  return data(http.post<ApiEnvelope<OfflineEvidencePackInfo>>('/review/offline-evidence/import', body))
}
export const activateOfflineEvidenceVersion = (digest: string) => data(http.post<ApiEnvelope<OfflineEvidencePackInfo>>(`/review/offline-evidence/versions/${digest}/activate`))
export interface RuleImportResult { total: number; imported: number; skipped: number; termsImported: number; skippedCodes: string[] }
export const exportRulesJson = () => data(http.get<ApiEnvelope<unknown>>('/rules/export'))
export const importRulesJson = (bundle: unknown) => data(http.post<ApiEnvelope<RuleImportResult>>('/rules/import', bundle))
export const exportDictionariesJson = () => data(http.get<ApiEnvelope<unknown>>('/dictionaries/export'))
export const importDictionariesJson = (bundle: unknown) => data(http.post<ApiEnvelope<RuleImportResult>>('/dictionaries/import', bundle))
export const createRule = (payload: CreateRulePayload) => data(http.post<ApiEnvelope<ReviewRule>>('/rules', payload))
export const updateRule = (id: string, payload: Omit<CreateRulePayload, 'ruleCode'> & { lockVersion: number }) =>
  data(http.put<ApiEnvelope<ReviewRule>>(`/rules/${id}`, payload))
export const deleteRule = (id: string) => data(http.delete<ApiEnvelope<null>>(`/rules/${id}`))
export const testRule = (id: string, text: string, expectedMatchCount: number) =>
  data(http.post<ApiEnvelope<{ passed: boolean; caseCount: number; passedCount: number; results: Array<{ index: number; actualMatchCount: number; expectedMatchCount: number; passed: boolean; matches: string[] }> }>>(`/rules/${id}/test`, {
    cases: [{ text, expectedMatchCount }],
  }))
export const submitRule = (id: string) => data(http.post<ApiEnvelope<ReviewRule>>(`/rules/${id}/submit-for-review`))
export const publishRule = (id: string) => data(http.post<ApiEnvelope<ReviewRule>>(`/rules/${id}/publish`))
export const deactivateRule = (id: string) => data(http.patch<ApiEnvelope<ReviewRule>>(`/rules/${id}/deactivate`))
export const reactivateRule = (id: string) => data(http.patch<ApiEnvelope<ReviewRule>>(`/rules/${id}/reactivate`))

export const listDictionaries = () => data(http.get<ApiEnvelope<Dictionary[]>>('/dictionaries'))
export const createDictionary = (payload: Pick<Dictionary, 'dictCode' | 'dictName' | 'dictType'> & { sourceDescription: string }) =>
  data(http.post<ApiEnvelope<Dictionary>>('/dictionaries', payload))
export const updateDictionary = (id: string, payload: Pick<Dictionary, 'dictName' | 'dictType' | 'sourceDescription' | 'lockVersion'>) =>
  data(http.put<ApiEnvelope<Dictionary>>(`/dictionaries/${id}`, payload))
export const deleteDictionary = (id: string) => data(http.delete<ApiEnvelope<null>>(`/dictionaries/${id}`))
export const listTerms = (dictionaryId: string) => data(http.get<ApiEnvelope<DictionaryTerm[]>>(`/dictionaries/${dictionaryId}/terms`))
export const createTerm = (dictionaryId: string, payload: {
  termText: string; standardForm: string; aliases: string[]; categoryTag: string; riskLevel: string; sourceReference: string; usageNote: string
}) => data(http.post<ApiEnvelope<DictionaryTerm>>(`/dictionaries/${dictionaryId}/terms`, payload))
export const updateTerm = (dictionaryId: string, termId: string, payload: {
  termText: string; standardForm: string; aliases: string[]; categoryTag: string; riskLevel: string; sourceReference: string; usageNote: string; lockVersion: number
}) => data(http.put<ApiEnvelope<DictionaryTerm>>(`/dictionaries/${dictionaryId}/terms/${termId}`, payload))
export const deleteTerm = (dictionaryId: string, termId: string) =>
  data(http.delete<ApiEnvelope<null>>(`/dictionaries/${dictionaryId}/terms/${termId}`))
export const importTerms = async (dictionaryId: string, file: File) => {
  const form = new FormData()
  form.append('file', file)
  return data(http.post<ApiEnvelope<{ totalRows: number; importedRows: number; skippedRows: number; errors: string[] }>>(
    `/dictionaries/${dictionaryId}/import`, form,
  ))
}
