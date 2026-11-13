import { http, type ApiEnvelope } from './http'

export interface OpenApp {
  id: string
  app_code: string
  app_name: string
  app_type: string
  auth_type: string
  status: string
  created_at: string
  keys?: OpenAppKey[]
  grants?: OpenAppGrant[]
}
export interface OpenAppKey { id: string; key_prefix: string; status: string; created_at: string }
export interface OpenAppGrant { id: string; scope_type: string; scope_code: string }
export interface CreatedKey { id: string; keyPrefix: string; apiKey: string }

export async function listOpenApps() {
  return (await http.get<ApiEnvelope<OpenApp[]>>('/open-apps')).data.data
}
export async function getOpenApp(id: string) {
  return (await http.get<ApiEnvelope<OpenApp>>(`/open-apps/${id}`)).data.data
}
export async function createOpenApp(appCode: string, appName: string) {
  return (await http.post<ApiEnvelope<OpenApp>>('/open-apps', { appCode, appName })).data.data
}
export async function setOpenAppEnabled(id: string, enabled: boolean) {
  return (await http.put<ApiEnvelope<OpenApp>>(`/open-apps/${id}/status`, { enabled })).data.data
}
export async function deleteOpenApp(id: string) { await http.delete(`/open-apps/${id}`) }
export async function createOpenAppKey(id: string) {
  return (await http.post<ApiEnvelope<CreatedKey>>(`/open-apps/${id}/keys`)).data.data
}
export async function deleteOpenAppKey(id: string, keyId: string) { await http.delete(`/open-apps/${id}/keys/${keyId}`) }
export async function addOpenAppGrant(id: string, scopeType: string, scopeCode: string) {
  return (await http.post<ApiEnvelope<OpenAppGrant>>(`/open-apps/${id}/grants`, { scopeType, scopeCode })).data.data
}
export async function deleteOpenAppGrant(id: string, grantId: string) { await http.delete(`/open-apps/${id}/grants/${grantId}`) }
