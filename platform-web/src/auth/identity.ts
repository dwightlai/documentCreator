import Keycloak from 'keycloak-js'

const oidcUrl = import.meta.env.VITE_OIDC_URL
const client = oidcUrl ? new Keycloak({
  url: oidcUrl,
  realm: import.meta.env.VITE_OIDC_REALM || 'document-platform',
  clientId: import.meta.env.VITE_OIDC_CLIENT_ID || 'document-platform-web',
}) : undefined

export async function initializeIdentity() {
  if (!client) return
  await client.init({ onLoad: 'login-required', pkceMethod: 'S256', checkLoginIframe: false })
  window.setInterval(() => {
    client.updateToken(60).catch(() => client.login())
  }, 30_000)
}

export async function accessToken() {
  if (!client) return undefined
  await client.updateToken(30)
  return client.token
}

export function usesOidc() { return Boolean(client) }

const sessionKey = 'platform.session'

export function sessionToken() {
  return localStorage.getItem(sessionKey) || ''
}

export function saveSession(token: string) {
  localStorage.setItem(sessionKey, token)
}

export function clearSession() {
  localStorage.removeItem(sessionKey)
}

export async function authorizationHeader(): Promise<Record<string, string>> {
  if (usesOidc()) {
    const token = await accessToken()
    return token ? { Authorization: `Bearer ${token}` } : {}
  }
  const token = sessionToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}
