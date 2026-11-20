package de.tantive.timetracking.service;

import de.tantive.timetracking.config.*;
import de.tantive.timetracking.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** Master data: users, teams, projects, activities, schedules, holidays, locks (U-*, E-*, C-01). Every change is logged (H-02). */
@Service
@Transactional
public class AdminService {
    public record UserIn(String email, String firstName, String lastName, String personnelNumber, Set<Role> roles,
                         Long teamId, LocalDate entryDate, LocalDate exitDate, Integer openingBalanceMinutes,
                         LocalDate openingBalanceDate, Boolean sendInvite) {}
    public record ProjectIn(String code, String name, String description, Long leadId, LocalDate startDate, LocalDate endDate,
                            Boolean openToAll, Boolean internal, Integer budgetMinutes, Project.BudgetPeriod budgetPeriod) {}

    private final Repos.Users users;
    private final Repos.Teams teams;
    private final Repos.Projects projects;
    private final Repos.Activities activities;
    private final Repos.Schedules schedules;
    private final Repos.Holidays holidays;
    private final Repos.Locks locks;
    private final AuditService audit;
    private final AuthService auth;

    public AdminService(Repos.Users u, Repos.Teams t, Repos.Projects p, Repos.Activities a, Repos.Schedules s,
                        Repos.Holidays h, Repos.Locks l, AuditService audit, AuthService auth) {
        users = u; teams = t; projects = p; activities = a; schedules = s; holidays = h; locks = l; this.audit = audit; this.auth = auth;
    }

    // ---- users ----
    public AppUser createUser(AppUser actor, UserIn in) {
        if (in.email() == null || in.email().isBlank() || !in.email().contains("@")) throw ApiException.bad("EMAIL_INVALID");
        if (users.findByEmailIgnoreCase(in.email().trim()).isPresent()) throw ApiException.conflict("EMAIL_EXISTS");
        if (in.firstName() == null || in.firstName().isBlank() || in.lastName() == null || in.lastName().isBlank())
            throw ApiException.bad("NAME_REQUIRED");
        AppUser u = new AppUser();
        applyUser(u, in);
        u.email = in.email().trim().toLowerCase();
        u.language = "de";
        u = users.save(u);
        audit.admin(actor, "AppUser", u.id, "CREATE", null, Snapshots.of(u));
        if (in.sendInvite() == null || in.sendInvite()) auth.invite(u);
        return u;
    }

    public AppUser updateUser(AppUser actor, Long id, UserIn in) {
        AppUser u = users.findById(id).orElseThrow(ApiException::notFound);
        Map<String, Object> before = Snapshots.of(u);
        if (in.email() != null && !in.email().equalsIgnoreCase(u.email)) {
            if (users.findByEmailIgnoreCase(in.email().trim()).isPresent()) throw ApiException.conflict("EMAIL_EXISTS");
            u.email = in.email().trim().toLowerCase();
        }
        boolean removesAdmin = u.isAdmin() && in.roles() != null && !in.roles().contains(Role.BACKOFFICE_ADMIN);
        if (removesAdmin && countActiveAdmins() <= 1) throw ApiException.conflict("LAST_ADMIN");
        applyUser(u, in);
        u = users.save(u);
        audit.admin(actor, "AppUser", u.id, "UPDATE", before, Snapshots.of(u));
        return u;
    }

    private long countActiveAdmins() { return users.findAll().stream().filter(x -> x.active && x.isAdmin()).count(); }

    private void applyUser(AppUser u, UserIn in) {
        if (in.firstName() != null) u.firstName = in.firstName();
        if (in.lastName() != null) u.lastName = in.lastName();
        u.personnelNumber = in.personnelNumber();
        if (in.roles() != null) {
            u.roles.clear();
            u.roles.addAll(in.roles());
        }
        if (u.roles.isEmpty()) u.roles.add(Role.EMPLOYEE);
        u.team = in.teamId() == null ? null : teams.findById(in.teamId()).orElseThrow(() -> ApiException.bad("TEAM_UNKNOWN"));
        u.entryDate = in.entryDate();
        u.exitDate = in.exitDate();
        if (in.openingBalanceMinutes() != null) u.openingBalanceMinutes = in.openingBalanceMinutes();
        u.openingBalanceDate = in.openingBalanceDate();
    }

    public void setActive(AppUser actor, Long id, boolean active) {
        AppUser u = users.findById(id).orElseThrow(ApiException::notFound);
        if (!active && u.isAdmin() && u.active && countActiveAdmins() <= 1) throw ApiException.conflict("LAST_ADMIN");
        if (!active && u.id.equals(actor.id)) throw ApiException.conflict("CANNOT_DEACTIVATE_SELF");
        Map<String, Object> before = Snapshots.of(u);
        u.active = active;
        users.save(u);
        audit.admin(actor, "AppUser", u.id, active ? "REACTIVATE" : "DEACTIVATE", before, Snapshots.of(u));
    }

