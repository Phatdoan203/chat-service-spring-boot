package com.side_project.chat_service.common.util;

public final class DmKeys {

    private DmKeys() {
    }

    /** least:greatest, nen ai mo truoc cung ra cung mot khoa. */
    public static String of(String userA, String userB) {
        return userA.compareTo(userB) <= 0 ? userA + ":" + userB : userB + ":" + userA;
    }
}
