package com.club.management.common.exception;

public enum ErrorCode {

    // ========== 通用错误 ==========
    PARAM_INVALID(40001, "参数校验失败"),
    UNAUTHORIZED(40002, "未登录或登录已过期"),
    ACCOUNT_LOCKED(40006, "账号已锁定，请15分钟后重试"),
    FORBIDDEN(40003, "无权限执行此操作"),
    RESOURCE_NOT_FOUND(40004, "资源不存在"),
    IDEMPOTENT_CONFLICT(40005, "请勿重复提交"),
    RATE_LIMIT_EXCEEDED(42900, "请求过于频繁，请稍后重试"),

    // ========== 用户模块 ==========
    USER_NOT_FOUND(40101, "用户不存在"),
    USER_ALREADY_EXISTS(40102, "用户已存在"),
    PASSWORD_INCORRECT(40103, "用户名或密码错误"),
    ROLE_NOT_FOUND(40104, "角色不存在"),

    // ========== 社团模块 ==========
    CLUB_NOT_FOUND(40201, "社团不存在"),
    CLUB_ALREADY_EXISTS(40202, "社团名称已存在"),
    CLUB_NOT_APPROVED(40203, "社团尚未通过审批"),
    MEMBER_ALREADY_EXISTS(40204, "该用户已是社团成员"),
    MEMBER_NOT_FOUND(40205, "成员不存在"),
    CLUB_PRESIDENT_CANNOT_LEAVE(40206, "社长不能直接退出社团"),

    // ========== 活动模块 ==========
    ACTIVITY_NOT_FOUND(40301, "活动不存在"),
    ACTIVITY_STATUS_INVALID(40302, "活动状态不允许此操作"),
    ACTIVITY_APPROVAL_REJECTED(40303, "活动审批被驳回"),
    ACTIVITY_ALREADY_APPROVED(40304, "活动已审批，不可重复操作"),
    ACTIVITY_BUDGET_EXCEEDED(40305, "预算超出限制"),
    ACTIVITY_TIME_CONFLICT(40306, "活动时间与已有活动冲突"),
    ACTIVITY_NOT_COMPLETED(40359, "活动尚未结束，不可生成报告"),
    CHECKIN_DATA_EMPTY(40360, "打卡数据为空，无法生成报告"),
    REPORT_ALREADY_EXISTS(40361, "报告已存在"),

    // ========== 报名签到模块 ==========
    REGISTRATION_FULL(40401, "活动报名人数已满"),
    REGISTRATION_DUPLICATE(40402, "请勿重复报名"),
    REGISTRATION_CLOSED(40403, "报名已截止"),
    REGISTRATION_NOT_FOUND(40404, "报名记录不存在"),
    SIGN_IN_DUPLICATE(40405, "请勿重复签到"),
    SIGN_IN_CODE_INVALID(40406, "签到码无效"),
    SIGN_IN_TIME_EXPIRED(40407, "签到时间已过"),

    // ========== 资源模块 ==========
    RESOURCE_NOT_AVAILABLE(40501, "资源不可用"),
    VENUE_TIME_CONFLICT(40502, "场地在该时段已被预约"),
    VENUE_NOT_FOUND(40503, "场地不存在"),
    EQUIPMENT_NOT_AVAILABLE(40504, "设备已被借出"),

    // ========== 通知模块 ==========
    NOTIFICATION_NOT_FOUND(40601, "通知不存在"),

    // ========== 打卡模块 ==========
    CHECKIN_TASK_EXISTS(40350, "打卡任务已存在"),
    CHECKIN_DEADLINE_PASSED(40351, "打卡已截止"),
    CHECKIN_ALREADY_SUBMITTED(40352, "已提交过打卡"),
    CHECKIN_TASK_NOT_FOUND(40363, "打卡任务不存在"),
    CHECKIN_NOT_SETTLED(40355, "打卡尚未截止，不可结算"),
    CHECKIN_ALREADY_SETTLED(40356, "已结算过"),
    SIGNOUT_NOT_CHECKED_IN(40357, "未签到，无法签退"),
    SIGNOUT_ALREADY_DONE(40358, "已签退过"),
    COMPILATION_NOT_DEADLINE(40364, "打卡尚未截止，不能生成汇编"),
    COMPILATION_ALREADY_EXISTS(40362, "汇编文档已存在"),

    // ========== AI 模块 ==========
    AI_SERVICE_UNAVAILABLE(40700, "AI 服务不可用"),
    AI_LLM_FAILED(40701, "LLM 生成失败"),
    AI_NO_RELEVANT_CONTENT(40702, "知识库中无相关内容"),
    AI_REPORT_NOT_FOUND(40703, "报告不存在"),
    AI_REPORT_ALREADY_CONFIRMED(40704, "报告已确认"),
    FILE_FORMAT_NOT_SUPPORTED(40705, "文件格式不支持"),
    FILE_SIZE_EXCEEDED(40706, "文件大小超限"),

    // ========== 系统内部错误 ==========
    SYSTEM_ERROR(50001, "系统内部错误"),
    DATABASE_ERROR(50002, "数据库操作失败"),
    REDIS_ERROR(50003, "缓存操作失败");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() { return code; }
    public String getMessage() { return message; }
}
