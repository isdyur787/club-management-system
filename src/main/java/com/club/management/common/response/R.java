package com.club.management.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.club.management.common.exception.ErrorCode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> {

    private Integer code;
    private String message;
    private T data;
    private Long timestamp;

    public static <T> R<T> ok() {
        R<T> r = new R<>();
        r.setCode(200);
        r.setMessage("操作成功");
        r.setTimestamp(System.currentTimeMillis());
        return r;
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.setCode(200);
        r.setMessage("操作成功");
        r.setData(data);
        r.setTimestamp(System.currentTimeMillis());
        return r;
    }

    public static <T> R<T> fail(Integer code, String message) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMessage(message);
        r.setTimestamp(System.currentTimeMillis());
        return r;
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        R<T> r = new R<>();
        r.setCode(errorCode.getCode());
        r.setMessage(errorCode.getMessage());
        r.setTimestamp(System.currentTimeMillis());
        return r;
    }

    public static <T> R<T> fail(ErrorCode errorCode, String detail) {
        R<T> r = new R<>();
        r.setCode(errorCode.getCode());
        r.setMessage(errorCode.getMessage() + ": " + detail);
        r.setTimestamp(System.currentTimeMillis());
        return r;
    }

    public Integer getCode() { return code; }
    public void setCode(Integer code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}
