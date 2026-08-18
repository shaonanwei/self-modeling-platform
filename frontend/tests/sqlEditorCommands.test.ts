import { describe, expect, it, vi } from 'vitest'
import { replaceEditorText } from '@/components/queryEditor/sqlEditorCommands'

describe('replaceEditorText', () => {
  it('通过可撤销编辑替换完整 SQL 内容', () => {
    const model = { getFullModelRange: vi.fn(() => ({ marker: 'all' })) }
    const editor = {
      getModel: vi.fn(() => model),
      pushUndoStop: vi.fn(),
      executeEdits: vi.fn(() => true),
      focus: vi.fn()
    }

    replaceEditorText(editor as never, 'SELECT 1')

    expect(editor.pushUndoStop).toHaveBeenCalledTimes(2)
    expect(editor.executeEdits).toHaveBeenCalledWith('ai-sql-assistant', [{
      range: { marker: 'all' },
      text: 'SELECT 1',
      forceMoveMarkers: true
    }])
    expect(editor.focus).toHaveBeenCalledOnce()
  })

  it('编辑器没有模型时不执行替换', () => {
    const editor = {
      getModel: vi.fn(() => null),
      pushUndoStop: vi.fn(),
      executeEdits: vi.fn(),
      focus: vi.fn()
    }

    replaceEditorText(editor as never, 'SELECT 1')

    expect(editor.pushUndoStop).not.toHaveBeenCalled()
    expect(editor.executeEdits).not.toHaveBeenCalled()
    expect(editor.focus).not.toHaveBeenCalled()
  })
})
