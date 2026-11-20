package de.tantive.timetracking.config;

import de.tantive.timetracking.domain.*;
import de.tantive.timetracking.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;

/**
 * Fictitious demo data (step 0.3): all roles, teams, federal states, a contract change, opening balances,
 * projects with and without budgets, an archived project, absences, rule violations and one locked month.
 * Only created with app.demo-data=true on an empty installation. All demo users have the password below.
 */
@Component
@Order(10)
public class DemoData implements ApplicationRunner {
    public static final String PASSWORD = "Demo-Passw0rd1";
    private static final String WEEK = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY";

    private final Repos.Users users;
    private final Repos.Teams teams;
    private final Repos.Projects projects;
    private final Repos.Activities activities;
    private final AdminService admin;
    private final WorkTimeService work;
    private final EntryService entries;
    private final AbsenceService absences;
    private final Calc calc;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;
    @Value("${app.demo-data:false}") boolean enabled;

    public DemoData(Repos.Users u, Repos.Teams t, Repos.Projects p, Repos.Activities a, AdminService admin, WorkTimeService w,
                    EntryService e, AbsenceService ab, Calc calc, PasswordEncoder enc, TransactionTemplate tx) {
        users = u; teams = t; projects = p; activities = a; this.admin = admin; work = w; entries = e; absences = ab;
        this.calc = calc; encoder = enc; this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled || users.count() > 1) return;
        tx.executeWithoutResult(s -> create());
    }

    private AppUser person(AppUser actor, String first, String last, String nr, Long team, LocalDate entry, int hours, String days, String state,
                           int openingMinutes, Role... roles) {
        AppUser u = admin.createUser(actor, new AdminService.UserIn(first.toLowerCase() + "." + last.toLowerCase() + "@demo.local", first, last, nr,
                new HashSet<>(Arrays.asList(roles)), team, entry, null, openingMinutes, entry, false));
        u.passwordHash = encoder.encode(PASSWORD);
        u.language = "de";
        users.save(u);
        if (hours > 0) admin.saveSchedule(actor, u.id, entry, hours * 60, days == null ? null : parseDays(days), state);
        return u;
    }

    private static Set<DayOfWeek> parseDays(String d) {
        Set<DayOfWeek> s = EnumSet.noneOf(DayOfWeek.class);
        for (String x : d.split(",")) s.add(DayOfWeek.valueOf(x));
        return s;
    }

    private void create() {
        LocalDate today = Tz.today();
        LocalDate start = today.withDayOfMonth(1).minusMonths(2);
        AppUser root = users.findAll().get(0);
        Team eng = admin.saveTeam(root, null, "Engineering"), con = admin.saveTeam(root, null, "Consulting"), back = admin.saveTeam(root, null, "Backoffice");
        root.team = back; root.language = "en"; users.save(root);

        AppUser sabine = person(root, "Sabine", "Admin", "1001", back.id, start, 40, WEEK, "BE", 0, Role.BACKOFFICE_ADMIN, Role.EMPLOYEE);
        AppUser mara = person(root, "Mara", "Manager", "1002", back.id, start, 0, null, "BE", 0, Role.MANAGEMENT);
        AppUser paul = person(root, "Paul", "Lead", "2001", eng.id, start, 40, WEEK, "BE", 0, Role.EMPLOYEE, Role.PROJECT_LEAD);
        AppUser anna = person(root, "Anna", "Schmidt", "2002", eng.id, start, 40, WEEK, "BE", 0, Role.EMPLOYEE);
        AppUser ben = person(root, "Ben", "Mueller", "2003", eng.id, start, 40, WEEK, "BY", 3 * 60, Role.EMPLOYEE);
        AppUser clara = person(root, "Clara", "Weber", "3001", con.id, start, 30, "MONDAY,WEDNESDAY,FRIDAY", "NW", 0, Role.EMPLOYEE, Role.PROJECT_LEAD);
        AppUser dieter = person(root, "Dieter", "Fischer", "3002", con.id, start, 40, WEEK, "HH", 12 * 60, Role.EMPLOYEE);
        AppUser eva = person(root, "Eva", "Becker", "1003", back.id, start, 20, "TUESDAY,WEDNESDAY,THURSDAY,FRIDAY", "BE", 0, Role.EMPLOYEE);
        AppUser frank = person(root, "Frank", "Alt", "9001", con.id, start.minusMonths(6), 40, WEEK, "BE", 0, Role.EMPLOYEE);
        frank.exitDate = start.plusDays(10); users.save(frank);
        admin.setActive(root, frank.id, false);

        // contract changes / moves (validity dates)
        admin.saveSchedule(root, ben.id, start.plusMonths(1), 32 * 60, parseDays("MONDAY,TUESDAY,WEDNESDAY,THURSDAY"), "BY");
        admin.saveSchedule(root, dieter.id, start.plusMonths(1), 40 * 60, parseDays(WEEK), "NW");

        for (int y = start.getYear(); y <= today.getYear() + 1; y++) admin.generateHolidays(root, y);

        Map<String, Activity> act = new HashMap<>();
        for (Activity a : activities.findAll()) act.put(a.name, a);
        Project adm = projects.findByCodeIgnoreCase("ADM").orElseThrow();
        Project web = admin.saveProject(root, null, new AdminService.ProjectIn("WEB", "Website Relaunch", "Neue Unternehmenswebsite", paul.id, start, null, false, false, 520 * 60, Project.BudgetPeriod.TOTAL));
        Project app = admin.saveProject(root, null, new AdminService.ProjectIn("APP", "Mobile App", "iOS/Android App", paul.id, start, null, false, false, 200 * 60, Project.BudgetPeriod.MONTHLY));
        Project crm = admin.saveProject(root, null, new AdminService.ProjectIn("CRM", "CRM Migration", "Datenmigration und Schulung", clara.id, start, null, false, false, 400 * 60, Project.BudgetPeriod.TOTAL));
        Project sup = admin.saveProject(root, null, new AdminService.ProjectIn("SUP", "Support & Wartung", "Offen für alle", null, null, null, true, false, null, null));
        Project old = admin.saveProject(root, null, new AdminService.ProjectIn("OLD", "Altprojekt (archiviert)", "Abgeschlossen", paul.id, null, null, false, false, null, null));
        admin.setMembers(root, web.id, Set.of(paul.id, anna.id, ben.id, clara.id));
        admin.setMembers(root, app.id, Set.of(paul.id, ben.id, dieter.id));
        admin.setMembers(root, crm.id, Set.of(clara.id, dieter.id, eva.id));
        admin.setMembers(root, old.id, Set.of(anna.id));
        admin.setArchived(root, old.id, true);

        Map<Long, List<Project>> bookable = new HashMap<>();
        for (AppUser u : List.of(sabine, paul, anna, ben, clara, dieter, eva)) {
            List<Project> l = new ArrayList<>();
            for (Project p : List.of(web, app, crm, sup, adm)) if (p.openToAll || p.memberIds.contains(u.id)) l.add(p);
            bookable.put(u.id, l);
        }

        LocalDate yesterday = today.minusDays(1);
        // absences
        LocalDate vac = start.plusMonths(1).plusDays(7);
        for (int i = 0; i < 5; i++) absences.set(anna, anna, vac.plusDays(i), Absence.Type.VACATION, false);
        absences.set(ben, ben, start.plusMonths(1).plusDays(14), Absence.Type.SICK, false);
        absences.set(clara, clara, start.plusMonths(1).plusDays(16), Absence.Type.OTHER, true);
        absences.set(eva, eva, start.plusDays(20), Absence.Type.VACATION, true);

        Random rnd = new Random(42);
        String[] activityNames = {"Development", "Meeting", "Training", "Administration"};
        for (AppUser u : List.of(sabine, paul, anna, ben, clara, dieter, eva)) {
            List<DayResult> days = calc.days(u, start, yesterday);
            int gap = 0;
            for (DayResult d : days) {
                if (d.targetMinutes() <= 0) continue;
                // eva forgets two days, dieter overworks on one of the last days
                boolean forgot = u == eva && (d.date().equals(yesterday.minusDays(3)) || d.date().equals(start.plusDays(35)));
                if (forgot || d.date().equals(yesterday) && u == sabine) continue;
                int startMin = 8 * 60 + rnd.nextInt(4) * 10;
                int worked = d.targetMinutes() + (rnd.nextInt(5) - 2) * 15;
                int brk = 30 + (rnd.nextInt(3) == 0 ? 15 : 0);
                boolean overwork = u == dieter && d.date().equals(yesterday.minusDays(2));
                if (overwork) { worked = 11 * 60; brk = 20; }
                int end = startMin + worked + brk;
                LocalDate dt = d.date();
                work.save(u, u, dt, dt.atTime(startMin / 60, startMin % 60), dt.atTime(end / 60, end % 60),
                        List.of(new WorkTimeService.BreakIn(dt.atTime(12, 0), dt.atTime(12, brk >= 60 ? 59 : brk))));
                // allocate project time (sometimes a bit less than working time → hint)
                int projectMinutes = worked - (rnd.nextInt(10) == 0 ? 30 : 0);
                List<Project> options = bookable.get(u.id);
                int parts = 1 + rnd.nextInt(3);
                int remaining = projectMinutes;
                for (int i = 0; i < parts && remaining > 0; i++) {
                    int m = i == parts - 1 ? remaining : Math.max(15, (remaining / (parts - i) / 15) * 15);
                    m = Math.min(m, remaining);
                    Project p = options.get(rnd.nextInt(options.size()));
                    String an = p == adm ? "Administration" : activityNames[rnd.nextInt(3)];
                    entries.create(u, u, new EntryService.Input(dt, p.id, act.get(an).id, null, null, m, i == 0 ? "Demo" : null));
                    remaining -= m;
                }
            }
        }
        // the oldest month is closed; the recent months stay open for the locking demo
        admin.lock(root, start.getYear(), start.getMonthValue(), null);
    }
}
