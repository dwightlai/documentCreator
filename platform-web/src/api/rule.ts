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
export const createRule = (payload: CreateRulePayload) => data(http.post<ApiEnvelope<ReviewRule>>('/rules', payload))
export const testRule = (id: string, text: string, expectedMatchCount: number) =>
  data(http.post<ApiEnvelope<{ passed: boolean; caseCount: number; passedCount: number }>>(`/rules/${id}/test`, {
    cases: [{ text, expectedMatchCount }],
  }))
export const submitRule = (id: string) => data(http.post<ApiEnvelope<ReviewRule>>(`/rules/${id}/submit-for-review`))
export const publishRule = (id: string) => data(http.post<ApiEnvelope<ReviewRule>>(`/rules/${id}/publish`))
export const deactivateRule = (id: string) => data(http.patch<ApiEnvelope<ReviewRule>>(`/rules/${id}/deactivate`))

export const listDictionaries = () => data(http.get<ApiEnvelope<Dictionary[]>>('/dictionaries'))
export const createDictionary = (payload: Pick<Dictionary, 'dictCode' | 'dictName' | 'dictType'> & { sourceDescription: string }) =>
  data(http.post<ApiEnvelope<Dictionary>>('/dictionaries', payload))
export const listTerms = (dictionaryId: string) => data(http.get<ApiEnvelope<DictionaryTerm[]>>(`/dictionaries/${dictionaryId}/terms`))
export const createTerm = (dictionaryId: string, payload: {
  termText: string; standardForm: string; aliases: string[]; categoryTag: string; riskLevel: string; sourceReference: string; usageNote: string
}) => data(http.post<ApiEnvelope<DictionaryTerm>>(`/dictionaries/${dictionaryId}/terms`, payload))
export const importTerms = async (dictionaryId: string, file: File) => {
  const form = new FormData()
  form.append('file', file)
  return data(http.post<ApiEnvelope<{ totalRows: number; importedRows: number; skippedRows: number; errors: string[] }>>(
    `/dictionaries/${dictionaryId}/import`, form,
  ))
}
