package de.tantive.timetracking.web;

import de.tantive.timetracking.config.*;
import de.tantive.timetracking.domain.*;
import de.tantive.timetracking.service.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.*;
import java.util.*;

/** Backoffice administration (U-*, E-*, C-01, I-04). Every method requires the backoffice role. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    public record ScheduleIn(LocalDate validFrom, int weeklyMinutes, Set<DayOfWeek> workingDays, String federalState) {}
    public record NameIn(String name, Boolean active) {}
    public record ActiveIn(boolean active) {}
    public record IdsIn(Set<Long> ids) {}
    public record HolidayIn(LocalDate date, String federalState, String nameDe, String nameEn) {}
    public record LockIn(int year, int month, Long userId) {}
    public record ScheduleDto(Long id, LocalDate validFrom, int weeklyMinutes, Set<DayOfWeek> workingDays, String federalState) {}

    private final Access access;
    private final AdminService admin;
    private final ImportService importer;
    private final BudgetService budget;
    private final Repos.Users users;
    private final Repos.Teams teams;
    private final Repos.Projects projects;
    private final Repos.Activities activities;
    private final Repos.Schedules schedules;
    private final Repos.Holidays holidays;
    private final Repos.Locks locks;
    private final Repos.Audits audits;

    public AdminController(Access access, AdminService admin, ImportService importer, BudgetService budget, Repos.Users users,
                           Repos.Teams teams, Repos.Projects projects, Repos.Activities activities, Repos.Schedules schedules,
                           Repos.Holidays holidays, Repos.Locks locks, Repos.Audits audits) {
        this.access = access; this.admin = admin; this.importer = importer; this.budget = budget; this.users = users; this.teams = teams;
        this.projects = projects; this.activities = activities; this.schedules = schedules; this.holidays = holidays;
        this.locks = locks; this.audits = audits;
    }

    // ---- users ----
    @GetMapping("/users")
    public List<Dtos.UserDto> users() {
        access.requireAdmin();
        return users.findAll().stream().sorted(Comparator.comparing((AppUser u) -> u.lastName).thenComparing(u -> u.firstName)).map(Dtos.UserDto::of).toList();
    }

    @GetMapping("/users/{id}")
    public Map<String, Object> user(@PathVariable Long id) {
        access.requireAdmin();
        AppUser u = users.findById(id).orElseThrow(ApiException::notFound);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("user", Dtos.UserDto.of(u));
        r.put("schedules", schedules.findByUserIdOrderByValidFromAsc(id).stream()
                .map(s -> new ScheduleDto(s.id, s.validFrom, s.weeklyMinutes, s.days(), s.federalState)).toList());
        r.put("projectIds", projects.findAll().stream().filter(p -> p.memberIds.contains(id)).map(p -> p.id).toList());
        return r;
    }

    @PostMapping("/users")
    public Dtos.UserDto createUser(@RequestBody AdminService.UserIn in) { return Dtos.UserDto.of(admin.createUser(access.requireAdmin(), in)); }

    @PutMapping("/users/{id}")
    public Dtos.UserDto updateUser(@PathVariable Long id, @RequestBody AdminService.UserIn in) { return Dtos.UserDto.of(admin.updateUser(access.requireAdmin(), id, in)); }

    @PostMapping("/users/{id}/active")
    public void active(@PathVariable Long id, @RequestBody ActiveIn in) { admin.setActive(access.requireAdmin(), id, in.active()); }

    @PostMapping("/users/{id}/invite")
    public void invite(@PathVariable Long id) {
        access.requireAdmin();
        admin.reinvite(id);
    }

    @PostMapping("/users/{id}/schedules")
    public void saveSchedule(@PathVariable Long id, @RequestBody ScheduleIn in) {
        admin.saveSchedule(access.requireAdmin(), id, in.validFrom(), in.weeklyMinutes(), in.workingDays(), in.federalState());
    }

    @DeleteMapping("/schedules/{id}")
    public void deleteSchedule(@PathVariable Long id) { admin.deleteSchedule(access.requireAdmin(), id); }

    @PutMapping("/users/{id}/projects")
    public void userProjects(@PathVariable Long id, @RequestBody IdsIn in) { admin.setUserProjects(access.requireAdmin(), id, in.ids()); }

    // ---- teams / activities ----
    @GetMapping("/teams") public List<Team> teams() { access.requireAdmin(); return teams.findAll(); }
    @PostMapping("/teams") public Team createTeam(@RequestBody NameIn in) { return admin.saveTeam(access.requireAdmin(), null, in.name()); }
    @PutMapping("/teams/{id}") public Team renameTeam(@PathVariable Long id, @RequestBody NameIn in) { return admin.saveTeam(access.requireAdmin(), id, in.name()); }

    @GetMapping("/activities") public List<Activity> activities() { access.requireAdmin(); return activities.findAll(); }
    @PostMapping("/activities") public Activity createActivity(@RequestBody NameIn in) { return admin.saveActivity(access.requireAdmin(), null, in.name(), in.active()); }
    @PutMapping("/activities/{id}") public Activity updateActivity(@PathVariable Long id, @RequestBody NameIn in) { return admin.saveActivity(access.requireAdmin(), id, in.name(), in.active()); }

    // ---- projects ----
    @GetMapping("/projects")
    public List<Dtos.ProjectDto> projects() {
        access.requireAdmin();
        LocalDate today = Tz.today();
        return projects.findAll().stream().sorted(Comparator.comparing(p -> p.code))
                .map(p -> Dtos.ProjectDto.of(p, p.budgetMinutes == null ? null : budget.usedMinutes(p, today))).toList();
    }

    @PostMapping("/projects")
    public Dtos.ProjectDto createProject(@RequestBody AdminService.ProjectIn in) { return Dtos.ProjectDto.of(admin.saveProject(access.requireAdmin(), null, in), null); }

    @PutMapping("/projects/{id}")
    public Dtos.ProjectDto updateProject(@PathVariable Long id, @RequestBody AdminService.ProjectIn in) { return Dtos.ProjectDto.of(admin.saveProject(access.requireAdmin(), id, in), null); }

    @PostMapping("/projects/{id}/archived")
    public void archive(@PathVariable Long id, @RequestBody ActiveIn in) { admin.setArchived(access.requireAdmin(), id, in.active()); }

    @PutMapping("/projects/{id}/members")
    public void members(@PathVariable Long id, @RequestBody IdsIn in) { admin.setMembers(access.requireAdmin(), id, in.ids()); }

    // ---- holidays ----
    @GetMapping("/holidays")
    public List<Holiday> holidays(@RequestParam int year) {
        access.requireAdmin();
        return holidays.findByDateBetween(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)).stream()
                .sorted(Comparator.comparing((Holiday h) -> h.date).thenComparing(h -> h.federalState == null ? "" : h.federalState)).toList();
    }
    @PostMapping("/holidays")
    public Holiday saveHoliday(@RequestBody HolidayIn in) { return admin.saveHoliday(access.requireAdmin(), in.date(), in.federalState(), in.nameDe(), in.nameEn()); }
    @DeleteMapping("/holidays/{id}")
    public void deleteHoliday(@PathVariable Long id) { admin.deleteHoliday(access.requireAdmin(), id); }
    @PostMapping("/holidays/generate")
    public Map<String, Object> generate(@RequestParam int year) { return Map.of("created", admin.generateHolidays(access.requireAdmin(), year)); }

    // ---- locks ----
    @GetMapping("/locks")
    public List<Map<String, Object>> locks() {
        access.requireAdmin();
        List<Map<String, Object>> out = new ArrayList<>();
        for (PeriodLock l : locks.findAll().stream().sorted(Comparator.comparing((PeriodLock x) -> x.id).reversed()).toList()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.id); m.put("year", l.lockYear); m.put("month", l.lockMonth); m.put("userId", l.userId);
            m.put("userName", l.userId == null ? null : users.findById(l.userId).map(AppUser::fullName).orElse("?"));
            m.put("active", l.active); m.put("lockedAt", l.lockedAt); m.put("unlockedAt", l.unlockedAt);
            out.add(m);
        }
        return out;
    }
    @PostMapping("/locks")
    public Map<String, Object> lock(@RequestBody LockIn in) { return Map.of("id", admin.lock(access.requireAdmin(), in.year(), in.month(), in.userId()).id); }
    @PostMapping("/locks/{id}/unlock")
    public void unlock(@PathVariable Long id) { admin.unlock(access.requireAdmin(), id); }

    // ---- import ----
    @PostMapping("/import/{kind}")
    public ImportService.Result importFile(@PathVariable String kind, @RequestParam("file") MultipartFile file) throws IOException {
        AppUser me = access.requireAdmin();
        List<List<String>> rows = ImportService.parse(file.getOriginalFilename(), file.getBytes());
        return switch (kind) {
            case "users" -> importer.importUsers(me, rows);
            case "projects" -> importer.importProjects(me, rows);
            default -> throw ApiException.notFound();
        };
    }
}
