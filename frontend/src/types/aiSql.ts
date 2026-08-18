export type AiSqlMessageRole = 'user' | 'assistant'

export interface AiSqlMessage {
  role: AiSqlMessageRole
  content: string
}

export interface AiSqlChatRequest {
  dataSourceId: string
  currentSql?: string
  messages: AiSqlMessage[]
}

export interface AiSqlStatusEventData {
  message: string
}

export interface AiSqlDeltaEventData {
  content: string
}

export interface AiSqlSqlEventData {
  sql: string
  valid: boolean
  message: string
}

export interface AiSqlErrorEventData {
  code: string
  message: string
  retryable: boolean
}

export interface AiSqlDoneEventData {
  finishReason?: string
}

export type AiSqlSseEvent =
  | { type: 'status'; data: AiSqlStatusEventData }
  | { type: 'delta'; data: AiSqlDeltaEventData }
  | { type: 'sql'; data: AiSqlSqlEventData }
  | { type: 'error'; data: AiSqlErrorEventData }
  | { type: 'done'; data: AiSqlDoneEventData }

export type AiSqlEventType = AiSqlSseEvent['type']

export type AiSqlStreamErrorCode = 'UNAUTHORIZED' | 'STREAM_ERROR'

const STREAM_ERROR_MESSAGE = 'AI SQL 流式响应异常'
const UNAUTHORIZED_MESSAGE = '登录状态已失效'

export class AiSqlStreamError extends Error {
  constructor(readonly code: AiSqlStreamErrorCode) {
    super(code === 'UNAUTHORIZED' ? UNAUTHORIZED_MESSAGE : STREAM_ERROR_MESSAGE)
    this.name = 'AiSqlStreamError'
  }
}
