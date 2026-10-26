package com.club.management.common.exception;

public class AuthException extends BusinessException {
    public AuthException(ErrorCode errorCode) { super(errorCode); }
    public AuthException(ErrorCode errorCode, String detail) { super(errorCode, detail); }
}
