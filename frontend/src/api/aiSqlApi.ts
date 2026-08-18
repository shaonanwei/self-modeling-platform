import type { AiSqlChatRequest, AiSqlSseEvent } from '@/types/aiSql'
import { AiSqlStreamError } from '@/types/aiSql'
import { getAccessToken } from '@/utils/auth'
import { handleUnauthorized } from '@/utils/handleUnauthorized'
import { SseParser } from '@/utils/sseParser'

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/+$/, '')

export interface StreamAiSqlOptions {
  signal: AbortSignal
  onEvent: (event: AiSqlSseEvent) => void
}

/** 发起已认证的 AI SQL SSE 对话请求。 */
export async function streamAiSql(
  request: AiSqlChatRequest,
  options: StreamAiSqlOptions
): Promise<void> {
  const response = await fetchStream(request, options.signal)
  if (response.status === 401) {
    handleUnauthorized()
    throw new AiSqlStreamError('UNAUTHORIZED')
  }
  if (!response.ok || !response.body) {
    throw new AiSqlStreamError('STREAM_ERROR')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  const parser = new SseParser()
  let protocolCompleted = false
  let terminalEventSeen = false
  const forwardEvents = (events: AiSqlSseEvent[]) => {
    events.forEach(event => {
      if (event.type === 'done' || event.type === 'error') {
        terminalEventSeen = true
      }
      options.onEvent(event)
    })
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      if (value) {
        forwardEvents(parser.push(decoder.decode(value, { stream: true })))
      }
    }

    const tail = decoder.decode()
    if (tail) {
      forwardEvents(parser.push(tail))
    }
    forwardEvents(parser.finish())
    if (!terminalEventSeen) {
      throw new AiSqlStreamError('STREAM_ERROR')
    }
    protocolCompleted = true
  } catch (error) {
    if (error instanceof AiSqlStreamError || isAbortError(error)) {
      throw error
    }
    throw new AiSqlStreamError('STREAM_ERROR')
  } finally {
    if (!protocolCompleted) {
      try {
        await reader.cancel()
      } catch {
        // 读取失败或已中止时无需额外处理。
      }
    }
    reader.releaseLock()
  }
}

async function fetchStream(request: AiSqlChatRequest, signal: AbortSignal): Promise<Response> {
  const token = getAccessToken()
  try {
    return await fetch(`${API_BASE_URL}/api/v1/ai/sql/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(token ? { Authorization: token } : {})
      },
      body: JSON.stringify(request),
      signal
    })
  } catch (error) {
    if (isAbortError(error)) {
      throw error
    }
    throw new AiSqlStreamError('STREAM_ERROR')
  }
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}
