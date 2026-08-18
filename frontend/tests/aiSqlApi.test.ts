import { afterEach, describe, expect, it, vi } from 'vitest'
import { AiSqlStreamError, type AiSqlChatRequest, type AiSqlSseEvent } from '@/types/aiSql'

const { getAccessTokenMock, handleUnauthorizedMock } = vi.hoisted(() => ({
  getAccessTokenMock: vi.fn(() => 'test-token'),
  handleUnauthorizedMock: vi.fn()
}))

vi.mock('@/utils/auth', () => ({ getAccessToken: getAccessTokenMock }))
vi.mock('@/utils/handleUnauthorized', () => ({ handleUnauthorized: handleUnauthorizedMock }))

import { streamAiSql } from '@/api/aiSqlApi'

const request: AiSqlChatRequest = {
  dataSourceId: 'master',
  currentSql: '',
  messages: [{ role: 'user', content: '查询订单' }]
}

function streamResponse(chunks: string[]): Response {
  const encoder = new TextEncoder()
  return new Response(new ReadableStream({
    start(controller) {
      chunks.forEach(chunk => controller.enqueue(encoder.encode(chunk)))
      controller.close()
    }
  }), { status: 200 })
}

function responseWithReader(readResults: Array<ReadableStreamReadResult<Uint8Array>>) {
  const reader = {
    read: vi.fn(),
    cancel: vi.fn().mockResolvedValue(undefined),
    releaseLock: vi.fn()
  }
  readResults.forEach(result => reader.read.mockResolvedValueOnce(result))
  const response = {
    ok: true,
    status: 200,
    body: { getReader: vi.fn(() => reader) }
  } as unknown as Response

  return { response, reader }
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.clearAllMocks()
})

describe('streamAiSql', () => {
  it('posts messages with the current token and forwards parsed events', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(streamResponse([
      'event: delta\ndata: {"content":"SELECT"}\n\n',
      'event: done\ndata: {}\n\n'
    ])))
    const events: AiSqlSseEvent[] = []

    await streamAiSql(request, {
      signal: new AbortController().signal,
      onEvent: event => events.push(event)
    })

    expect(fetch).toHaveBeenCalledWith('/api/v1/ai/sql/chat', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({
        Authorization: 'test-token',
        Accept: 'text/event-stream'
      }),
      body: JSON.stringify(request)
    }))
    expect(events.map(event => event.type)).toEqual(['delta', 'done'])
  })

  it('uses the shared unauthorized handler for a 401 response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 401 })))

    await expect(streamAiSql(request, {
      signal: new AbortController().signal,
      onEvent: vi.fn()
    })).rejects.toMatchObject({ code: 'UNAUTHORIZED' })

    expect(handleUnauthorizedMock).toHaveBeenCalledOnce()
  })

  it('returns a fixed safe error for failed responses and malformed streams', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('sensitive response', { status: 503 })))

    await expect(streamAiSql(request, {
      signal: new AbortController().signal,
      onEvent: vi.fn()
    })).rejects.toEqual(new AiSqlStreamError('STREAM_ERROR'))

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(streamResponse([
      'event: unsupported\ndata: {"sql":"sensitive SQL"}\n\n'
    ])))

    await expect(streamAiSql(request, {
      signal: new AbortController().signal,
      onEvent: vi.fn()
    })).rejects.toEqual(new AiSqlStreamError('STREAM_ERROR'))
  })

  it('rejects empty and truncated successful streams with a fixed safe error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(streamResponse([])))

    await expect(streamAiSql(request, {
      signal: new AbortController().signal,
      onEvent: vi.fn()
    })).rejects.toEqual(new AiSqlStreamError('STREAM_ERROR'))

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(streamResponse([
      'event: delta\ndata: {"content":"partial"}\n\n'
    ])))

    await expect(streamAiSql(request, {
      signal: new AbortController().signal,
      onEvent: vi.fn()
    })).rejects.toEqual(new AiSqlStreamError('STREAM_ERROR'))
  })

  it('releases but does not cancel the reader after a terminal event reaches EOF', async () => {
    const encoder = new TextEncoder()
    const { response, reader } = responseWithReader([
      { done: false, value: encoder.encode('event: done\ndata: {}\n\n') },
      { done: true, value: undefined }
    ])
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response))

    await streamAiSql(request, {
      signal: new AbortController().signal,
      onEvent: vi.fn()
    })

    expect(reader.cancel).not.toHaveBeenCalled()
    expect(reader.releaseLock).toHaveBeenCalledOnce()
  })

  it('cancels and releases the reader after a stream parsing failure', async () => {
    const encoder = new TextEncoder()
    const { response, reader } = responseWithReader([
      { done: false, value: encoder.encode('event: unknown\ndata: {}\n\n') }
    ])
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response))

    await expect(streamAiSql(request, {
      signal: new AbortController().signal,
      onEvent: vi.fn()
    })).rejects.toEqual(new AiSqlStreamError('STREAM_ERROR'))

    expect(reader.cancel).toHaveBeenCalledOnce()
    expect(reader.releaseLock).toHaveBeenCalledOnce()
  })

  it('cancels and releases the reader after truncated EOF without a terminal event', async () => {
    const encoder = new TextEncoder()
    const { response, reader } = responseWithReader([
      { done: false, value: encoder.encode('event: delta\ndata: {"content":"partial"}\n\n') },
      { done: true, value: undefined }
    ])
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response))

    await expect(streamAiSql(request, {
      signal: new AbortController().signal,
      onEvent: vi.fn()
    })).rejects.toEqual(new AiSqlStreamError('STREAM_ERROR'))

    expect(reader.cancel).toHaveBeenCalledOnce()
    expect(reader.releaseLock).toHaveBeenCalledOnce()
  })

  it('releases the reader after an abort', async () => {
    const abortError = new DOMException('请求已取消', 'AbortError')
    const reader = {
      read: vi.fn().mockRejectedValue(abortError),
      cancel: vi.fn().mockResolvedValue(undefined),
      releaseLock: vi.fn()
    }
    const response = {
      ok: true,
      status: 200,
      body: { getReader: vi.fn(() => reader) }
    } as unknown as Response
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response))

    await expect(streamAiSql(request, {
      signal: new AbortController().signal,
      onEvent: vi.fn()
    })).rejects.toBe(abortError)

    expect(reader.cancel).toHaveBeenCalledOnce()
    expect(reader.releaseLock).toHaveBeenCalledOnce()
  })
})
