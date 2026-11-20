package de.tantive.timetracking.service;

import de.tantive.timetracking.config.*;
import de.tantive.timetracking.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** Project time entries, project timer, week-grid cells and copy functions (B-01..B-10, B-12). */
@Service
@Transactional
public class EntryService {
    public record Input(LocalDate date, Long projectId, Long activityId, LocalTime start, LocalTime end,
                        Integer minutes, String description) {}

    private final Repos.Entries repo;
    private final Repos.Projects projects;
    private final Repos.Activities activities;
    private final LockService locks;
    private final AuditService audit;
    private final BudgetService budget;
    private final Access access;

    public EntryService(Repos.Entries repo, Repos.Projects projects, Repos.Activities activities, LockService locks,
                        AuditService audit, BudgetService budget, Access access) {
        this.repo = repo; this.projects = projects; this.activities = activities; this.locks = locks;
        this.audit = audit; this.budget = budget; this.access = access;
    }

    private Project checkProject(AppUser actor, AppUser subject, Long projectId) {
        Project p = projects.findById(projectId).orElseThrow(() -> ApiException.bad("PROJECT_UNKNOWN"));
        boolean ok = p.archived ? false : (actor.isAdmin() || access.canBook(subject, p));
        if (!ok) throw ApiException.bad(p.archived ? "PROJECT_ARCHIVED" : "PROJECT_NOT_ASSIGNED");
        return p;
    }

    private void checkActivity(Long id) {
        Activity a = activities.findById(id).orElseThrow(() -> ApiException.bad("ACTIVITY_UNKNOWN"));
        if (!a.active) throw ApiException.bad("ACTIVITY_INACTIVE");
    }

    private void apply(ProjectEntry e, Input in) {
        e.entryDate = in.date();
        e.projectId = in.projectId();
        e.activityId = in.activityId();
        e.description = in.description();
        if (in.start() != null && in.end() != null) {
            LocalDateTime s = in.date().atTime(in.start());
            LocalDateTime en = in.end().isAfter(in.start()) ? in.date().atTime(in.end()) : in.date().plusDays(1).atTime(in.end());
            e.startAt = Tz.toInstant(s);
            e.endAt = Tz.toInstant(en);
            e.minutes = (int) Duration.between(e.startAt, e.endAt).toMinutes();
        } else if (in.minutes() != null) {
            if (in.minutes() <= 0 || in.minutes() > 24 * 60) throw ApiException.bad("DURATION_INVALID");
            e.startAt = null; e.endAt = null;
            e.minutes = in.minutes();
        } else throw ApiException.bad("DURATION_REQUIRED");
    }

    public ProjectEntry create(AppUser actor, AppUser subject, Input in) {
        if (in.date() == null) throw ApiException.bad("DATE_REQUIRED");
        Project p = checkProject(actor, subject, in.projectId());
        checkActivity(in.activityId());
        locks.assertEditable(actor, subject.id, in.date());
        ProjectEntry e = new ProjectEntry();
        e.userId = subject.id;
        apply(e, in);
        e = repo.save(e);
        audit.data(actor, subject.id, "ProjectEntry", e.id, "CREATE", null, Snapshots.of(e), e.createdAt);
        budget.check(p, e.entryDate);
        return e;
    }

    public ProjectEntry update(AppUser actor, Long id, Input in) {
        ProjectEntry e = repo.findById(id).orElseThrow(ApiException::notFound);
        access.requireSelfOrAdmin(actor, e.userId);
        AppUser subject = access.subject(actor, e.userId);
        locks.assertEditable(actor, e.userId, e.entryDate);
        if (in.date() == null) throw ApiException.bad("DATE_REQUIRED");
        locks.assertEditable(actor, e.userId, in.date());
        if (!in.projectId().equals(e.projectId)) checkProject(actor, subject, in.projectId());
        if (!in.activityId().equals(e.activityId)) checkActivity(in.activityId());
        Map<String, Object> before = Snapshots.of(e);
        apply(e, in);
        e = repo.save(e);
        audit.data(actor, e.userId, "ProjectEntry", e.id, "UPDATE", before, Snapshots.of(e), e.createdAt);
        budget.check(projects.findById(e.projectId).orElse(null), e.entryDate);
        return e;
    }

