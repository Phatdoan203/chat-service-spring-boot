package com.side_project.chat_service.common.util;

public final class LogEvent {

    private LogEvent() {
    }

    /** Dung "event=x k1=v1 k2=v2"; gia tri null in "-". */
    public static String of(String event, Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues phai theo cap key,value");
        }
        var sb = new StringBuilder("event=").append(event);
        for (int i = 0; i < keyValues.length; i += 2) {
            Object v = keyValues[i + 1];
            sb.append(' ').append(keyValues[i]).append('=').append(v == null ? "-" : v);
        }
        return sb.toString();
    }
}
