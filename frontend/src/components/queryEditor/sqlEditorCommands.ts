import type * as monaco from 'monaco-editor'

/** 使用可撤销编辑替换 Monaco 编辑器中的全部 SQL。 */
export function replaceEditorText(
  editor: monaco.editor.IStandaloneCodeEditor,
  sql: string
): void {
  const model = editor.getModel()
  if (!model) return

  editor.pushUndoStop()
  editor.executeEdits('ai-sql-assistant', [{
    range: model.getFullModelRange(),
    text: sql,
    forceMoveMarkers: true
  }])
  editor.pushUndoStop()
  editor.focus()
}
