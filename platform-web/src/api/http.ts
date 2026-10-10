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
  if (typeof FormData !== 'undefined' && config.data instanceof FormData) {
    config.headers.delete('Content-Type')
  }
  return config
})

http.interceptors.response.use((response) => {
  const envelope = response.data as ApiEnvelope<unknown>
  if (envelope && typeof envelope.code === 'number' && envelope.code >= 400) {
    return Promise.reject(new Error(envelope.message || '请求失败'))
  }
  return response
}, (error) => {
  const message = error?.response?.data?.message
  return Promise.reject(message ? new ApiError(message, error.response) : error)
})

/** 携带服务端业务消息的请求错误，保留 HTTP 状态以便调用方区分冲突、未找到等情况。 */
export class ApiError extends Error {
  readonly status?: number
  readonly response?: { status?: number }

  constructor(message: string, response?: { status?: number }) {
    super(message)
    this.name = 'ApiError'
    this.status = response?.status
    this.response = response
  }
}

export const isConflict = (error: unknown) => error instanceof ApiError && error.status === 409
