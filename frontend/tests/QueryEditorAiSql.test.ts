import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { useQueryEditorStore } from '@/stores/queryEditorStore'

const { sqlEditorReplaceMock, sqlExecuteMock, parseSqlToCanvasMock } = vi.hoisted(() => ({
  sqlEditorReplaceMock: vi.fn(),
  sqlExecuteMock: vi.fn(),
  parseSqlToCanvasMock: vi.fn()
}))

vi.mock('@/api/sqlApi', () => ({
  sqlApi: {
    execute: sqlExecuteMock,
    validate: vi.fn(),
    getSmartRecommend: vi.fn()
  }
}))

vi.mock('@/components/queryEditor/SqlEditor.vue', async () => {
  const { defineComponent, h } = await import('vue')
  return {
    default: defineComponent({
      name: 'SqlEditor',
      setup(_, { expose }) {
        expose({ replaceAllSql: sqlEditorReplaceMock })
        return () => h('div')
      }
    })
  }
})

import QueryEditor from '@/components/queryEditor/QueryEditor.vue'

const AiSqlDrawerStub = defineComponent({
  name: 'AiSqlDrawer',
  emits: ['apply-sql', 'update:visible', 'update:messages'],
  setup(_, { emit }) {
    return () => h('button', {
      'data-test': 'apply-ai-sql',
      onClick: () => emit('apply-sql', 'SELECT 1')
    })
  }
})

function mountQueryEditor() {
  return mount(QueryEditor, {
    props: {
      dataSourceId: 'master',
      aiMessages: []
    },
    global: {
      stubs: {
        MetadataPanel: true,
        CanvasArea: true,
        PropertyPanel: true,
        SqlResultViewer: true,
        AiSqlDrawer: AiSqlDrawerStub,
        ElButton: { template: '<button><slot /></button>' },
        ElTooltip: { template: '<span><slot /></span>' },
        ElIcon: { template: '<span><slot /></span>' },
        ElDialog: { template: '<section><slot /></section>' }
      }
    }
  })
}

describe('QueryEditor AI SQL 集成', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('将 AI 生成 SQL 放在右侧画布按钮之前', () => {
    const wrapper = mountQueryEditor()
    const actions = wrapper.findAll('.qe-toolbar-right button')
      .map(button => button.text().trim())

    expect(actions).toEqual(['AI 生成 SQL', '画布', '校验'])
  })

  it('应用安全 AI SQL 仅更新编辑器和本地状态，不保存、不执行、不转换画布', async () => {
    const wrapper = mountQueryEditor()
    const store = useQueryEditorStore()
    store.parseSqlToCanvas = parseSqlToCanvasMock

    await wrapper.get('[data-test="apply-ai-sql"]').trigger('click')

    expect(sqlEditorReplaceMock).toHaveBeenCalledWith('SELECT 1')
    expect(store.sqlText).toBe('SELECT 1')
    expect(sqlExecuteMock).not.toHaveBeenCalled()
    expect(parseSqlToCanvasMock).not.toHaveBeenCalled()
  })

  it('将抽屉消息更新透传给父组件', async () => {
    const wrapper = mountQueryEditor()
    const messages = [{ role: 'user' as const, content: '统计订单' }]

    await wrapper.findComponent(AiSqlDrawerStub).vm.$emit('update:messages', messages)

    expect(wrapper.emitted('update:aiMessages')).toEqual([[messages]])
  })

  it('应用 AI SQL 时取消待执行的画布自动生成任务', async () => {
    vi.useFakeTimers()
    const wrapper = mountQueryEditor()
    const store = useQueryEditorStore()
    const generateSql = vi.spyOn(store, 'generateSql')
      .mockReturnValue('SELECT stale_canvas_sql')

    store.canvasConfig.limit = 321
    await wrapper.vm.$nextTick()
    await wrapper.get('[data-test="apply-ai-sql"]').trigger('click')
    await vi.advanceTimersByTimeAsync(250)

    expect(store.sqlText).toBe('SELECT 1')
		expect(generateSql).not.toHaveBeenCalled()
  })

  it('组件卸载时取消待执行的画布自动生成任务', async () => {
    vi.useFakeTimers()
    const wrapper = mountQueryEditor()
    const store = useQueryEditorStore()
    const generateSql = vi.spyOn(store, 'generateSql')
      .mockReturnValue('SELECT stale_canvas_sql')

    store.canvasConfig.limit = 654
    await wrapper.vm.$nextTick()
    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(250)

		expect(generateSql).not.toHaveBeenCalled()
  })
})