    public void reinvite(Long id) {
        AppUser u = users.findById(id).orElseThrow(ApiException::notFound);
        if (!u.active) throw ApiException.conflict("USER_INACTIVE");
        auth.invite(u);
    }

    // ---- schedules ----
    public TargetSchedule saveSchedule(AppUser actor, Long userId, LocalDate validFrom, int weeklyMinutes, Set<DayOfWeek> days, String state) {
        users.findById(userId).orElseThrow(ApiException::notFound);
        if (validFrom == null || weeklyMinutes < 0 || weeklyMinutes > 60 * 60) throw ApiException.bad("SCHEDULE_INVALID");
        if (days == null || days.isEmpty()) { if (weeklyMinutes > 0) throw ApiException.bad("SCHEDULE_INVALID"); days = Set.of(); }
        TargetSchedule s = schedules.findByUserIdOrderByValidFromAsc(userId).stream()
                .filter(x -> x.validFrom.equals(validFrom)).findFirst().orElse(null);
        Map<String, Object> before = s == null ? null : Snapshots.of(s);
        if (s == null) { s = new TargetSchedule(); s.userId = userId; s.validFrom = validFrom; }
        s.weeklyMinutes = weeklyMinutes;
        s.workingDays = String.join(",", new TreeSet<>(days).stream().map(Enum::name).toList());
        s.federalState = state == null ? "BE" : state;
        s = schedules.save(s);
        audit.admin(actor, "TargetSchedule", s.id, before == null ? "CREATE" : "UPDATE", before, Snapshots.of(s));
        return s;
    }

    public void deleteSchedule(AppUser actor, Long id) {
        TargetSchedule s = schedules.findById(id).orElseThrow(ApiException::notFound);
        audit.admin(actor, "TargetSchedule", s.id, "DELETE", Snapshots.of(s), null);
        schedules.delete(s);
    }

    // ---- teams ----
    public Team saveTeam(AppUser actor, Long id, String name) {
        if (name == null || name.isBlank()) throw ApiException.bad("NAME_REQUIRED");
        teams.findByNameIgnoreCase(name.trim()).filter(t -> !t.id.equals(id)).ifPresent(t -> { throw ApiException.conflict("NAME_EXISTS"); });
        Team t = id == null ? new Team() : teams.findById(id).orElseThrow(ApiException::notFound);
        Map<String, Object> before = id == null ? null : Snapshots.of(t);
        t.name = name.trim();
        t = teams.save(t);
        audit.admin(actor, "Team", t.id, id == null ? "CREATE" : "UPDATE", before, Snapshots.of(t));
        return t;
    }

    // ---- activities ----
    public Activity saveActivity(AppUser actor, Long id, String name, Boolean active) {
        if (name == null || name.isBlank()) throw ApiException.bad("NAME_REQUIRED");
        Activity a = id == null ? new Activity() : activities.findById(id).orElseThrow(ApiException::notFound);
        Map<String, Object> before = id == null ? null : Snapshots.of(a);
        a.name = name.trim();
        if (active != null) a.active = active;
        try { a = activities.saveAndFlush(a); } catch (org.springframework.dao.DataIntegrityViolationException e) { throw ApiException.conflict("NAME_EXISTS"); }
        audit.admin(actor, "Activity", a.id, id == null ? "CREATE" : "UPDATE", before, Snapshots.of(a));
        return a;
    }

    // ---- projects ----
    public Project saveProject(AppUser actor, Long id, ProjectIn in) {
        if (in.code() == null || in.code().isBlank() || in.name() == null || in.name().isBlank()) throw ApiException.bad("NAME_REQUIRED");
        projects.findByCodeIgnoreCase(in.code().trim()).filter(p -> !p.id.equals(id)).ifPresent(p -> { throw ApiException.conflict("CODE_EXISTS"); });
        if (in.startDate() != null && in.endDate() != null && in.endDate().isBefore(in.startDate())) throw ApiException.bad("DATE_RANGE_INVALID");
        if (in.budgetMinutes() != null && in.budgetMinutes() < 0) throw ApiException.bad("BUDGET_INVALID");
        Project p = id == null ? new Project() : projects.findById(id).orElseThrow(ApiException::notFound);
        Map<String, Object> before = id == null ? null : Snapshots.of(p);
        p.code = in.code().trim();
        p.name = in.name().trim();
        p.description = in.description();
        p.lead = in.leadId() == null ? null : users.findById(in.leadId()).orElseThrow(() -> ApiException.bad("USER_UNKNOWN"));
        if (p.lead != null && !p.lead.has(Role.PROJECT_LEAD)) {
            Map<String, Object> ub = Snapshots.of(p.lead);
            p.lead.roles.add(Role.PROJECT_LEAD);
            users.save(p.lead);
            audit.admin(actor, "AppUser", p.lead.id, "UPDATE", ub, Snapshots.of(p.lead));
        }
        p.startDate = in.startDate();
        p.endDate = in.endDate();
        p.openToAll = Boolean.TRUE.equals(in.openToAll());
        if (in.internal() != null) p.internal = in.internal();
        p.budgetMinutes = in.budgetMinutes() == null || in.budgetMinutes() == 0 ? null : in.budgetMinutes();
        p.budgetPeriod = in.budgetPeriod() == null ? Project.BudgetPeriod.TOTAL : in.budgetPeriod();
        p = projects.save(p);
        audit.admin(actor, "Project", p.id, id == null ? "CREATE" : "UPDATE", before, Snapshots.of(p));
        return p;
    }

