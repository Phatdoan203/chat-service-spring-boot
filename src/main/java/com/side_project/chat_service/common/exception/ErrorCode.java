package com.side_project.chat_service.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    /** Co y tra 404 nhu CONVERSATION_NOT_FOUND: nguoi ngoai khong duoc biet hoi thoai co ton tai. */
    NOT_A_MEMBER(HttpStatus.NOT_FOUND, "Hoi thoai khong ton tai"),
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Hoi thoai khong ton tai"),
    FORBIDDEN_ROLE(HttpStatus.FORBIDDEN, "Khong du quyen trong hoi thoai nay"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Nguoi dung khong ton tai"),
    CONFLICT(HttpStatus.CONFLICT, "Xung dot nghiep vu"),
    INVALID_REQUEST(HttpStatus.UNPROCESSABLE_ENTITY, "Du lieu gui len khong hop le");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
