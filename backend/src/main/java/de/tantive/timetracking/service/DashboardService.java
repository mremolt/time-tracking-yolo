package de.tantive.timetracking.service;

import de.tantive.timetracking.config.*;
import de.tantive.timetracking.domain.*;
import org.springframework.stereotype.Service;
import java.time.*;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.stream.Collectors;

/** Overview dashboard for backoffice and management. Contains aggregates only, never per-person data (F-01, F-14, D-10). */
@Service
public class DashboardService {
    private final Repos.Users users;
    private final Repos.Projects projects;
    private final Repos.Entries entries;
    private final Calc calc;
    private final BudgetService budget;
    private final Access access;

    public DashboardService(Repos.Users u, Repos.Projects p, Repos.Entries e, Calc c, BudgetService b, Access a) {
        users = u; projects = p; entries = e; calc = c; budget = b; access = a;
    }

    public Map<String, Object> dashboard(LocalDate from, LocalDate to) {
        access.requireAny(Role.BACKOFFICE_ADMIN, Role.MANAGEMENT);
        if (from == null || to == null || to.isBefore(from) || from.plusYears(2).isBefore(to)) throw ApiException.bad("DATE_RANGE_INVALID");
        Map<Long, AppUser> um = users.findAll().stream().collect(Collectors.toMap(u -> u.id, u -> u));
        Map<Long, Project> pm = projects.findAll().stream().collect(Collectors.toMap(p -> p.id, p -> p));
        Map<String, Long> byProject = new TreeMap<>(), byTeam = new TreeMap<>();
        Map<String, long[]> weekly = new TreeMap<>();
        long projectTotal = 0;
        for (ProjectEntry e : entries.findByEntryDateBetween(from, to)) {
            long m = e.minutes + (e.running() ? Calc.runningMinutes(e) : 0);
            projectTotal += m;
            Project p = pm.get(e.projectId);
            byProject.merge(p.code + " " + p.name, m, Long::sum);
            AppUser u = um.get(e.userId);
            byTeam.merge(u.team == null ? "–" : u.team.name, m, Long::sum);
            weekly.computeIfAbsent(week(e.entryDate), k -> new long[2])[1] += m;
        }
        long workTotal = 0, overtimeTotal = 0, missingDays = 0, missingEmployees = 0;
        Map<String, Long> overtimeByTeam = new TreeMap<>();
        LocalDate end = to.isBefore(Tz.today()) ? to : Tz.today().minusDays(1);
        for (AppUser u : um.values()) {
            if (!u.active) continue;
            if (!end.isBefore(from)) {
                int missing = 0;
                for (DayResult d : calc.days(u, from, end)) {
                    workTotal += d.workMinutes();
                    weekly.computeIfAbsent(week(d.date()), k -> new long[2])[0] += d.workMinutes();
                    if (d.hints().stream().anyMatch(h -> h.key().equals("HINT_NO_ENTRY"))) missing++;
                }
                missingDays += missing;
                if (missing > 0) missingEmployees++;
            }
            long bal = calc.balance(u, to);
            overtimeTotal += bal;
            overtimeByTeam.merge(u.team == null ? "–" : u.team.name, bal, Long::sum);
        }
        List<Map<String, Object>> near = new ArrayList<>();
        LocalDate today = Tz.today();
        for (Project p : pm.values()) {
            if (p.budgetMinutes == null || p.archived) continue;
            long pct = budget.usedMinutes(p, today) * 100 / p.budgetMinutes;
            if (pct >= 80) near.add(Map.of("project", p.code + " " + p.name, "percent", pct));
        }
        near.sort(Comparator.comparing((Map<String, Object> x) -> (Long) x.get("percent")).reversed());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("from", from); r.put("to", to);
        r.put("projectMinutes", projectTotal);
        r.put("workMinutes", workTotal);
        r.put("byProject", top(byProject, 10));
        r.put("byTeam", top(byTeam, 50));
        r.put("weekly", weekly.entrySet().stream().map(x -> Map.<String, Object>of("week", x.getKey(), "workMinutes", x.getValue()[0], "projectMinutes", x.getValue()[1])).toList());
        r.put("overtimeTotal", overtimeTotal);
        r.put("overtimeByTeam", top(overtimeByTeam, 50));
        r.put("missingDays", missingDays);
        r.put("missingEmployees", missingEmployees);
        r.put("nearBudget", near);
        return r;
    }

    private static String week(LocalDate d) {
        return d.get(IsoFields.WEEK_BASED_YEAR) + "-W" + String.format("%02d", d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    private static List<Map<String, Object>> top(Map<String, Long> m, int n) {
        return m.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(n)
                .map(e -> Map.<String, Object>of("name", e.getKey(), "minutes", e.getValue())).toList();
    }
}
