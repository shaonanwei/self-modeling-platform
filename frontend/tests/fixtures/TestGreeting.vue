<template>
  <p class="greeting">{{ message }}</p>
</template>

<script setup lang="ts">
defineProps<{ message: string }>()
</script>
