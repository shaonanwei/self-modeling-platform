import {
  AiSqlStreamError,
  type AiSqlDeltaEventData,
  type AiSqlDoneEventData,
  type AiSqlErrorEventData,
  type AiSqlEventType,
  type AiSqlSseEvent,
  type AiSqlSqlEventData,
  type AiSqlStatusEventData
} from '@/types/aiSql'

const EVENT_TYPES: ReadonlySet<AiSqlEventType> = new Set([
  'status',
  'delta',
  'sql',
  'error',
  'done'
])

export { AiSqlStreamError }

/** 将网络分块按 SSE 行协议还原为经过校验的 AI SQL 事件。 */
export class SseParser {
  private currentLine = ''
  private skipNextLf = false
  private eventType?: string
  private dataLines: string[] = []

  push(chunk: string): AiSqlSseEvent[] {
    const events: AiSqlSseEvent[] = []

    for (const character of chunk) {
      if (this.skipNextLf) {
        this.skipNextLf = false
        if (character === '\n') {
          continue
        }
      }
      if (character === '\r') {
        this.processLine(events)
        this.skipNextLf = true
      } else if (character === '\n') {
        this.processLine(events)
      } else {
        this.currentLine += character
      }
    }

    return events
  }

  finish(): AiSqlSseEvent[] {
    if (this.currentLine || this.eventType !== undefined || this.dataLines.length > 0) {
      throw new AiSqlStreamError('STREAM_ERROR')
    }
    return []
  }

  private processLine(events: AiSqlSseEvent[]): void {
    const line = this.currentLine
    this.currentLine = ''

    if (!line) {
      this.dispatch(events)
      return
    }
    if (line.startsWith(':')) {
      return
    }

    const separatorIndex = line.indexOf(':')
    const field = separatorIndex === -1 ? line : line.slice(0, separatorIndex)
    let value = separatorIndex === -1 ? '' : line.slice(separatorIndex + 1)
    if (value.startsWith(' ')) {
      value = value.slice(1)
    }

    if (field === 'event') {
      this.eventType = value
    } else if (field === 'data') {
      this.dataLines.push(value)
    }
  }

  private dispatch(events: AiSqlSseEvent[]): void {
    const type = this.eventType
    const dataLines = this.dataLines
    this.eventType = undefined
    this.dataLines = []

    if (type === undefined && dataLines.length === 0) {
      return
    }
    if (!isAiSqlEventType(type) || dataLines.length === 0) {
      throw new AiSqlStreamError('STREAM_ERROR')
    }

    events.push(parseFrame(type, dataLines))
  }
}

function parseFrame(type: AiSqlEventType, dataLines: string[]): AiSqlSseEvent {
  try {
    const value: unknown = JSON.parse(dataLines.join('\n'))
    if (!isDataObject(value)) {
      throw new AiSqlStreamError('STREAM_ERROR')
    }
    return toEvent(type, value)
  } catch (error) {
    if (error instanceof AiSqlStreamError) {
      throw error
    }
    throw new AiSqlStreamError('STREAM_ERROR')
  }
}

function toEvent(type: AiSqlEventType, data: Record<string, unknown>): AiSqlSseEvent {
  switch (type) {
    case 'status':
      if (typeof data.message === 'string') {
        const statusData: AiSqlStatusEventData = { ...data, message: data.message }
        return { type, data: statusData }
      }
      break
    case 'delta':
      if (typeof data.content === 'string') {
        const deltaData: AiSqlDeltaEventData = { ...data, content: data.content }
        return { type, data: deltaData }
      }
      break
    case 'sql':
      if (typeof data.sql === 'string' && typeof data.valid === 'boolean'
        && typeof data.message === 'string') {
        const sqlData: AiSqlSqlEventData = {
          ...data,
          sql: data.sql,
          valid: data.valid,
          message: data.message
        }
        return { type, data: sqlData }
      }
      break
    case 'error':
      if (typeof data.code === 'string' && typeof data.message === 'string'
        && typeof data.retryable === 'boolean') {
        const errorData: AiSqlErrorEventData = {
          ...data,
          code: data.code,
          message: data.message,
          retryable: data.retryable
        }
        return { type, data: errorData }
      }
      break
    case 'done':
      if (data.finishReason === undefined || typeof data.finishReason === 'string') {
        return { type, data: data as AiSqlDoneEventData }
      }
      break
  }
  throw new AiSqlStreamError('STREAM_ERROR')
}

function isAiSqlEventType(type: string | undefined): type is AiSqlEventType {
  return type !== undefined && EVENT_TYPES.has(type as AiSqlEventType)
}

function isDataObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
