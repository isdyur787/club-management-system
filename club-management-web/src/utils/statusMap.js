import {
  ActivityStatusTypeMap,
  ActivityStatusTextMap,
  ActivityTypeTextMap
} from '@/constants/enums'

/**
 * 获取活动状态对应的 Element Plus Tag 类型
 * @param {string} status 活动状态枚举值
 * @returns {string} Element Plus tag type
 */
export function getStatusType(status) {
  return ActivityStatusTypeMap[status] ?? 'info'
}

/**
 * 获取活动状态的中文文本
 * @param {string} status 活动状态枚举值
 * @returns {string} 中文文本
 */
export function getStatusText(status) {
  return ActivityStatusTextMap[status] ?? status
}

/**
 * 获取活动类型的中文文本
 * @param {string} type 活动类型枚举值
 * @returns {string} 中文文本
 */
export function getTypeText(type) {
  return ActivityTypeTextMap[type] ?? type
}