    public void delete(AppUser actor, Long id) {
        ProjectEntry e = repo.findById(id).orElseThrow(ApiException::notFound);
        access.requireSelfOrAdmin(actor, e.userId);
        locks.assertEditable(actor, e.userId, e.entryDate);
        audit.data(actor, e.userId, "ProjectEntry", e.id, "DELETE", Snapshots.of(e), null, e.createdAt);
        repo.delete(e);
    }

    // ---- project timer ----

    public ProjectEntry startTimer(AppUser u, Long projectId, Long activityId) {
        Project p = checkProject(u, u, projectId);
        checkActivity(activityId);
        LocalDate today = Tz.today();
        locks.assertEditable(u, u.id, today);
        stopTimer(u, false);
        ProjectEntry e = new ProjectEntry();
        e.userId = u.id; e.entryDate = today; e.projectId = projectId; e.activityId = activityId;
        e.startAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        e = repo.save(e);
        audit.data(u, u.id, "ProjectEntry", e.id, "CREATE", null, Snapshots.of(e), e.createdAt);
        return e;
    }

    /** Stops the running project timer (if any) and turns it into an entry. */
    public ProjectEntry stopTimer(AppUser u, boolean required) {
        List<ProjectEntry> running = repo.findByUserIdAndEndAtIsNullAndStartAtIsNotNull(u.id);
        if (running.isEmpty()) {
            if (required) throw ApiException.conflict("NO_RUNNING_TIMER");
            return null;
        }
        ProjectEntry e = running.get(0);
        locks.assertEditable(u, u.id, e.entryDate);
        Map<String, Object> before = Snapshots.of(e);
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        e.endAt = now.isAfter(e.startAt) ? now : e.startAt.plusSeconds(60);
        e.minutes = (int) Math.max(1, Duration.between(e.startAt, e.endAt).toMinutes());
        e = repo.save(e);
        audit.data(u, u.id, "ProjectEntry", e.id, "UPDATE", before, Snapshots.of(e), e.createdAt);
        budget.check(projects.findById(e.projectId).orElse(null), e.entryDate);
        return e;
    }

    // ---- week grid ----

    /** Sets the total of one grid cell (project x activity x day). 0 removes the entry. */
    public void setCell(AppUser actor, AppUser subject, LocalDate date, Long projectId, Long activityId, int minutes) {
        List<ProjectEntry> cell = repo.findByUserIdAndEntryDateBetweenOrderByEntryDateAscStartAtAscIdAsc(subject.id, date, date)
                .stream().filter(e -> e.projectId.equals(projectId) && e.activityId.equals(activityId) && !e.running()).toList();
        if (cell.size() > 1) throw ApiException.conflict("CELL_MULTIPLE");
        if (cell.isEmpty()) {
            if (minutes > 0) create(actor, subject, new Input(date, projectId, activityId, null, null, minutes, null));
        } else if (minutes == 0) {
            delete(actor, cell.get(0).id);
        } else {
            ProjectEntry e = cell.get(0);
            update(actor, e.id, new Input(date, projectId, activityId, null, null, minutes, e.description));
        }
    }

    /** Copies entries of one day (or one week, shifted by 7 days) to another (B-09). Durations only, no clock times. */
    public int copy(AppUser actor, AppUser subject, LocalDate from, LocalDate to, boolean week) {
        int days = week ? 7 : 1;
        int n = 0;
        for (int i = 0; i < days; i++) {
            LocalDate src = from.plusDays(i), dst = to.plusDays(i);
            for (ProjectEntry e : repo.findByUserIdAndEntryDateBetweenOrderByEntryDateAscStartAtAscIdAsc(subject.id, src, src)) {
                if (e.running()) continue;
                try {
                    create(actor, subject, new Input(dst, e.projectId, e.activityId, null, null, e.minutes, e.description));
                    n++;
                } catch (ApiException skip) {
                    if (!skip.code.startsWith("PROJECT_") && !skip.code.equals("ACTIVITY_INACTIVE")) throw skip;
                }
            }
        }
        return n;
    }
}
