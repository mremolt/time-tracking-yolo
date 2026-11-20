package de.tantive.timetracking.service;

import de.tantive.timetracking.config.*;
import de.tantive.timetracking.domain.*;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import java.time.*;
import java.time.format.TextStyle;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * All reports share one basis: filters + grouping, with the D-10 visibility rules applied centrally
 * to every query (F-04, H-05). Each report returns a localised {@link Table} that can be exported.
 */
@Service
public class ReportService {
    public enum Group { PROJECT, USER, TEAM, ACTIVITY, WEEK, MONTH, DAY }
    public record Filter(LocalDate from, LocalDate to, Long teamId, Long projectId, Long userId, Long activityId) {}
    record Row(ProjectEntry e, AppUser user, Project project, Activity activity) {}

    private final Repos.Users users;
    private final Repos.Projects projects;
    private final Repos.Activities activities;
    private final Repos.Entries entries;
    private final Repos.Absences absences;
    private final Repos.WorkDays workDays;
    private final Calc calc;
    private final BudgetService budget;
    private final Access access;
    private final MessageSource msg;

    public ReportService(Repos.Users u, Repos.Projects p, Repos.Activities a, Repos.Entries e, Repos.Absences ab, Repos.WorkDays w,
                         Calc calc, BudgetService budget, Access access, MessageSource msg) {
        users = u; projects = p; activities = a; entries = e; absences = ab; workDays = w;
        this.calc = calc; this.budget = budget; this.access = access; this.msg = msg;
    }

    // ---- helpers ----
    static Locale loc(String lang) { return "en".equalsIgnoreCase(lang) ? Locale.ENGLISH : Locale.GERMAN; }
    String t(String key, Locale l) { return msg.getMessage(key, null, key, l); }
    static String h(long minutes, Locale l) { return String.format(l, "%.2f", minutes / 60.0); }
    static String hd(double days, Locale l) { return String.format(l, "%.1f", days); }

