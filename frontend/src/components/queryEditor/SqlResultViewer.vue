<template>
  <div class="sql-result-viewer">
    <div v-if="result && result.success" class="result-content">
      <div class="result-header">
        <span>共 {{ result.total }} 行</span>
        <span>| 列数: {{ result.columns.length }}</span>
      </div>
      <div class="result-table-wrapper">
        <table class="result-table">
          <thead>
            <tr>
              <th v-for="col in result.columns" :key="col">{{ col }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, idx) in result.rows" :key="idx">
              <td v-for="col in result.columns" :key="col">{{ formatValue(row[col]) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
    <div v-else class="error-state">
      <p>{{ result?.message || '无数据' }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { SqlExecuteResult } from '@/types/queryEditor'

defineProps<{
  result: SqlExecuteResult | null
}>()

function formatValue(val: any): string {
  if (val === null || val === undefined) return 'NULL'
  if (typeof val === 'object') return JSON.stringify(val)
  return String(val)
}
</script>

<style scoped>
.sql-result-viewer { height: 100%; overflow: auto; }
.result-header { padding: 8px 12px; background: #16213e; font-size: 12px; color: #94a3b8; }
.result-table-wrapper { overflow: auto; max-height: 500px; }
.result-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.result-table th {
  position: sticky;
  top: 0;
  background: #0f3460;
  color: #60a5fa;
  padding: 6px 10px;
  text-align: left;
  font-weight: 600;
  z-index: 1;
}
.result-table td {
  padding: 5px 10px;
  border-bottom: 1px solid #1a2744;
  color: #cbd5e1;
  word-break: break-all;
}
.result-table tr:hover td { background: rgba(59,130,246,0.06); }
.error-state { text-align: center; padding: 40px; color: #f87171; }
</style>
