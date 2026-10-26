<template>
  <div class="stat-card">
    <div :class="['stat-icon', color]">
      <el-icon><slot name="icon" /></el-icon>
    </div>
    <div class="stat-info">
      <div class="stat-value">{{ value }}</div>
      <div class="stat-label">{{ label }}</div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  value: {
    type: [String, Number],
    default: 0
  },
  label: {
    type: String,
    default: ''
  },
  color: {
    type: String,
    default: 'blue',
    validator: (v) => ['blue', 'green', 'orange', 'red', 'purple', 'cyan'].includes(v)
  }
})
</script>

<style scoped>
.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}
.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: #fff;
  flex-shrink: 0;
}
.stat-icon.blue   { background: linear-gradient(135deg, var(--primary-color), #66b1ff); }
.stat-icon.green  { background: linear-gradient(135deg, #67c23a, #85ce61); }
.stat-icon.orange { background: linear-gradient(135deg, #e6a23c, #ebb563); }
.stat-icon.red    { background: linear-gradient(135deg, #f56c6c, #f89898); }
.stat-icon.purple { background: linear-gradient(135deg, #9b59b6, #b07cc6); }
.stat-icon.cyan   { background: linear-gradient(135deg, #00b8d4, #48c9b0); }
.stat-info {
  display: flex;
  flex-direction: column;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}
.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}
</style>
