package com.side_project.chat_service.common.util;

import java.util.UUID;

public final class DmKeys {

    private DmKeys() {
    }

    /** least:greatest theo thu tu chuoi UUID, nen ai mo truoc cung ra cung mot khoa. */
    public static String of(UUID userA, UUID userB) {
        String a = userA.toString();
        String b = userB.toString();
        return a.compareTo(b) <= 0 ? a + ":" + b : b + ":" + a;
    }
}
