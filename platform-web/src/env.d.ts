/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_TENANT_ID?: string
  readonly VITE_USER_ID?: string
  readonly VITE_OIDC_URL?: string
  readonly VITE_OIDC_REALM?: string
  readonly VITE_OIDC_CLIENT_ID?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
