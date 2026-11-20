package de.tantive.timetracking.config;

import java.time.*;

/** All wall-clock handling happens in the company time zone; instants are stored in UTC. */
public final class Tz {
    public static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private Tz() {}
    public static Instant toInstant(LocalDateTime t) { return t.atZone(ZONE).toInstant(); }
    public static LocalDateTime local(Instant i) { return i == null ? null : LocalDateTime.ofInstant(i, ZONE); }
    public static LocalDate dateOf(Instant i) { return LocalDate.ofInstant(i, ZONE); }
    public static LocalDate today() { return LocalDate.now(ZONE); }
}