    public void setArchived(AppUser actor, Long id, boolean archived) {
        Project p = projects.findById(id).orElseThrow(ApiException::notFound);
        Map<String, Object> before = Snapshots.of(p);
        p.archived = archived;
        projects.save(p);
        audit.admin(actor, "Project", p.id, archived ? "ARCHIVE" : "UNARCHIVE", before, Snapshots.of(p));
    }

    public void setMembers(AppUser actor, Long projectId, Set<Long> memberIds) {
        Project p = projects.findById(projectId).orElseThrow(ApiException::notFound);
        Map<String, Object> before = Snapshots.of(p);
        p.memberIds.clear();
        for (Long uid : memberIds) { users.findById(uid).orElseThrow(() -> ApiException.bad("USER_UNKNOWN")); p.memberIds.add(uid); }
        projects.save(p);
        audit.admin(actor, "Project", p.id, "MEMBERS", before, Snapshots.of(p));
    }

    /** Assignment from the user side (U-05). */
    public void setUserProjects(AppUser actor, Long userId, Set<Long> projectIds) {
        users.findById(userId).orElseThrow(ApiException::notFound);
        for (Project p : projects.findAll()) {
            boolean want = projectIds.contains(p.id), has = p.memberIds.contains(userId);
            if (want == has) continue;
            Map<String, Object> before = Snapshots.of(p);
            if (want) p.memberIds.add(userId); else p.memberIds.remove(userId);
            projects.save(p);
            audit.admin(actor, "Project", p.id, "MEMBERS", before, Snapshots.of(p));
        }
    }

    // ---- holidays ----
    public Holiday saveHoliday(AppUser actor, LocalDate date, String state, String nameDe, String nameEn) {
        String st = state == null || state.isBlank() ? null : state;
        Holiday h = holidays.findByDateBetween(date, date).stream()
                .filter(x -> Objects.equals(x.federalState, st)).findFirst().orElse(null);
        Map<String, Object> before = h == null ? null : Snapshots.of(h);
        if (h == null) h = new Holiday();
        h.date = date; h.federalState = st; h.nameDe = nameDe; h.nameEn = nameEn == null || nameEn.isBlank() ? nameDe : nameEn;
        h = holidays.save(h);
        audit.admin(actor, "Holiday", h.id, before == null ? "CREATE" : "UPDATE", before, Snapshots.of(h));
        return h;
    }

    public void deleteHoliday(AppUser actor, Long id) {
        Holiday h = holidays.findById(id).orElseThrow(ApiException::notFound);
        audit.admin(actor, "Holiday", h.id, "DELETE", Snapshots.of(h), null);
        holidays.delete(h);
    }

    /** Generates the public holidays of a year (nationwide + per state) for I-04. Maintained yearly; review before use. */
    public int generateHolidays(AppUser actor, int year) {
        int n = 0;
        for (HolidayCalendar.Entry e : HolidayCalendar.forYear(year)) {
            boolean exists = holidays.findByDateBetween(e.date(), e.date()).stream().anyMatch(x -> Objects.equals(x.federalState, e.state()));
            if (exists) continue;
            saveHoliday(actor, e.date(), e.state(), e.nameDe(), e.nameEn());
            n++;
        }
        return n;
    }

    // ---- locks ----
    public PeriodLock lock(AppUser actor, int year, int month, Long userId) {
        YearMonth.of(year, month);
        for (PeriodLock l : locks.findByLockYearAndLockMonthAndActiveTrue(year, month))
            if (Objects.equals(l.userId, userId)) throw ApiException.conflict("ALREADY_LOCKED");
        PeriodLock l = new PeriodLock();
        l.lockYear = year; l.lockMonth = month; l.userId = userId; l.lockedBy = actor.id;
        l = locks.save(l);
        audit.admin(actor, "PeriodLock", l.id, "LOCK", null, Snapshots.of(l));
        return l;
    }

    public void unlock(AppUser actor, Long lockId) {
        PeriodLock l = locks.findById(lockId).orElseThrow(ApiException::notFound);
        if (!l.active) return;
        Map<String, Object> before = Snapshots.of(l);
        l.active = false; l.unlockedAt = Instant.now();
        locks.save(l);
        audit.admin(actor, "PeriodLock", l.id, "UNLOCK", before, Snapshots.of(l));
    }
}
