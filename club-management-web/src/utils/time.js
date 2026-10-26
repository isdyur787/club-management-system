/**
 * 全局时间格式化工具
 * 将 ISO / yyyy-MM-dd HH:mm:ss 格式统一为 yyyy-MM-dd HH:mm
 */

/**
 * 格式化时间字符串
 * @param {string|null} value - 时间字符串（如 "2024-06-01 14:30:00"）
 * @returns {string} 格式化后的时间（如 "2024-06-01 14:30"），null 返回 "-"
 */
export function formatTime(value) {
  if (!value) return '-'
  const str = String(value)
  // 匹配 yyyy-MM-dd HH:mm:ss 或 yyyy-MM-ddTHH:mm:ss
  const match = str.match(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2})/)
  if (match) {
    return `${match[1]} ${match[2]}`
  }
  return str
}
