package de.tantive.timetracking;

import de.tantive.timetracking.config.Tz;
import de.tantive.timetracking.domain.*;
import de.tantive.timetracking.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CalcTest extends TestBase {
    @Autowired Calc calc;
    @Autowired WorkTimeService work;
    @Autowired AbsenceService absences;

    @BeforeEach void setUp() { seed(); }

    private void day(AppUser u, String date, String from, String to, String... breaks) {
        LocalDate d = LocalDate.parse(date);
        List<WorkTimeService.BreakIn> bs = new ArrayList<>();
        for (String b : breaks) { String[] p = b.split("-"); bs.add(new WorkTimeService.BreakIn(d.atTime(LocalTime.parse(p[0])), d.atTime(LocalTime.parse(p[1])))); }
        work.save(admin, u, d, d.atTime(LocalTime.parse(from)), d.atTime(LocalTime.parse(to)), bs);
    }

    private DayResult result(AppUser u, String date) { return calc.days(u, LocalDate.parse(date), LocalDate.parse(date)).get(0); }

    @Test void targetTimeFollowsContractChangeAndWorkingDays() {
        schedule(alice, LocalDate.of(2026, 3, 1), 20, "TUESDAY,THURSDAY", "BE");
        assertEquals(480, result(alice, "2026-02-06").targetMinutes());   // Friday, 40h / 5 days
        assertEquals(0, result(alice, "2026-02-07").targetMinutes());     // Saturday
        assertEquals(600, result(alice, "2026-03-03").targetMinutes());   // Tuesday after contract change: 20h / 2 days
        assertEquals(0, result(alice, "2026-03-04").targetMinutes());     // Wednesday no longer a working day
    }

    @Test void holidaysDependOnFederalState() {
        Holiday h = new Holiday(); h.date = LocalDate.of(2026, 1, 6); h.federalState = "BY"; h.nameDe = "Heilige Drei Könige"; h.nameEn = "Epiphany"; holidays.save(h);
        Holiday n = new Holiday(); n.date = LocalDate.of(2026, 5, 1); n.nameDe = "Tag der Arbeit"; n.nameEn = "Labour Day"; holidays.save(n);
        assertEquals(480, result(alice, "2026-01-06").targetMinutes(), "BE has no holiday on 6 Jan");
        schedule(bob, LocalDate.of(2026, 1, 1), 40, "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY", "BY");
        assertEquals(0, result(bob, "2026-01-06").targetMinutes());   // Bob's latest schedule on that day says BY
        assertEquals(0, result(alice, "2026-05-01").targetMinutes()); // nationwide
    }

    @Test void balanceCountsOpeningBalanceAbsencesAndHalfDays() {
        alice.openingBalanceMinutes = 120; alice.openingBalanceDate = LocalDate.of(2026, 2, 2); users.save(alice);
        day(alice, "2026-02-02", "08:00", "17:00", "12:00-13:00");            // 8h net = target
        day(alice, "2026-02-03", "08:00", "19:00", "12:00-13:00");            // 10h net = +2h
        absences.set(alice, alice, LocalDate.of(2026, 2, 4), Absence.Type.VACATION, false); // target 0
        absences.set(alice, alice, LocalDate.of(2026, 2, 5), Absence.Type.SICK, true);      // half day: target 4h
        day(alice, "2026-02-05", "08:00", "12:30");                           // 4.5h net => +0.5h
        // 120 opening + 0 + 120 + 0 + 30
        assertEquals(270, calc.balance(alice, LocalDate.of(2026, 2, 5)));
        // Friday 6 Feb without any recording: -8h
        assertEquals(270 - 480, calc.balance(alice, LocalDate.of(2026, 2, 6)));
    }

    @Test void balanceOfPastPeriodIsReproducibleAfterMasterDataChanges() {
        alice.openingBalanceDate = LocalDate.of(2026, 2, 2); users.save(alice);
        day(alice, "2026-02-02", "08:00", "17:00", "12:00-13:00");
        long before = calc.balance(alice, LocalDate.of(2026, 2, 6));
        schedule(alice, LocalDate.of(2026, 3, 1), 20, "TUESDAY", "BY"); // later contract change
        Holiday h = new Holiday(); h.date = LocalDate.of(2026, 6, 1); h.nameDe = "x"; h.nameEn = "x"; holidays.save(h);
        assertEquals(before, calc.balance(alice, LocalDate.of(2026, 2, 6)));
    }

    @Test void workPastMidnightBelongsToStartDay() {
        LocalDate d = LocalDate.of(2026, 2, 11);
        work.save(admin, alice, d, d.atTime(22, 0), d.plusDays(1).atTime(2, 0), List.of());
        assertEquals(240, result(alice, "2026-02-11").workMinutes());
        assertEquals(0, result(alice, "2026-02-12").workMinutes());
    }

    @Test void daylightSavingChangeUsesRealElapsedTime() {
        LocalDate d = LocalDate.of(2026, 3, 28); // clocks go forward at night to 29 March: 22:00 -> 06:00 is only 7 real hours
        work.save(admin, alice, d, d.atTime(22, 0), d.plusDays(1).atTime(6, 0), List.of());
        assertEquals(7 * 60, result(alice, "2026-03-28").workMinutes());
        LocalDate o = LocalDate.of(2026, 10, 24); // clocks go back: 9 real hours
        work.save(admin, bob, o, o.atTime(22, 0), o.plusDays(1).atTime(6, 0), List.of());
        assertEquals(9 * 60, result(bob, "2026-10-24").workMinutes());
    }

    @Test void ruleChecksWarnButDoNotBlock() {
        day(alice, "2026-02-09", "07:00", "23:00", "12:00-12:20"); // 15h40m net, short break
        DayResult r = result(alice, "2026-02-09");
        List<String> keys = r.warnings().stream().map(Msg::key).toList();
        assertTrue(keys.contains("RULE_MAX_DAILY"));
        assertTrue(keys.contains("RULE_BREAK_45"));
        day(alice, "2026-02-10", "07:00", "12:00");                // only 8h rest after 23:00
        assertTrue(result(alice, "2026-02-10").warnings().stream().anyMatch(m -> m.key().equals("RULE_REST")));
        day(bob, "2026-02-09", "08:00", "15:00", "12:00-12:20");   // 6h40m net with 20 min break => needs 30
        assertTrue(result(bob, "2026-02-09").warnings().stream().anyMatch(m -> m.key().equals("RULE_BREAK_30")));
        day(carol, "2026-02-09", "08:00", "16:30", "12:00-12:30"); // 8h net, 30 min => fine
        assertTrue(result(carol, "2026-02-09").warnings().isEmpty());
    }

    @Test void gapHints() {
        assertTrue(result(alice, "2026-02-09").hints().stream().anyMatch(m -> m.key().equals("HINT_NO_ENTRY")));
        assertTrue(result(alice, "2026-02-07").hints().isEmpty(), "weekend needs no entry");
        day(alice, "2026-02-10", "08:00", "13:00");
        DayResult r = result(alice, "2026-02-10");
        assertTrue(r.hints().stream().anyMatch(m -> m.key().equals("HINT_BELOW_TARGET")));
        assertTrue(r.hints().stream().anyMatch(m -> m.key().equals("HINT_PROJECT_MISMATCH")));
    }

    @Test void invalidTimesAreRejected() {
        LocalDate d = LocalDate.of(2026, 2, 9);
        assertThrows(de.tantive.timetracking.config.ApiException.class,
                () -> work.save(admin, alice, d, d.atTime(9, 0), d.atTime(8, 0), List.of()));
        assertThrows(de.tantive.timetracking.config.ApiException.class,
                () -> work.save(admin, alice, d, d.atTime(8, 0), d.atTime(17, 0), List.of(new WorkTimeService.BreakIn(d.atTime(7, 0), d.atTime(7, 30)))));
    }
}
