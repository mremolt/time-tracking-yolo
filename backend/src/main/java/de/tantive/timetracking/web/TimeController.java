package de.tantive.timetracking.web;

import de.tantive.timetracking.config.*;
import de.tantive.timetracking.domain.*;
import de.tantive.timetracking.service.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.stream.Collectors;

/** Recording endpoints for employees: working time, project time, absences, day/week data and summaries. */
@RestController
@RequestMapping("/api")
public class TimeController {
    public record BreakIn(LocalDateTime start, LocalDateTime end) {}
    public record WorkDayIn(Long userId, LocalDate date, LocalDateTime start, LocalDateTime end, List<BreakIn> breaks) {}
    public record EntryIn(Long userId, LocalDate date, Long projectId, Long activityId, LocalTime start, LocalTime end,
                          Integer minutes, String description) {}
    public record TimerIn(Long projectId, Long activityId) {}
    public record CellIn(Long userId, LocalDate date, Long projectId, Long activityId, int minutes) {}
    public record CopyIn(Long userId, LocalDate from, LocalDate to, boolean week) {}
    public record AbsenceIn(Long userId, LocalDate date, Absence.Type type, boolean halfDay) {}

    private final Access access;
    private final Calc calc;
    private final WorkTimeService work;
    private final EntryService entryService;
    private final AbsenceService absenceService;
    private final Repos.WorkDays workDays;
    private final Repos.Entries entries;
    private final Repos.Projects projects;
    private final Repos.Locks locks;
    private final LockService lockService;

    public TimeController(Access access, Calc calc, WorkTimeService work, EntryService es, AbsenceService as, Repos.WorkDays wd,
                          Repos.Entries entries, Repos.Projects projects, Repos.Locks locks, LockService ls) {
        this.access = access; this.calc = calc; this.work = work; this.entryService = es; this.absenceService = as;
        this.workDays = wd; this.entries = entries; this.projects = projects; this.locks = locks; this.lockService = ls;
    }

    // ---- read ----
    @GetMapping("/days")
    public List<Dtos.DayDto> days(@RequestParam LocalDate from, @RequestParam LocalDate to, @RequestParam(required = false) Long userId) {
        AppUser me = access.me();
        AppUser s = access.subject(me, userId);
        if (to.isBefore(from) || from.plusDays(62).isBefore(to)) throw ApiException.bad("DATE_RANGE_INVALID");
        Map<LocalDate, WorkDay> wd = workDays.findByUserIdAndWorkDateBetweenOrderByWorkDate(s.id, from, to).stream()
                .collect(Collectors.toMap(w -> w.workDate, w -> w));
        Map<LocalDate, List<ProjectEntry>> en = entries.findByUserIdAndEntryDateBetweenOrderByEntryDateAscStartAtAscIdAsc(s.id, from, to).stream()
                .collect(Collectors.groupingBy(e -> e.entryDate));
        List<Dtos.DayDto> out = new ArrayList<>();
        for (DayResult d : calc.days(s, from, to))
            out.add(new Dtos.DayDto(d.date(), d.targetMinutes(), d.baseTargetMinutes(), d.workMinutes(), d.breakMinutes(), d.projectMinutes(),
                    d.holidayDe(), d.holidayEn(), d.absence(), d.absenceHalf(), Dtos.WorkDayDto.of(wd.get(d.date())),
                    en.getOrDefault(d.date(), List.of()).stream().map(Dtos.EntryDto::of).toList(), d.warnings(), d.hints()));
        return out;
    }

    /** Running timers and the overtime balance (B-13). */
    @GetMapping("/status")
    public Map<String, Object> status(@RequestParam(required = false) Long userId) {
        AppUser me = access.me();
        AppUser s = access.subject(me, userId);
        LocalDate today = Tz.today();
        WorkDay w = null;
        for (LocalDate d : new LocalDate[]{today, today.minusDays(1)}) {
            WorkDay x = workDays.findByUserIdAndWorkDate(s.id, d).orElse(null);
            if (x != null && x.endAt == null) { w = x; break; }
        }
        if (w == null) w = workDays.findByUserIdAndWorkDate(s.id, today).orElse(null);
        List<ProjectEntry> running = entries.findByUserIdAndEndAtIsNullAndStartAtIsNotNull(s.id);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("today", today);
        r.put("workDay", Dtos.WorkDayDto.of(w));
        r.put("projectTimer", running.isEmpty() ? null : Dtos.EntryDto.of(running.get(0)));
        r.put("balanceMinutes", calc.balance(s, today));
        return r;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam LocalDate from, @RequestParam LocalDate to, @RequestParam(required = false) Long userId) {
        AppUser me = access.me();
        AppUser s = access.subject(me, userId);
        if (to.isBefore(from) || from.plusYears(1).isBefore(to)) throw ApiException.bad("DATE_RANGE_INVALID");
        long target = 0, workMin = 0, proj = 0;
        Map<String, long[]> weeks = new TreeMap<>();
        for (DayResult d : calc.days(s, from, to)) {
            target += d.targetMinutes(); workMin += d.workMinutes(); proj += d.projectMinutes();
            long[] w = weeks.computeIfAbsent(d.date().get(IsoFields.WEEK_BASED_YEAR) + "-W" + String.format("%02d", d.date().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)), k -> new long[3]);
            w[0] += d.targetMinutes(); w[1] += d.workMinutes(); w[2] += d.projectMinutes();
        }
        Map<Long, Project> pm = projects.findAll().stream().collect(Collectors.toMap(p -> p.id, p -> p));
        Map<String, Long> byProject = new TreeMap<>();
        for (ProjectEntry e : entries.findByUserIdAndEntryDateBetweenOrderByEntryDateAscStartAtAscIdAsc(s.id, from, to)) {
            Project p = pm.get(e.projectId);
            byProject.merge(p.code + " " + p.name, (long) e.minutes, Long::sum);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("targetMinutes", target); r.put("workMinutes", workMin); r.put("projectMinutes", proj);
        r.put("deltaMinutes", workMin - target);
        r.put("balanceMinutes", calc.balance(s, to));
        r.put("openingBalanceMinutes", s.openingBalanceMinutes);
        r.put("weeks", weeks.entrySet().stream().map(e -> Map.<String, Object>of("week", e.getKey(), "targetMinutes", e.getValue()[0],
                "workMinutes", e.getValue()[1], "projectMinutes", e.getValue()[2])).toList());
        r.put("byProject", byProject.entrySet().stream().map(e -> Map.<String, Object>of("name", e.getKey(), "minutes", e.getValue())).toList());
        return r;
    }

