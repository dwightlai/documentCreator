import axios from 'axios'
import { accessToken, usesOidc } from '@/auth/identity'

export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
  requestId: string
  timestamp: string
}

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
})

http.interceptors.request.use(async (config) => {
  if (usesOidc()) {
    const token = await accessToken()
    config.headers.set('Authorization', `Bearer ${token}`)
  } else {
    config.headers.set('X-Tenant-Id', import.meta.env.VITE_TENANT_ID || 'local-government')
    config.headers.set('X-User-Id', import.meta.env.VITE_USER_ID || 'local-user')
  }
  config.headers.set('X-Request-Id', crypto.randomUUID().replaceAll('-', ''))
  return config
})

http.interceptors.response.use((response) => {
  const envelope = response.data as ApiEnvelope<unknown>
  if (envelope.code >= 400) return Promise.reject(new Error(envelope.message || '请求失败'))
  return response
})
