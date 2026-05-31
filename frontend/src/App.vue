<template>
  <router-view />
</template>

<script setup lang="ts">
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

/* 全屏弹窗样式 */
.step-edit-fullscreen-wrapper {
  width: 100vw !important;
  height: 100vh !important;
  min-height: 100vh !important;
  margin: 0 !important;
  padding: 0 !important;
  max-width: 100vw !important;
  max-height: 100vh !important;
  position: fixed !important;
  top: 0 !important;
  left: 0 !important;
  bottom: 0 !important;
  right: 0 !important;
}

.step-edit-fullscreen-dialog {
  width: 100vw !important;
  height: 100vh !important;
  min-height: 100vh !important;
  margin: 0 !important;
  padding: 0 !important;
  max-width: 100vw !important;
  max-height: 100vh !important;
  top: 0 !important;
  left: 0 !important;
  bottom: 0 !important;
  right: 0 !important;
}
</style>
