package de.tantive.timetracking.service;

import de.tantive.timetracking.config.Tz;
import de.tantive.timetracking.domain.*;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Target time, actual time, rule checks and balances (D-07, H-06, B-13, B-14).
 * Everything is a pure function of the stored data (validity-dated schedules, holidays, absences,
 * recorded times), so balances of past periods can always be recalculated with the same result.
 */
@Service
public class Calc {
    public static final int MAX_DAILY_MINUTES = 10 * 60;
    public static final int REST_MINUTES = 11 * 60;

    private final Repos.Schedules schedules;
    private final Repos.Holidays holidays;
    private final Repos.Absences absences;
    private final Repos.WorkDays workDays;
    private final Repos.Entries entries;

    public Calc(Repos.Schedules s, Repos.Holidays h, Repos.Absences a, Repos.WorkDays w, Repos.Entries e) {
        this.schedules = s; this.holidays = h; this.absences = a; this.workDays = w; this.entries = e;
    }

    /** Schedule valid on the given date, or null. */
    public static TargetSchedule scheduleOn(List<TargetSchedule> sorted, LocalDate d) {
        TargetSchedule r = null;
        for (TargetSchedule s : sorted) if (!s.validFrom.isAfter(d)) r = s; else break;
        return r;
    }

    /** Base target for a day: schedule, working days, user's entry/exit dates and public holidays (before absences). */
    public static int baseTarget(AppUser u, List<TargetSchedule> sorted, Map<String, Holiday> holidayByKey, LocalDate d) {
        if (u.entryDate != null && d.isBefore(u.entryDate)) return 0;
        if (u.exitDate != null && d.isAfter(u.exitDate)) return 0;
        TargetSchedule s = scheduleOn(sorted, d);
        if (s == null || s.days().isEmpty() || !s.days().contains(d.getDayOfWeek())) return 0;
        if (holidayFor(holidayByKey, s.federalState, d) != null) return 0;
        return Math.round((float) s.weeklyMinutes / s.days().size());
    }

    static Holiday holidayFor(Map<String, Holiday> byKey, String state, LocalDate d) {
        Holiday h = byKey.get(d + "|" + state);
        return h != null ? h : byKey.get(d + "|");
    }

    public static int applyAbsence(int base, Absence a) {
        if (a == null) return base;
        return a.halfDay ? base - base / 2 : 0;
    }

