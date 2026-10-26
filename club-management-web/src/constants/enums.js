/**
 * 活动状态枚举
 */
export const ActivityStatus = {
  DRAFT: 'DRAFT',
  PENDING: 'PENDING',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
  IN_PROGRESS: 'IN_PROGRESS',
  COMPLETED: 'COMPLETED',
  ARCHIVED: 'ARCHIVED'
}

/** 活动状态 → Element Plus Tag 类型 */
export const ActivityStatusTypeMap = {
  DRAFT: 'info',
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  IN_PROGRESS: 'primary',
  COMPLETED: 'success',
  ARCHIVED: 'info'
}

/** 活动状态 → 中文文本 */
export const ActivityStatusTextMap = {
  DRAFT: '草稿',
  PENDING: '待审批',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  ARCHIVED: '已归档'
}

/**
 * 活动类型枚举
 */
export const ActivityType = {
  CAMPUS: 'CAMPUS',
  OFF_CAMPUS: 'OFF_CAMPUS',
  LARGE: 'LARGE'
}

/** 活动类型 → 中文文本 */
export const ActivityTypeTextMap = {
  CAMPUS: '校内',
  OFF_CAMPUS: '校外',
  LARGE: '大型'
}

/**
 * 角色枚举
 */
export const Role = {
  STUDENT: 'STUDENT',
  CLUB_PRESIDENT: 'CLUB_PRESIDENT',
  TEACHER: 'TEACHER',
  ADMIN: 'ADMIN'
}
