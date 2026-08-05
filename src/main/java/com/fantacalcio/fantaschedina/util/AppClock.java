package com.fantacalcio.fantaschedina.util;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * All admin-entered times (matchday startAt, deadlines) are civil Italian time.
 * The JVM's default zone depends on the deployment (e.g. UTC in prod containers),
 * so any comparison against "now" must use this fixed zone instead of
 * LocalDateTime.now()/ZoneId.systemDefault() to avoid a silent offset.
 */
public final class AppClock {

    public static final ZoneId ZONE = ZoneId.of("Europe/Rome");

    private AppClock() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
