import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AiSqlSseEvent } from '@/types/aiSql'

const { streamAiSqlMock } = vi.hoisted(() => ({
  streamAiSqlMock: vi.fn()
}))

vi.mock('@/api/aiSqlApi', () => ({ streamAiSql: streamAiSqlMock }))

import AiSqlDrawer from '@/components/queryEditor/AiSqlDrawer.vue'

function mountDrawer(messages = [{ role: 'assistant' as const, content: '已有回答' }]) {
  return mount(AiSqlDrawer, {
    props: {
      visible: true,
      dataSourceId: 'master',
      currentSql: 'SELECT * FROM orders',
      messages
    },
    global: {
      stubs: {
        ElDrawer: {
          name: 'ElDrawer',
          props: ['modelValue'],
          template: '<section v-if="modelValue"><slot name="header" /><slot /></section>'
        },
        ElButton: { template: '<button><slot /></button>' },
        ElInput: {
          props: ['modelValue'],
          emits: ['update:modelValue'],
          template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
        },
        ElTag: { template: '<span><slot /></span>' }
      }
    }
  })
}

async function submit(wrapper: ReturnType<typeof mountDrawer>, content: string) {
  await wrapper.get('[data-test="ai-sql-input"]').setValue(content)
  await wrapper.get('[data-test="send-ai-message"]').trigger('click')
  await flushPromises()
}

function deferredStream() {
  let resolve: (() => void) | undefined
  const promise = new Promise<void>(completion => {
    resolve = completion
  })
  return { promise, resolve: () => resolve?.() }
}

afterEach(() => {
  vi.clearAllMocks()
  vi.restoreAllMocks()
})

describe('AiSqlDrawer', () => {
  it('仅关闭抽屉时保留既有消息', async () => {
    const wrapper = mountDrawer()

    await wrapper.get('[data-test="close-ai-drawer"]').trigger('click')

    expect(wrapper.emitted('update:visible')).toEqual([[false]])
    expect(wrapper.emitted('update:messages')).toBeUndefined()
  })

  it('为抽屉提供可访问名称，并忽略关闭后的重复 visible 更新', async () => {
    const wrapper = mountDrawer()

    expect(wrapper.findComponent({ name: 'ElDrawer' }).attributes('title')).toBe('AI SQL 助手')
    await wrapper.get('[data-test="close-ai-drawer"]').trigger('click')
    await wrapper.setProps({ visible: false })
    wrapper.findComponent({ name: 'ElDrawer' }).vm.$emit('update:modelValue', false)

    expect(wrapper.emitted('update:visible')).toEqual([[false]])
  })

  it('流式展示文本，且仅为有效 SQL 提供应用操作', async () => {
    streamAiSqlMock.mockImplementation(async (_request: unknown, options: { onEvent: (event: AiSqlSseEvent) => void }) => {
      options.onEvent({ type: 'delta', data: { content: '可使用：' } })
      options.onEvent({ type: 'sql', data: { sql: 'SELECT 1', valid: true, message: '校验通过' } })
      options.onEvent({ type: 'sql', data: { sql: 'DELETE FROM t', valid: false, message: '只允许 SELECT' } })
      options.onEvent({ type: 'done', data: { finishReason: 'stop' } })
    })
    const wrapper = mountDrawer([])

    await submit(wrapper, '生成查询')

    expect(wrapper.findAll('[data-test="apply-ai-sql"]')).toHaveLength(1)
    expect(wrapper.text()).toContain('SELECT 1')
    expect(wrapper.text()).toContain('DELETE FROM t')
    await wrapper.get('[data-test="apply-ai-sql"]').trigger('click')
    expect(wrapper.emitted('apply-sql')).toEqual([['SELECT 1']])
  })

  it('点击停止会中止仍在进行的请求', async () => {
    const stream = deferredStream()
    streamAiSqlMock.mockImplementation(() => stream.promise)
    const abortSpy = vi.spyOn(AbortController.prototype, 'abort')
    const wrapper = mountDrawer([])

    await submit(wrapper, '生成查询')
    await wrapper.get('[data-test="stop-ai-generation"]').trigger('click')
    stream.resolve()
    await flushPromises()

    expect(abortSpy).toHaveBeenCalledOnce()
  })

  it('已中止的片段不会写回后续对话上下文', async () => {
    const stream = deferredStream()
    streamAiSqlMock.mockImplementation(async (_request: unknown, options: { onEvent: (event: AiSqlSseEvent) => void }) => {
      options.onEvent({ type: 'delta', data: { content: '未完成内容' } })
      await stream.promise
    })
    const wrapper = mountDrawer([])

    await submit(wrapper, '生成查询')
    await wrapper.get('[data-test="stop-ai-generation"]').trigger('click')
    stream.resolve()
    await flushPromises()

    const messages = wrapper.emitted('update:messages')
    expect(messages).toEqual([[[{ role: 'user', content: '生成查询' }]]])
    expect(wrapper.text()).toContain('未完成内容')
  })

  it('组件销毁时会中止仍在进行的请求', async () => {
    const stream = deferredStream()
    streamAiSqlMock.mockImplementation(() => stream.promise)
    const abortSpy = vi.spyOn(AbortController.prototype, 'abort')
    const wrapper = mountDrawer([])

    await submit(wrapper, '生成查询')
    wrapper.unmount()
    stream.resolve()
    await flushPromises()

    expect(abortSpy).toHaveBeenCalledOnce()
  })

  it('清空操作显式清空消息，重新生成复用最后一个用户问题', async () => {
    streamAiSqlMock.mockImplementation(async (_request: unknown, options: { onEvent: (event: AiSqlSseEvent) => void }) => {
      options.onEvent({ type: 'done', data: {} })
    })
    const wrapper = mountDrawer([{ role: 'user', content: '上一次问题' }])

    await wrapper.get('[data-test="regenerate-ai-message"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-test="clear-ai-messages"]').trigger('click')

    expect(streamAiSqlMock).toHaveBeenCalledWith(expect.objectContaining({
      messages: [{ role: 'user', content: '上一次问题' }]
    }), expect.any(Object))
    expect(wrapper.emitted('update:messages')).toContainEqual([[]])
  })

  it('重新生成会移除最后一个用户问题之后的旧回复', async () => {
    streamAiSqlMock.mockImplementation(async (_request: unknown, options: { onEvent: (event: AiSqlSseEvent) => void }) => {
      options.onEvent({ type: 'done', data: {} })
    })
    const wrapper = mountDrawer([
      { role: 'user', content: '请查询订单' },
      { role: 'assistant', content: '旧回答' }
    ])

    await wrapper.get('[data-test="regenerate-ai-message"]').trigger('click')
    await flushPromises()

    expect(streamAiSqlMock).toHaveBeenCalledWith(expect.objectContaining({
      messages: [{ role: 'user', content: '请查询订单' }]
    }), expect.any(Object))
  })
})
