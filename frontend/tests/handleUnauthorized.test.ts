import { afterEach, describe, expect, it, vi } from 'vitest'

const { clearTokensMock, replaceMock } = vi.hoisted(() => ({
  clearTokensMock: vi.fn(),
  replaceMock: vi.fn()
}))

vi.mock('@/utils/auth', () => ({ clearTokens: clearTokensMock }))
vi.mock('@/router', () => ({
  default: {
    replace: replaceMock,
    currentRoute: { value: { fullPath: '/models' } }
  }
}))

import { handleUnauthorized } from '@/utils/handleUnauthorized'

afterEach(() => {
  vi.runOnlyPendingTimers()
  vi.useRealTimers()
  vi.clearAllMocks()
})

describe('handleUnauthorized', () => {
  it('clears tokens and suppresses duplicate redirects for concurrent 401 responses', () => {
    vi.useFakeTimers()

    handleUnauthorized()
    handleUnauthorized()

    expect(clearTokensMock).toHaveBeenCalledOnce()
    expect(replaceMock).toHaveBeenCalledOnce()
    expect(replaceMock).toHaveBeenCalledWith({
      path: '/login',
      query: { redirect: '/models' }
    })

    vi.advanceTimersByTime(1000)
    handleUnauthorized()

    expect(clearTokensMock).toHaveBeenCalledTimes(2)
    expect(replaceMock).toHaveBeenCalledTimes(2)
  })

  it('releases the guard after a synchronous dependency failure', () => {
    vi.useFakeTimers()
    clearTokensMock.mockImplementationOnce(() => {
      throw new Error('storage unavailable')
    })

    expect(() => handleUnauthorized()).not.toThrow()
    handleUnauthorized()

    expect(clearTokensMock).toHaveBeenCalledTimes(2)
    expect(replaceMock).toHaveBeenCalledOnce()
  })

  it('releases the guard after router navigation rejects', async () => {
    vi.useFakeTimers()
    replaceMock.mockRejectedValueOnce(new Error('navigation failed'))

    handleUnauthorized()
    await Promise.resolve()
    handleUnauthorized()

    expect(replaceMock).toHaveBeenCalledTimes(2)
  })
})
