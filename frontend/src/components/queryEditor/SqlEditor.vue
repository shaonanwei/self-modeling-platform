<template>
  <div ref="editorContainer" class="sql-editor-container"></div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import * as monaco from 'monaco-editor'
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker'
import { replaceEditorText } from './sqlEditorCommands'

self.MonacoEnvironment = {
  getWorker() {
    return new editorWorker()
  }
}

const props = defineProps<{
  sql: string
  flex?: number | string
}>()

const emit = defineEmits<{
  (e: 'update:sql', value: string): void
  (e: 'change', value: string): void
  (e: 'validate'): void
  (e: 'execute'): void
}>()

const editorContainer = ref<HTMLElement>()
let editor: monaco.editor.IStandaloneCodeEditor | null = null

onMounted(() => {
  if (!editorContainer.value) return

  editor = monaco.editor.create(editorContainer.value, {
    value: props.sql,
    language: 'sql',
    theme: 'vs-dark',
    minimap: { enabled: false },
    fontSize: 13,
    lineNumbers: 'on',
    scrollBeyondLastLine: false,
    automaticLayout: true,
    tabSize: 2,
    wordWrap: 'on',
    suggestOnTriggerCharacters: true,
    quickSuggestions: true,
    folding: true,
    bracketPairColorization: { enabled: true },
    padding: { top: 8, bottom: 8 }
  })

  editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => {
    emit('execute')
  })

  editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => {
    emit('validate')
  })

  let changeTimer: ReturnType<typeof setTimeout> | null = null
  editor.onDidChangeModelContent(() => {
    if (changeTimer) clearTimeout(changeTimer)
    changeTimer = setTimeout(() => {
      const val = editor?.getValue() || ''
      emit('update:sql', val)
      emit('change', val)
    }, 150)
  })
})

watch(() => props.sql, (newVal) => {
  if (editor && editor.getValue() !== newVal) {
    editor.setValue(newVal)
  }
})

function replaceAllSql(sql: string) {
  if (!editor) return
  replaceEditorText(editor, sql)
  emit('update:sql', sql)
  emit('change', sql)
}

defineExpose({ replaceAllSql })

onBeforeUnmount(() => {
  editor?.dispose()
})
</script>

<style scoped>
.sql-editor-container {
  width: 100%;
  flex: 1;
  min-height: 0;
}
</style>