    /** Detailed results for each day of [from, to]. */
    public List<DayResult> days(AppUser u, LocalDate from, LocalDate to) {
        List<TargetSchedule> sched = schedules.findByUserIdOrderByValidFromAsc(u.id);
        Map<String, Holiday> hol = new HashMap<>();
        for (Holiday h : holidays.findByDateBetween(from, to))
            hol.put(h.date + "|" + (h.federalState == null ? "" : h.federalState), h);
        Map<LocalDate, Absence> abs = absences.findByUserIdAndAbsenceDateBetween(u.id, from, to).stream()
                .collect(Collectors.toMap(a -> a.absenceDate, a -> a));
        Map<LocalDate, WorkDay> wd = workDays.findByUserIdAndWorkDateBetweenOrderByWorkDate(u.id, from.minusDays(7), to)
                .stream().collect(Collectors.toMap(w -> w.workDate, w -> w, (a, b) -> a, TreeMap::new));
        Map<LocalDate, Long> proj = new HashMap<>();
        for (ProjectEntry e : entries.findByUserIdAndEntryDateBetweenOrderByEntryDateAscStartAtAscIdAsc(u.id, from, to))
            proj.merge(e.entryDate, (long) e.minutes + (e.running() ? runningMinutes(e) : 0), Long::sum);
        TreeMap<LocalDate, WorkDay> sortedWd = new TreeMap<>(wd);
        Instant now = Instant.now();
        LocalDate today = Tz.today();
        List<DayResult> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            int base = baseTarget(u, sched, hol, d);
            Absence a = abs.get(d);
            int target = applyAbsence(base, a);
            WorkDay w = sortedWd.get(d);
            long work = w == null ? 0 : w.netMinutes(now);
            long brk = w == null ? 0 : w.breakMinutes(now);
            long pm = proj.getOrDefault(d, 0L);
            TargetSchedule s = scheduleOn(sched, d);
            Holiday h = s == null ? null : holidayFor(hol, s.federalState, d);
            List<Msg> warnings = new ArrayList<>();
            List<Msg> hints = new ArrayList<>();
            if (w != null) {
                if (work > MAX_DAILY_MINUTES) warnings.add(Msg.of("RULE_MAX_DAILY", "hours", String.format(Locale.ROOT, "%.1f", work / 60.0)));
                if (work > 9 * 60 && brk < 45) warnings.add(Msg.of("RULE_BREAK_45", "break", brk));
                else if (work > 6 * 60 && brk < 30) warnings.add(Msg.of("RULE_BREAK_30", "break", brk));
                Map.Entry<LocalDate, WorkDay> prev = sortedWd.lowerEntry(d);
                if (prev != null && prev.getValue().endAt != null) {
                    long rest = Duration.between(prev.getValue().endAt, w.startAt).toMinutes();
                    if (rest < REST_MINUTES) warnings.add(Msg.of("RULE_REST", "hours", String.format(Locale.ROOT, "%.1f", rest / 60.0)));
                }
            }
            boolean past = d.isBefore(today);
            boolean finished = w != null && w.endAt != null;
            if (past && target > 0 && w == null && pm == 0) hints.add(Msg.of("HINT_NO_ENTRY"));
            if (past && target > 0 && w != null && work < target) hints.add(Msg.of("HINT_BELOW_TARGET", "missing", target - work));
            if ((past || finished) && w != null && Math.abs(pm - work) >= 1) hints.add(Msg.of("HINT_PROJECT_MISMATCH", "work", work, "project", pm));
            else if (past && w == null && pm > 0) hints.add(Msg.of("HINT_PROJECT_MISMATCH", "work", 0, "project", pm));
            out.add(new DayResult(d, target, base, work, brk, pm, h == null ? null : h.nameDe, h == null ? null : h.nameEn,
                    a == null ? null : a.type, a != null && a.halfDay, w == null ? null : w.startAt, w == null ? null : w.endAt,
                    w != null && w.endAt == null, w != null, warnings, hints));
        }
        return out;
    }

    public static long runningMinutes(ProjectEntry e) {
        return Math.max(0, Duration.between(e.startAt, Instant.now()).toMinutes());
    }

    /** First day that counts towards the user's balance. */
    public LocalDate balanceStart(AppUser u) {
        if (u.openingBalanceDate != null) return u.openingBalanceDate;
        if (u.entryDate != null) return u.entryDate;
        List<TargetSchedule> s = schedules.findByUserIdOrderByValidFromAsc(u.id);
        return s.isEmpty() ? null : s.get(0).validFrom;
    }

    /** Overtime balance in minutes at the end of {@code upTo}: opening balance + sum(actual - target). */
    public long balance(AppUser u, LocalDate upTo) {
        LocalDate start = balanceStart(u);
        if (start == null) return u.openingBalanceMinutes;
        LocalDate today = Tz.today();
        LocalDate end = upTo;
        if (!end.isBefore(today)) {
            // today only counts once the working day has been ended; the future never counts
            WorkDay w = workDays.findByUserIdAndWorkDate(u.id, today).orElse(null);
            end = (w != null && w.endAt != null) ? today : today.minusDays(1);
            if (upTo.isBefore(end)) end = upTo;
        }
        if (end.isBefore(start)) return u.openingBalanceMinutes;
        long sum = u.openingBalanceMinutes;
        for (DayResult r : days(u, start, end)) sum += r.workMinutes() - r.targetMinutes();
        return sum;
    }
}
