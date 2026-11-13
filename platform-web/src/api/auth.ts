import { http, type ApiEnvelope } from './http'

export interface TenantOption { tenant_code: string; tenant_name: string }
export interface LoginResult {
  token: string
  expiresAt: string
  tenantCode: string
  tenantName: string
  username: string
  displayName: string
  roles: string[]
}

export const listTenants = () => http.get<ApiEnvelope<TenantOption[]>>('/auth/tenants').then(response => response.data.data)
export const listManagedTenants = () => http.get<ApiEnvelope<TenantOption[]>>('/auth/tenants/managed').then(response => response.data.data)
export const updateTenant = (code: string, tenantName: string, enabled: boolean) => http.put(`/auth/tenants/${code}`, { tenantName, enabled })
export const login = (tenantCode: string, username: string, password: string) =>
  http.post<ApiEnvelope<LoginResult>>('/auth/login', { tenantCode, username, password }).then(response => response.data.data)
export const logout = () => http.post('/auth/logout')
export const changePassword = (currentPassword: string, newPassword: string) =>
  http.post('/auth/password', { currentPassword, newPassword })
export const createTenant = (input: { tenantCode: string; tenantName: string; adminUsername: string; adminDisplayName: string; adminPassword: string }) =>
  http.post('/auth/tenants', input)
