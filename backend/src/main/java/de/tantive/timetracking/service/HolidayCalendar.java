package de.tantive.timetracking.service;

import java.time.LocalDate;
import java.util.*;

/** Generates German public holidays. State rules are simplified; the backoffice reviews and corrects them each year (I-04). */
public final class HolidayCalendar {
    public record Entry(LocalDate date, String state, String nameDe, String nameEn) {}
    public static final List<String> STATES = List.of("BW", "BY", "BE", "BB", "HB", "HH", "HE", "MV", "NI", "NW", "RP", "SL", "SN", "ST", "SH", "TH");
    private HolidayCalendar() {}

    public static LocalDate easter(int y) {
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451, month = (h + l - 7 * m + 114) / 31, day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(y, month, day);
    }

    public static List<Entry> forYear(int y) {
        List<Entry> r = new ArrayList<>();
        LocalDate easter = easter(y);
        add(r, LocalDate.of(y, 1, 1), null, "Neujahr", "New Year's Day");
        add(r, easter.minusDays(2), null, "Karfreitag", "Good Friday");
        add(r, easter.plusDays(1), null, "Ostermontag", "Easter Monday");
        add(r, LocalDate.of(y, 5, 1), null, "Tag der Arbeit", "Labour Day");
        add(r, easter.plusDays(39), null, "Christi Himmelfahrt", "Ascension Day");
        add(r, easter.plusDays(50), null, "Pfingstmontag", "Whit Monday");
        add(r, LocalDate.of(y, 10, 3), null, "Tag der Deutschen Einheit", "German Unity Day");
        add(r, LocalDate.of(y, 12, 25), null, "1. Weihnachtstag", "Christmas Day");
        add(r, LocalDate.of(y, 12, 26), null, "2. Weihnachtstag", "Boxing Day");
        for (String s : List.of("BW", "BY", "ST")) add(r, LocalDate.of(y, 1, 6), s, "Heilige Drei Könige", "Epiphany");
        add(r, LocalDate.of(y, 3, 8), "BE", "Internationaler Frauentag", "International Women's Day");
        add(r, LocalDate.of(y, 3, 8), "MV", "Internationaler Frauentag", "International Women's Day");
        for (String s : List.of("BW", "BY", "HE", "NW", "RP", "SL"))
            add(r, easter.plusDays(60), s, "Fronleichnam", "Corpus Christi");
        for (String s : List.of("BB", "HB", "HH", "MV", "NI", "SN", "ST", "SH", "TH"))
            add(r, LocalDate.of(y, 10, 31), s, "Reformationstag", "Reformation Day");
        for (String s : List.of("BW", "BY", "NW", "RP", "SL"))
            add(r, LocalDate.of(y, 11, 1), s, "Allerheiligen", "All Saints' Day");
        add(r, LocalDate.of(y, 11, 18).with(java.time.temporal.TemporalAdjusters.previous(java.time.DayOfWeek.WEDNESDAY)), "SN",
                "Buß- und Bettag", "Repentance Day");
        return r;
    }

    private static void add(List<Entry> r, LocalDate d, String state, String de, String en) { r.add(new Entry(d, state, de, en)); }
}
