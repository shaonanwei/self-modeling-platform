<template>
  <div class="insert-indicator" @click.stop="handleClick">
    <el-divider style="margin: 8px 0" />
    <div class="insert-btn">
      <el-icon><Plus /></el-icon>
    </div>
  </div>
</template>

<script setup lang="ts">
const emit = defineEmits<{
  (e: 'click'): void
}>()

const handleClick = () => {
  emit('click')
}
</script>

<style scoped>
.insert-indicator {
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s;
  padding: 4px 0;
}

.insert-indicator:hover {
  opacity: 1;
}

.insert-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #409EFF;
  color: white;
  font-size: 16px;
  margin: 4px 0;
  transition: transform 0.2s;
}

.insert-btn:hover {
  transform: scale(1.1);
}
</style>
