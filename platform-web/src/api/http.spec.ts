import { afterEach, describe, expect, it, vi } from 'vitest'
import { randomId } from './http'

describe('randomId', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('uses randomUUID in secure contexts', () => {
    expect(randomId()).toMatch(/^[0-9a-f]{32}$/)
  })

  it('falls back to getRandomValues when randomUUID is unavailable (plain HTTP via IP)', () => {
    vi.stubGlobal('crypto', { getRandomValues: (array: Uint8Array) => array.fill(171) })
    expect(randomId()).toBe('ab'.repeat(16))
  })
})
