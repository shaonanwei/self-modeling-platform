import { describe, expect, it } from 'vitest'
import { AiSqlStreamError, SseParser } from '@/utils/sseParser'

describe('SseParser', () => {
  it('parses events split across network chunks', () => {
    const parser = new SseParser()

    expect(parser.push('event: del')).toEqual([])
    expect(parser.push('ta\ndata: {"content":"SEL"}\n\n')).toEqual([
      { type: 'delta', data: { content: 'SEL' } }
    ])
  })

  it('supports CRLF delimiters and multiline data', () => {
    const parser = new SseParser()

    expect(parser.push('event: error\r\ndata: {"code":"X",\r\n')).toEqual([])
    expect(parser.push('data: "message":"失败","retryable":false}\r\n\r\n')).toEqual([
      { type: 'error', data: { code: 'X', message: '失败', retryable: false } }
    ])
  })

  it('supports bare CR and CRLF split across chunks', () => {
    const bareCrParser = new SseParser()
    const splitCrLfParser = new SseParser()

    expect(bareCrParser.push('event: delta\rdata: {"content":"A"}\r\r')).toEqual([
      { type: 'delta', data: { content: 'A' } }
    ])
    expect(splitCrLfParser.push('event: delta\r')).toEqual([])
    expect(splitCrLfParser.push('\ndata: {"content":"B"}\r\n\r')).toEqual([
      { type: 'delta', data: { content: 'B' } }
    ])
    expect(splitCrLfParser.push('\n')).toEqual([])
  })

  it('ignores comment and id/retry heartbeat frames', () => {
    const parser = new SseParser()

    expect(parser.push(': keepalive\n\nid: 12\nretry: 1000\n\n')).toEqual([])
  })

  it('validates each event payload and allows done finishReason', () => {
    const parser = new SseParser()

    expect(parser.push('event: status\ndata: {"message":"生成中"}\n\n')).toEqual([
      { type: 'status', data: { message: '生成中' } }
    ])
    expect(parser.push('event: delta\ndata: {"content":"SELECT"}\n\n')).toEqual([
      { type: 'delta', data: { content: 'SELECT' } }
    ])
    expect(parser.push('event: sql\ndata: {"sql":"SELECT 1","valid":true,"message":"通过"}\n\n')).toEqual([
      { type: 'sql', data: { sql: 'SELECT 1', valid: true, message: '通过' } }
    ])
    expect(parser.push('event: error\ndata: {"code":"X","message":"失败","retryable":false}\n\n')).toEqual([
      { type: 'error', data: { code: 'X', message: '失败', retryable: false } }
    ])
    expect(parser.push('event: done\ndata: {"finishReason":"stop"}\n\n')).toEqual([
      { type: 'done', data: { finishReason: 'stop' } }
    ])

    for (const frame of [
      'event: status\ndata: {}\n\n',
      'event: delta\ndata: {"content":false}\n\n',
      'event: sql\ndata: {"sql":"SELECT 1","valid":"false","message":"通过"}\n\n',
      'event: error\ndata: {"code":"X","message":"失败"}\n\n',
      'event: done\ndata: {"finishReason":false}\n\n'
    ]) {
      expect(() => parser.push(frame)).toThrow(AiSqlStreamError)
    }
  })

  it('rejects unknown events, missing data, and malformed JSON safely', () => {
    const parser = new SseParser()

    for (const frame of [
      'event: unknown\ndata: {}\n\n',
      'event: done\n\n',
      'event: delta\ndata: {not-json}\n\n'
    ]) {
      expect(() => parser.push(frame)).toThrow(AiSqlStreamError)
      expect(() => parser.push(frame)).toThrow(/流式响应异常/)
    }
  })

  it('rejects an unclosed final frame safely', () => {
    const parser = new SseParser()

    parser.push('event: delta\ndata: {"content":"partial"}')

    expect(() => parser.finish()).toThrow(AiSqlStreamError)
  })
})
