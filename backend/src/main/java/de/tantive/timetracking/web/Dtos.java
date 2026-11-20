package de.tantive.timetracking.web;

import de.tantive.timetracking.config.Tz;
import de.tantive.timetracking.domain.*;
import de.tantive.timetracking.service.Msg;
import java.time.*;
import java.util.*;

public final class Dtos {
    private Dtos() {}

    public record UserDto(Long id, String email, String firstName, String lastName, String personnelNumber, boolean active,
                          Set<Role> roles, Long teamId, String teamName, LocalDate entryDate, LocalDate exitDate, String language,
                          Long defaultProjectId, String preferredView, boolean notifyBudget, int openingBalanceMinutes,
                          LocalDate openingBalanceDate, boolean invitePending, boolean locked) {
        public static UserDto of(AppUser u) {
            return new UserDto(u.id, u.email, u.firstName, u.lastName, u.personnelNumber, u.active, new TreeSet<>(u.roles),
                    u.team == null ? null : u.team.id, u.team == null ? null : u.team.name, u.entryDate, u.exitDate, u.language,
                    u.defaultProjectId, u.preferredView, u.notifyBudget, u.openingBalanceMinutes, u.openingBalanceDate,
                    u.inviteToken != null, u.lockedUntil != null && u.lockedUntil.isAfter(Instant.now()));
        }
    }

    public record ProjectDto(Long id, String code, String name, String description, Long leadId, String leadName,
                             LocalDate startDate, LocalDate endDate, boolean archived, boolean openToAll, boolean internal,
                             Integer budgetMinutes, Project.BudgetPeriod budgetPeriod, Set<Long> memberIds, Long usedMinutes) {
        public static ProjectDto of(Project p, Long used) {
            return new ProjectDto(p.id, p.code, p.name, p.description, p.lead == null ? null : p.lead.id,
                    p.lead == null ? null : p.lead.fullName(), p.startDate, p.endDate, p.archived, p.openToAll, p.internal,
                    p.budgetMinutes, p.budgetPeriod, new TreeSet<>(p.memberIds), used);
        }
    }

    public record BreakDto(LocalDateTime start, LocalDateTime end) {}
    public record WorkDayDto(Long id, LocalDate date, LocalDateTime start, LocalDateTime end, List<BreakDto> breaks, boolean running, boolean paused) {
        public static WorkDayDto of(WorkDay w) {
            if (w == null) return null;
            return new WorkDayDto(w.id, w.workDate, Tz.local(w.startAt), Tz.local(w.endAt),
                    w.breaks.stream().map(b -> new BreakDto(Tz.local(b.startAt), Tz.local(b.endAt))).toList(),
                    w.endAt == null, w.breaks.stream().anyMatch(b -> b.endAt == null));
        }
    }
    public record EntryDto(Long id, Long userId, LocalDate date, Long projectId, Long activityId, LocalDateTime start,
                           LocalDateTime end, int minutes, String description, boolean running, LocalDateTime createdAt) {
        public static EntryDto of(ProjectEntry e) {
            return new EntryDto(e.id, e.userId, e.entryDate, e.projectId, e.activityId, Tz.local(e.startAt), Tz.local(e.endAt),
                    e.running() ? (int) de.tantive.timetracking.service.Calc.runningMinutes(e) : e.minutes, e.description, e.running(),
                    Tz.local(e.createdAt));
        }
    }
    public record DayDto(LocalDate date, int targetMinutes, int baseTargetMinutes, long workMinutes, long breakMinutes, long projectMinutes,
                         String holidayDe, String holidayEn, Absence.Type absence, boolean absenceHalf, WorkDayDto workDay,
                         List<EntryDto> entries, List<Msg> warnings, List<Msg> hints) {}
}
