<template>
  <el-drawer
    :model-value="visible"
    direction="rtl"
    size="min(440px, 100vw)"
    title="AI SQL 助手"
    :with-header="false"
    append-to-body
    class="ai-sql-drawer"
    @update:model-value="handleVisibleChange"
  >
    <section class="ai-sql-panel" aria-label="AI SQL 助手">
      <header class="ai-sql-header">
        <div>
          <h2>AI SQL 助手</h2>
          <p>仅可手动应用已通过安全校验的 SQL</p>
        </div>
        <div class="header-actions">
          <el-button
            text
            :disabled="generating || messages.length === 0"
            data-test="clear-ai-messages"
            @click="clearMessages"
          >
            清空对话
          </el-button>
          <el-button
            text
            circle
            aria-label="关闭 AI SQL 助手"
            data-test="close-ai-drawer"
            @click="closeDrawer"
          >
            ×
          </el-button>
        </div>
      </header>

      <main
        ref="messageList"
        class="ai-sql-messages"
        aria-live="polite"
        :aria-busy="generating"
        @scroll="recordScrollPosition"
      >
        <template v-if="messages.length">
          <article
            v-for="(message, index) in messages"
            :key="`${message.role}-${index}-${message.content}`"
            class="ai-message"
            :class="`ai-message--${message.role}`"
          >
            <span class="ai-message-role">{{ message.role === 'user' ? '你' : 'AI' }}</span>
            <p>{{ message.content }}</p>
          </article>
        </template>

        <section v-else class="ai-sql-empty" aria-label="快捷问题">
          <p>告诉我你想查询什么，我会基于当前数据源协助生成只读 SQL。</p>
          <div class="quick-prompts">
            <el-button v-for="prompt in quickPrompts" :key="prompt" plain @click="sendMessage(prompt)">
              {{ prompt }}
            </el-button>
          </div>
        </section>

        <article v-if="partialAssistant" class="ai-message ai-message--assistant ai-message--partial">
          <span class="ai-message-role">AI</span>
          <p>{{ partialAssistant }}</p>
        </article>

        <section v-if="statusMessage" class="ai-status" role="status">{{ statusMessage }}</section>
        <section v-if="errorMessage" class="ai-error" role="alert">{{ errorMessage }}</section>

        <article
          v-for="(candidate, index) in candidates"
          :key="`${candidate.sql}-${index}`"
          class="ai-sql-candidate"
          :class="candidate.valid ? 'ai-sql-candidate--valid' : 'ai-sql-candidate--invalid'"
        >
          <div class="candidate-header">
            <el-tag :type="candidate.valid ? 'success' : 'danger'" effect="dark">
              {{ candidate.valid ? '安全 SQL' : '未通过校验' }}
            </el-tag>
            <span>{{ candidate.message }}</span>
          </div>
          <pre><code>{{ candidate.sql }}</code></pre>
          <el-button
            v-if="candidate.valid"
            type="primary"
            size="small"
            data-test="apply-ai-sql"
            @click="applySql(candidate.sql)"
          >
            应用到编辑器
          </el-button>
        </article>
      </main>

      <footer class="ai-sql-composer">
        <el-input
          v-model="input"
          type="textarea"
          :rows="3"
          resize="none"
          :disabled="generating"
          placeholder="例如：按客户统计本月订单总额"
          aria-label="AI SQL 对话输入"
          data-test="ai-sql-input"
          @keydown.ctrl.enter.prevent="sendMessage()"
        />
        <div class="composer-actions">
          <el-button
            v-if="lastUserContent && !generating"
            text
            data-test="regenerate-ai-message"
            @click="regenerate"
          >
            重新生成
          </el-button>
          <el-button
            v-if="generating"
            type="danger"
            plain
            data-test="stop-ai-generation"
            @click="stopGeneration"
          >
            停止生成
          </el-button>
          <el-button
            v-else
            type="primary"
            :disabled="!input.trim()"
            data-test="send-ai-message"
            @click="sendMessage()"
          >
            发送
          </el-button>
        </div>
      </footer>
    </section>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { streamAiSql } from '@/api/aiSqlApi'