    @GetMapping("/locked")
    public boolean locked(@RequestParam LocalDate date, @RequestParam(required = false) Long userId) {
        AppUser me = access.me();
        return lockService.isLocked(access.subject(me, userId).id, date);
    }

    // ---- working time ----
    @PutMapping("/work-days")
    public Dtos.WorkDayDto saveWorkDay(@RequestBody WorkDayIn in) {
        AppUser me = access.me();
        AppUser s = access.subject(me, in.userId());
        if (in.date() == null) throw ApiException.bad("DATE_REQUIRED");
        return Dtos.WorkDayDto.of(work.save(me, s, in.date(), in.start(), in.end(),
                in.breaks() == null ? List.of() : in.breaks().stream().map(b -> new WorkTimeService.BreakIn(b.start(), b.end())).toList()));
    }

    @DeleteMapping("/work-days/{id}")
    public void deleteWorkDay(@PathVariable Long id) { work.delete(access.me(), id); }

    @PostMapping("/work/start") public Dtos.WorkDayDto start() { return Dtos.WorkDayDto.of(work.start(access.me())); }
    @PostMapping("/work/pause") public Dtos.WorkDayDto pause() { return Dtos.WorkDayDto.of(work.pause(access.me())); }
    @PostMapping("/work/resume") public Dtos.WorkDayDto resume() { return Dtos.WorkDayDto.of(work.resume(access.me())); }
    @PostMapping("/work/stop") public Dtos.WorkDayDto stop() { return Dtos.WorkDayDto.of(work.stop(access.me())); }

    // ---- project time ----
    @PostMapping("/entries")
    public Dtos.EntryDto createEntry(@RequestBody EntryIn in) {
        AppUser me = access.me();
        return Dtos.EntryDto.of(entryService.create(me, access.subject(me, in.userId()), toInput(in)));
    }

    @PutMapping("/entries/{id}")
    public Dtos.EntryDto updateEntry(@PathVariable Long id, @RequestBody EntryIn in) {
        return Dtos.EntryDto.of(entryService.update(access.me(), id, toInput(in)));
    }

    @DeleteMapping("/entries/{id}")
    public void deleteEntry(@PathVariable Long id) { entryService.delete(access.me(), id); }

    @PostMapping("/entries/timer/start")
    public Dtos.EntryDto startTimer(@RequestBody TimerIn in) { return Dtos.EntryDto.of(entryService.startTimer(access.me(), in.projectId(), in.activityId())); }

    @PostMapping("/entries/timer/stop")
    public Dtos.EntryDto stopTimer() { return Dtos.EntryDto.of(entryService.stopTimer(access.me(), true)); }

    @PutMapping("/entries/cell")
    public void cell(@RequestBody CellIn in) {
        AppUser me = access.me();
        entryService.setCell(me, access.subject(me, in.userId()), in.date(), in.projectId(), in.activityId(), in.minutes());
    }

    @PostMapping("/entries/copy")
    public Map<String, Object> copy(@RequestBody CopyIn in) {
        AppUser me = access.me();
        return Map.of("copied", entryService.copy(me, access.subject(me, in.userId()), in.from(), in.to(), in.week()));
    }

    private static EntryService.Input toInput(EntryIn in) {
        return new EntryService.Input(in.date(), in.projectId(), in.activityId(), in.start(), in.end(), in.minutes(), in.description());
    }

    // ---- absences ----
    @PutMapping("/absences")
    public void setAbsence(@RequestBody AbsenceIn in) {
        AppUser me = access.me();
        if (in.date() == null || in.type() == null) throw ApiException.bad("VALIDATION");
        absenceService.set(me, access.subject(me, in.userId()), in.date(), in.type(), in.halfDay());
    }

    @DeleteMapping("/absences")
    public void clearAbsence(@RequestParam LocalDate date, @RequestParam(required = false) Long userId) {
        AppUser me = access.me();
        absenceService.clear(me, access.subject(me, userId), date);
    }
}
