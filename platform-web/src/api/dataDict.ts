import { http, type ApiEnvelope } from './http'

export interface DataDictType {
  id: string
  type_code: string
  type_name: string
  description?: string
  sort_order: number
  enabled: boolean
  lock_version: number
}

export interface DataDictItem {
  id: string
  type_code: string
  item_code: string
  item_label: string
  sort_order: number
  enabled: boolean
  lock_version: number
}

const data = async <T>(request: Promise<{ data: ApiEnvelope<T> }>) => (await request).data.data

export const listDataDictTypes = () => data(http.get<ApiEnvelope<DataDictType[]>>('/data-dicts'))
export const listDataDictItems = (typeCode: string, enabledOnly = true) =>
  data(http.get<ApiEnvelope<DataDictItem[]>>(`/data-dicts/${encodeURIComponent(typeCode)}/items`, { params: { enabledOnly } }))
export const createDataDictType = (payload: { typeCode: string; typeName: string; description?: string; sortOrder?: number; enabled?: boolean }) =>
  data(http.post<ApiEnvelope<DataDictType>>('/data-dicts', payload))
export const updateDataDictType = (id: string, payload: { typeCode: string; typeName: string; description?: string; sortOrder?: number; enabled?: boolean }) =>
  data(http.put<ApiEnvelope<DataDictType>>(`/data-dicts/${id}`, payload))
export const deleteDataDictType = (id: string) => data(http.delete<ApiEnvelope<null>>(`/data-dicts/${id}`))
export const createDataDictItem = (typeCode: string, payload: { itemCode: string; itemLabel: string; sortOrder?: number; enabled?: boolean }) =>
  data(http.post<ApiEnvelope<DataDictItem>>(`/data-dicts/${encodeURIComponent(typeCode)}/items`, payload))
export const updateDataDictItem = (id: string, payload: { itemCode: string; itemLabel: string; sortOrder?: number; enabled?: boolean }) =>
  data(http.put<ApiEnvelope<DataDictItem>>(`/data-dicts/items/${id}`, payload))
export const deleteDataDictItem = (id: string) => data(http.delete<ApiEnvelope<null>>(`/data-dicts/items/${id}`))