import type { AiSqlMessage, AiSqlSseEvent } from '@/types/aiSql'

interface AiSqlCandidate {
  sql: string
  valid: boolean
  message: string
}

const props = defineProps<{
  visible: boolean
  dataSourceId: string
  currentSql: string
  messages: AiSqlMessage[]
}>()

const emit = defineEmits<{
  (event: 'update:visible', value: boolean): void
  (event: 'update:messages', value: AiSqlMessage[]): void
  (event: 'apply-sql', value: string): void
}>()

const quickPrompts = ['根据描述生成 SQL', '优化当前 SQL', '解释并修正当前 SQL']
const input = ref('')
const generating = ref(false)
const partialAssistant = ref('')
const candidates = ref<AiSqlCandidate[]>([])
const statusMessage = ref('')
const errorMessage = ref('')
const messageList = ref<HTMLElement>()
const shouldAutoScroll = ref(true)
let abortController: AbortController | null = null
let activeRequestMessages: AiSqlMessage[] = []
let requestAborted = false
let receivedDone = false
let receivedError = false

const lastUserIndex = computed(() => {
  for (let index = props.messages.length - 1; index >= 0; index -= 1) {
    if (props.messages[index].role === 'user') {
      return index
    }
  }
  return -1
})

const lastUserContent = computed(() => {
  if (lastUserIndex.value < 0) {
    return ''
  }
  return props.messages[lastUserIndex.value].content
})

function closeDrawer() {
  emit('update:visible', false)
}

function handleVisibleChange(value: boolean) {
  if (value !== props.visible) {
    emit('update:visible', value)
  }
}

function clearMessages() {
  emit('update:messages', [])
  partialAssistant.value = ''
  candidates.value = []
  statusMessage.value = ''
  errorMessage.value = ''
}

function recordScrollPosition() {
  const container = messageList.value
  if (!container) return
  shouldAutoScroll.value = container.scrollHeight - container.clientHeight - container.scrollTop <= 48
}

function scrollToLatest() {
  if (!shouldAutoScroll.value) return
  void nextTick(() => {
    const container = messageList.value
    if (container) {
      container.scrollTop = container.scrollHeight
    }
  })
}

function handleEvent(event: AiSqlSseEvent) {
  const autoScroll = shouldAutoScroll.value
  switch (event.type) {
    case 'status':
      statusMessage.value = event.data.message
      break
    case 'delta':
      partialAssistant.value += event.data.content
      break
    case 'sql':
      candidates.value.push(event.data)
      break
    case 'error':
      receivedError = true
      errorMessage.value = event.data.message
      statusMessage.value = ''
      break
    case 'done':
      receivedDone = true
      statusMessage.value = ''
      break
  }
  shouldAutoScroll.value = autoScroll
  scrollToLatest()
}

async function sendMessage(content = input.value.trim(), baseMessages: AiSqlMessage[] = props.messages) {
  const normalizedContent = content.trim()
  if (!normalizedContent || generating.value) return

  const nextMessages: AiSqlMessage[] = [...baseMessages, { role: 'user', content: normalizedContent }]
  activeRequestMessages = nextMessages
  emit('update:messages', nextMessages)
  input.value = ''
  generating.value = true
  partialAssistant.value = ''
  candidates.value = []
  statusMessage.value = '正在准备请求'
  errorMessage.value = ''
  requestAborted = false
  receivedDone = false
  receivedError = false
  abortController = new AbortController()
  scrollToLatest()

  try {
    await streamAiSql({
      dataSourceId: props.dataSourceId,
      currentSql: props.currentSql,
      messages: nextMessages
    }, {
      signal: abortController.signal,
      onEvent: handleEvent
    })
    if (receivedDone && !receivedError && !requestAborted && partialAssistant.value) {
      const completedMessages: AiSqlMessage[] = [
        ...activeRequestMessages,
        { role: 'assistant', content: partialAssistant.value }
      ]
      activeRequestMessages = completedMessages
      emit('update:messages', completedMessages)
    }
  } catch (error: unknown) {
    if (!requestAborted && !isAbortError(error)) {
      errorMessage.value = '生成失败，请稍后重试'
    }
  } finally {
    generating.value = false
    abortController = null
    statusMessage.value = ''
  }
}

