import { http, type ApiEnvelope } from './http'

export interface Organization { id: string; org_code: string; org_name: string; parent_id?: string; enabled: boolean }
export interface Role { id: string; role_code: string; role_name: string; description?: string; enabled: boolean }
export interface User { id: string; username: string; display_name: string; org_id?: string; enabled: boolean; roleIds: string[] }
export interface CurrentUser { username: string; displayName?: string; display_name?: string; org_id?: string; orgName?: string; roles: string[] }

export async function listOrganizations() { return (await http.get<ApiEnvelope<Organization[]>>('/identity/organizations')).data.data }
export async function listRoles() { return (await http.get<ApiEnvelope<Role[]>>('/identity/roles')).data.data }
export async function listUsers() { return (await http.get<ApiEnvelope<User[]>>('/identity/users')).data.data }
export async function currentUser() { return (await http.get<ApiEnvelope<CurrentUser>>('/identity/me')).data.data }
export async function saveOrganization(input: object, id?: string) { return (await (id ? http.put(`/identity/organizations/${id}`, input) : http.post('/identity/organizations', input))).data }
export async function saveRole(input: object, id?: string) { return (await (id ? http.put(`/identity/roles/${id}`, input) : http.post('/identity/roles', input))).data }
export async function saveUser(input: object, id?: string) { return (await (id ? http.put(`/identity/users/${id}`, input) : http.post('/identity/users', input))).data }
export async function deleteIdentity(kind: 'organizations' | 'roles' | 'users', id: string) { await http.delete(`/identity/${kind}/${id}`) }