    private void checkRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) throw ApiException.bad("DATE_RANGE_INVALID");
        if (from.plusYears(2).isBefore(to)) throw ApiException.bad("DATE_RANGE_TOO_LARGE");
    }

    // ---- project time (F-02, F-04, F-12) ----
    private List<Row> rows(AppUser viewer, Filter f, boolean personLevel) {
        checkRange(f.from(), f.to());
        Set<Long> led = access.ledProjectIds(viewer);
        Map<Long, AppUser> um = users.findAll().stream().collect(Collectors.toMap(x -> x.id, x -> x));
        Map<Long, Project> pm = projects.findAll().stream().collect(Collectors.toMap(x -> x.id, x -> x));
        Map<Long, Activity> am = activities.findAll().stream().collect(Collectors.toMap(x -> x.id, x -> x));
        List<Row> out = new ArrayList<>();
        for (ProjectEntry e : entries.findByEntryDateBetween(f.from(), f.to())) {
            if (f.projectId() != null && !f.projectId().equals(e.projectId)) continue;
            if (f.userId() != null && !f.userId().equals(e.userId)) continue;
            if (f.activityId() != null && !f.activityId().equals(e.activityId)) continue;
            AppUser u = um.get(e.userId);
            if (f.teamId() != null && (u.team == null || !u.team.id.equals(f.teamId()))) continue;
            boolean visible = personLevel ? access.personVisible(viewer, e.userId, e.projectId, led)
                    : access.aggregateVisible(viewer, e.userId, e.projectId, led);
            if (!visible) continue;
            out.add(new Row(e, u, pm.get(e.projectId), am.get(e.activityId)));
        }
        return out;
    }

    private static int minutesOf(ProjectEntry e) { return e.minutes + (e.running() ? (int) Calc.runningMinutes(e) : 0); }

    private String key(Group g, Row r, Locale l) {
        return switch (g) {
            case PROJECT -> r.project().code + " " + r.project().name;
            case USER -> r.user().fullName();
            case TEAM -> r.user().team == null ? "–" : r.user().team.name;
            case ACTIVITY -> r.activity().name;
            case WEEK -> r.e().entryDate.get(IsoFields.WEEK_BASED_YEAR) + "-W" + String.format("%02d", r.e().entryDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case MONTH -> YearMonth.from(r.e().entryDate).toString();
            case DAY -> r.e().entryDate.toString();
        };
    }

    public Table projectReport(AppUser viewer, Filter f, List<Group> groups, boolean detail, String lang) {
        Locale l = loc(lang);
        boolean person = detail || groups.contains(Group.USER);
        List<Row> rows = rows(viewer, f, person);
        List<String> notes = new ArrayList<>();
        notes.add(f.from() + " – " + f.to());
        if (f.projectId() != null) {
            projects.findById(f.projectId()).ifPresent(p -> {
                if (p.budgetMinutes != null) {
                    long used = budget.usedMinutes(p, f.to());
                    notes.add(t("col.budget", l) + ": " + h(p.budgetMinutes, l) + " h, " + t("col.used", l) + ": " + h(used, l) + " h ("
                            + used * 100 / p.budgetMinutes + " %)");
                }
            });
        }
        long total = rows.stream().mapToLong(r -> minutesOf(r.e())).sum();
        if (detail) {
            List<String> head = List.of(t("col.date", l), t("col.user", l), t("col.team", l), t("col.project", l), t("col.activity", l),
                    t("col.description", l), t("col.hours", l));
            List<List<String>> out = rows.stream()
                    .sorted(Comparator.comparing((Row r) -> r.e().entryDate).thenComparing(r -> r.user().lastName))
                    .map(r -> List.of(r.e().entryDate.toString(), r.user().fullName(), key(Group.TEAM, r, l), key(Group.PROJECT, r, l),
                            r.activity().name, r.e().description == null ? "" : r.e().description, h(minutesOf(r.e()), l))).toList();
            return new Table(t("report.project", l), head, out, pad(head.size(), t("report.total", l), h(total, l)), notes);
        }
        List<Group> gs = groups.isEmpty() ? List.of(Group.PROJECT) : groups;
        Map<List<String>, Long> sums = new TreeMap<>((a, b) -> String.join("\u0000", a).compareTo(String.join("\u0000", b)));
        for (Row r : rows) sums.merge(gs.stream().map(g -> key(g, r, l)).toList(), (long) minutesOf(r.e()), Long::sum);
        List<String> head = new ArrayList<>(gs.stream().map(g -> t("group." + g, l)).toList());
        head.add(t("col.hours", l));
        List<List<String>> out = new ArrayList<>();
        sums.forEach((k, v) -> { List<String> row = new ArrayList<>(k); row.add(h(v, l)); out.add(row); });
        return new Table(t("report.project", l), head, out, pad(head.size(), t("report.total", l), h(total, l)), notes);
    }

    private static List<String> pad(int size, String label, String value) {
        List<String> r = new ArrayList<>(Collections.nCopies(size, ""));
        r.set(0, label);
        r.set(size - 1, value);
        return r;
    }

    // ---- user report (F-03), also the monthly working-time record ----
    public Table userReport(AppUser viewer, Long userId, LocalDate from, LocalDate to, String lang) {
        Locale l = loc(lang);
        access.requireSelfOrAdmin(viewer, userId);
        checkRange(from, to);
        AppUser u = users.findById(userId).orElseThrow(ApiException::notFound);
        List<DayResult> days = calc.days(u, from, to);
        List<List<String>> out = new ArrayList<>();
        long work = 0, brk = 0, proj = 0, target = 0;
        for (DayResult d : days) {
            boolean relevant = d.hasWorkDay() || d.projectMinutes() > 0 || d.targetMinutes() > 0 || d.absence() != null || d.holidayDe() != null;
            if (!relevant) continue;
            work += d.workMinutes(); brk += d.breakMinutes(); proj += d.projectMinutes(); target += d.targetMinutes();
            String status = d.absence() != null ? t("absence." + d.absence(), l) + (d.absenceHalf() ? " ½" : "")
                    : d.holidayDe() != null ? ("en".equals(l.getLanguage()) ? d.holidayEn() : d.holidayDe()) : "";
            out.add(List.of(d.date().toString(), d.date().getDayOfWeek().getDisplayName(TextStyle.SHORT, l), status,
                    d.start() == null ? "" : Tz.local(d.start()).toLocalTime().toString(),
                    d.end() == null ? "" : Tz.local(d.end()).toLocalTime().toString(),
                    h(d.breakMinutes(), l), h(d.workMinutes(), l), h(d.projectMinutes(), l), h(d.targetMinutes(), l),
                    h(d.workMinutes() - d.targetMinutes(), l), String.valueOf(d.warnings().size())));
        }
        List<String> head = List.of(t("col.date", l), t("col.weekday", l), t("col.status", l), t("col.start", l), t("col.end", l),
                t("col.breaks", l), t("col.work", l), t("col.project", l), t("col.target", l), t("col.delta", l), t("col.warnings", l));
        List<String> totals = List.of(t("report.total", l), "", "", "", "", h(brk, l), h(work, l), h(proj, l), h(target, l), h(work - target, l), "");
        List<String> notes = new ArrayList<>();
        notes.add(u.fullName() + (u.personnelNumber == null ? "" : " (" + u.personnelNumber + ")") + ", " + from + " – " + to);
        long vac = 0, sick = 0, other = 0;
        for (Absence a : absences.findByUserIdAndAbsenceDateBetween(userId, from, to)) {
            double v = a.halfDay ? 0.5 : 1;
            if (a.type == Absence.Type.VACATION) vac += v * 2; else if (a.type == Absence.Type.SICK) sick += v * 2; else other += v * 2;
        }
        notes.add(t("col.vacation", l) + ": " + hd(vac / 2.0, l) + ", " + t("col.sick", l) + ": " + hd(sick / 2.0, l)
                + ", " + t("col.otherAbs", l) + ": " + hd(other / 2.0, l));
        notes.add(t("col.balance", l) + " (" + to + "): " + h(calc.balance(u, to), l) + " h");
        return new Table(t("report.user", l), head, out, totals, notes);
    }

    // ---- completeness (F-06): admin only ----
    public Table completeness(AppUser viewer, LocalDate from, LocalDate to, Long teamId, String lang) {
        Locale l = loc(lang);
        access.requireAdmin();
        checkRange(from, to);
        LocalDate end = to.isBefore(Tz.today()) ? to : Tz.today().minusDays(1);
        List<List<String>> out = new ArrayList<>();
        for (AppUser u : users.findAll()) {
            if (!u.active || (teamId != null && (u.team == null || !u.team.id.equals(teamId)))) continue;
            int missing = 0, below = 0, mismatch = 0, warn = 0;
            StringBuilder missingDates = new StringBuilder();
            if (!end.isBefore(from)) for (DayResult d : calc.days(u, from, end)) {
                for (Msg m : d.hints()) {
                    if (m.key().equals("HINT_NO_ENTRY")) { missing++; if (missingDates.length() < 60) missingDates.append(missingDates.length() > 0 ? ", " : "").append(d.date().getDayOfMonth()).append('.').append(d.date().getMonthValue()).append('.'); }
                    if (m.key().equals("HINT_BELOW_TARGET")) below++;
                    if (m.key().equals("HINT_PROJECT_MISMATCH")) mismatch++;
                }
                warn += d.warnings().size();
            }
            out.add(List.of(u.fullName(), u.team == null ? "–" : u.team.name, String.valueOf(missing), missingDates.toString(),
                    String.valueOf(below), String.valueOf(mismatch), String.valueOf(warn)));
        }
        out.sort(Comparator.comparing((List<String> r) -> Integer.parseInt(r.get(2))).reversed().thenComparing(r -> r.get(0)));
        List<String> head = List.of(t("col.user", l), t("col.team", l), t("col.missingDays", l), t("col.missingDates", l),
                t("col.belowTarget", l), t("col.mismatchDays", l), t("col.warnings", l));
        return new Table(t("report.completeness", l), head, out, List.of(), List.of(from + " – " + to));
    }

    // ---- budget (F-07) ----
    public Table budgetReport(AppUser viewer, String lang) {
        Locale l = loc(lang);
        access.requireAny(Role.BACKOFFICE_ADMIN, Role.MANAGEMENT, Role.PROJECT_LEAD);
        Set<Long> led = access.ledProjectIds(viewer);
        LocalDate today = Tz.today();
        List<List<String>> out = new ArrayList<>();
        for (Project p : projects.findAll()) {
            if (p.budgetMinutes == null || p.archived) continue;
            if (!viewer.isAdmin() && !viewer.has(Role.MANAGEMENT) && !led.contains(p.id)) continue;
            long used = budget.usedMinutes(p, today);
            long pct = used * 100 / p.budgetMinutes;
            String state = pct >= 100 ? t("state.exceeded", l) : pct >= 80 ? t("state.warning80", l) : t("state.ok", l);
            out.add(List.of(p.code + " " + p.name, p.lead == null ? "–" : p.lead.fullName(),
                    t("period." + p.budgetPeriod, l), h(p.budgetMinutes, l), h(used, l), pct + " %", state));
        }
        out.sort(Comparator.comparing((List<String> r) -> Integer.parseInt(r.get(5).replace(" %", ""))).reversed());
        List<String> head = List.of(t("col.project", l), t("col.lead", l), t("col.period", l), t("col.budget", l), t("col.used", l), t("col.percent", l), t("col.state", l));
        return new Table(t("report.budget", l), head, out);
    }

    // ---- overtime (F-09) ----
    public Table overtimeReport(AppUser viewer, LocalDate asOf, boolean byTeam, Long teamId, String lang) {
        Locale l = loc(lang);
        if (byTeam) access.requireAny(Role.BACKOFFICE_ADMIN, Role.MANAGEMENT); else access.requireAdmin();
        List<List<String>> out = new ArrayList<>();
        Map<String, long[]> teams = new TreeMap<>();
        for (AppUser u : users.findAll()) {
            if (!u.active || (teamId != null && (u.team == null || !u.team.id.equals(teamId)))) continue;
            long bal = calc.balance(u, asOf);
            String tn = u.team == null ? "–" : u.team.name;
            long[] agg = teams.computeIfAbsent(tn, k -> new long[2]);
            agg[0] += bal; agg[1]++;
            if (!byTeam) out.add(List.of(u.fullName(), tn, h(u.openingBalanceMinutes, l), h(bal, l)));
        }
        List<String> head;
        if (byTeam) {
            teams.forEach((k, v) -> out.add(List.of(k, String.valueOf(v[1]), h(v[0], l))));
            head = List.of(t("col.team", l), t("col.employees", l), t("col.balance", l));
        } else {
            out.sort(Comparator.comparing((List<String> r) -> r.get(0)));
            head = List.of(t("col.user", l), t("col.team", l), t("col.opening", l), t("col.balance", l));
        }
        return new Table(t("report.overtime", l), head, out, List.of(), List.of(t("col.asOf", l) + " " + asOf));
    }

    // ---- payroll export (F-13): admin only ----
    /** Fixed layout, UTF-8, semicolon separated, dot as decimal separator, one row per employee and month. */
    public Table payroll(AppUser viewer, YearMonth month) {
        access.requireAdmin();
        Locale l = Locale.ROOT;
        LocalDate from = month.atDay(1), to = month.atEndOfMonth();
        List<List<String>> out = new ArrayList<>();
        for (AppUser u : users.findAll().stream().sorted(Comparator.comparing((AppUser x) -> x.lastName).thenComparing(x -> x.firstName)).toList()) {
            List<DayResult> days = calc.days(u, from, to);
            boolean any = u.active || days.stream().anyMatch(d -> d.workMinutes() > 0 || d.absence() != null);
            if (!any) continue;
            long work = 0, target = 0, base = 0;
            double vac = 0, sick = 0, other = 0, hol = 0;
            for (DayResult d : days) {
                work += d.workMinutes(); target += d.targetMinutes(); base += d.baseTargetMinutes();
                double f = d.absenceHalf() ? 0.5 : 1;
                if (d.absence() == Absence.Type.VACATION) vac += f;
                else if (d.absence() == Absence.Type.SICK) sick += f;
                else if (d.absence() != null && d.baseTargetMinutes() > 0) other += f;
                if (d.holidayDe() != null && d.date().getDayOfWeek().getValue() < 6 && d.baseTargetMinutes() == 0 && d.absence() == null) hol += 1;
            }
            out.add(List.of(u.personnelNumber == null ? "" : u.personnelNumber, u.lastName, u.firstName, month.toString(),
                    h(base, l), h(target, l), h(work, l), h(work - target, l), h(calc.balance(u, to), l),
                    String.format(l, "%.1f", vac), String.format(l, "%.1f", sick), String.format(l, "%.1f", other), String.format(l, "%.0f", hol)));
        }
        List<String> head = List.of("PersonnelNumber", "LastName", "FirstName", "Month", "ContractHours", "TargetHours", "WorkedHours",
                "OvertimeMonth", "OvertimeBalance", "VacationDays", "SickDays", "OtherAbsenceDays", "PublicHolidays");
        return new Table("Payroll " + month, head, out);
    }

    // ---- own data (H-03) ----
    public Table ownData(AppUser viewer, LocalDate from, LocalDate to, String lang) {
        Locale l = loc(lang);
        checkRange(from, to);
        List<List<String>> out = new ArrayList<>();
        for (WorkDay w : workDays.findByUserIdAndWorkDateBetweenOrderByWorkDate(viewer.id, from, to))
            out.add(List.of(t("own.workday", l), w.workDate.toString(), Tz.local(w.startAt).toLocalTime().toString(),
                    w.endAt == null ? "" : Tz.local(w.endAt).toLocalTime().toString(), "", "", h(w.breakMinutes(Instant.now()), l),
                    h(w.netMinutes(Instant.now()), l), ""));
        Map<Long, Project> pm = projects.findAll().stream().collect(Collectors.toMap(p -> p.id, p -> p));
        Map<Long, Activity> am = activities.findAll().stream().collect(Collectors.toMap(a -> a.id, a -> a));
        for (ProjectEntry e : entries.findByUserIdAndEntryDateBetweenOrderByEntryDateAscStartAtAscIdAsc(viewer.id, from, to))
            out.add(List.of(t("own.entry", l), e.entryDate.toString(), e.startAt == null ? "" : Tz.local(e.startAt).toLocalTime().toString(),
                    e.endAt == null ? "" : Tz.local(e.endAt).toLocalTime().toString(), pm.get(e.projectId).code + " " + pm.get(e.projectId).name,
                    am.get(e.activityId).name, "", h(e.minutes, l), e.description == null ? "" : e.description));
        for (Absence a : absences.findByUserIdAndAbsenceDateBetween(viewer.id, from, to))
            out.add(List.of(t("own.absence", l), a.absenceDate.toString(), "", "", t("absence." + a.type, l), "", "", a.halfDay ? "0.5" : "1", ""));
        out.sort(Comparator.comparing((List<String> r) -> r.get(1)).thenComparing(r -> r.get(0)));
        List<String> head = List.of(t("col.type", l), t("col.date", l), t("col.start", l), t("col.end", l), t("col.project", l),
                t("col.activity", l), t("col.breaks", l), t("col.hours", l), t("col.description", l));
        return new Table(t("report.ownData", l), head, out, List.of(), List.of(viewer.fullName() + ", " + from + " – " + to));
    }
}