function regenerate() {
  if (lastUserIndex.value >= 0) {
    void sendMessage(lastUserContent.value, props.messages.slice(0, lastUserIndex.value))
  }
}

function stopGeneration() {
  if (!abortController) return
  requestAborted = true
  abortController.abort()
  statusMessage.value = '已停止生成'
}

function applySql(sql: string) {
  emit('apply-sql', sql)
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}

onBeforeUnmount(() => {
  if (abortController) {
    requestAborted = true
    abortController.abort()
  }
})
</script>

<style scoped>
.ai-sql-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  color: #dbeafe;
  background: #16213e;
}

.ai-sql-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  border-bottom: 1px solid #0f3460;
  background: #1e3a5f;
}

.ai-sql-header h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #e0f2fe;
}

.ai-sql-header p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #93c5fd;
}

.header-actions,
.composer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-sql-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px;
  scroll-behavior: smooth;
}

.ai-message {
  margin-bottom: 14px;
  padding: 12px;
  border: 1px solid #2d4a6f;
  border-radius: 6px;
  background: rgba(15, 52, 96, 0.55);
}

.ai-message--user {
  margin-left: 36px;
  border-color: rgba(59, 130, 246, 0.7);
  background: rgba(37, 99, 235, 0.2);
}

.ai-message--assistant,
.ai-sql-candidate {
  margin-right: 12px;
}

.ai-message-role {
  display: block;
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #93c5fd;
}

.ai-message p,
.ai-sql-empty p {
  margin: 0;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-sql-empty {
  padding: 12px;
  border: 1px dashed #2d4a6f;
  border-radius: 6px;
  color: #bfdbfe;
}

.quick-prompts {
  display: grid;
  gap: 8px;
  margin-top: 14px;
}

.ai-status,
.ai-error {
  margin: 10px 0;
  padding: 8px 10px;
  border-left: 3px solid #60a5fa;
  font-size: 12px;
  color: #bfdbfe;
  background: rgba(15, 52, 96, 0.45);
}

.ai-error {
  border-left-color: #f87171;
  color: #fecaca;
}

.ai-sql-candidate {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid #2d4a6f;
  border-radius: 6px;
  background: #10213d;
}

.ai-sql-candidate--valid {
  border-color: rgba(34, 197, 94, 0.7);
}

.ai-sql-candidate--invalid {
  border-color: rgba(248, 113, 113, 0.7);
}

.candidate-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 12px;
  color: #bfdbfe;
}

.ai-sql-candidate pre {
  overflow-x: auto;
  margin: 0 0 12px;
  padding: 10px;
  border-radius: 4px;
  color: #dbeafe;
  background: #0b172a;
  font: 12px/1.6 Consolas, 'Courier New', monospace;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-sql-composer {
  padding: 12px 16px;
  border-top: 1px solid #0f3460;
  background: #1e3a5f;
}

.composer-actions {
  justify-content: flex-end;
  margin-top: 8px;
}

:deep(.el-button) {
  border-color: #2d4a6f;
}

:deep(.el-textarea__inner) {
  color: #dbeafe;
  border-color: #2d4a6f;
  background: #10213d;
}

:deep(.el-textarea__inner:focus) {
  border-color: #60a5fa;
}

@media (max-width: 600px) {
  .ai-sql-header,
  .ai-sql-composer {
    padding-right: 12px;
    padding-left: 12px;
  }

  .ai-sql-messages {
    padding: 12px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ai-sql-messages {
    scroll-behavior: auto;
  }
}
</style>
