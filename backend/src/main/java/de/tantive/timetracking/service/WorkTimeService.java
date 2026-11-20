package de.tantive.timetracking.service;

import de.tantive.timetracking.config.*;
import de.tantive.timetracking.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** Legal working time recording: manual entry and the working-day timer (B-18, B-08, B-07). */
@Service
@Transactional
public class WorkTimeService {
    public record BreakIn(LocalDateTime start, LocalDateTime end) {}

    private final Repos.WorkDays repo;
    private final LockService locks;
    private final AuditService audit;
    private final Access access;

    public WorkTimeService(Repos.WorkDays repo, LockService locks, AuditService audit, Access access) {
        this.repo = repo; this.locks = locks; this.audit = audit; this.access = access;
    }

    /** Create or replace the working day of {@code date} (start/end may cross midnight, breaks may be several). */
    public WorkDay save(AppUser actor, AppUser subject, LocalDate date, LocalDateTime start, LocalDateTime end, List<BreakIn> breaks) {
        if (start == null) throw ApiException.bad("START_REQUIRED");
        if (!Tz.dateOf(Tz.toInstant(start)).equals(date)) throw ApiException.bad("START_NOT_ON_DATE");
        if (end != null && !end.isAfter(start)) throw ApiException.bad("END_BEFORE_START");
        locks.assertEditable(actor, subject.id, date);
        Instant s = Tz.toInstant(start);
        Instant e = end == null ? null : Tz.toInstant(end);
        WorkDay w = repo.findByUserIdAndWorkDate(subject.id, date).orElse(null);
        Map<String, Object> before = w == null ? null : Snapshots.of(w);
        if (w == null) { w = new WorkDay(); w.userId = subject.id; w.workDate = date; }
        w.startAt = s;
        w.endAt = e;
        w.breaks.clear();
        List<WorkBreak> sorted = new ArrayList<>();
        for (BreakIn b : breaks == null ? List.<BreakIn>of() : breaks) {
            if (b.start() == null || (b.end() != null && !b.end().isAfter(b.start()))) throw ApiException.bad("BREAK_INVALID");
            WorkBreak wb = new WorkBreak();
            wb.startAt = Tz.toInstant(b.start());
            wb.endAt = b.end() == null ? null : Tz.toInstant(b.end());
            if (wb.startAt.isBefore(s) || (e != null && wb.endAt != null && wb.endAt.isAfter(e)) || (e != null && wb.startAt.isAfter(e)))
                throw ApiException.bad("BREAK_OUTSIDE");
            sorted.add(wb);
        }
        sorted.sort(Comparator.comparing(b -> b.startAt));
        for (int i = 1; i < sorted.size(); i++) {
            WorkBreak p = sorted.get(i - 1);
            if (p.endAt == null || p.endAt.isAfter(sorted.get(i).startAt)) throw ApiException.bad("BREAK_OVERLAP");
        }
        w.breaks.addAll(sorted);
        w = repo.save(w);
        audit.data(actor, subject.id, "WorkDay", w.id, before == null ? "CREATE" : "UPDATE", before, Snapshots.of(w), w.createdAt);
        return w;
    }

    public void delete(AppUser actor, Long id) {
        WorkDay w = repo.findById(id).orElseThrow(ApiException::notFound);
        access.requireSelfOrAdmin(actor, w.userId);
        locks.assertEditable(actor, w.userId, w.workDate);
        audit.data(actor, w.userId, "WorkDay", w.id, "DELETE", Snapshots.of(w), null, w.createdAt);
        repo.delete(w);
    }

    // ---- timer (own working day only) ----

    public WorkDay start(AppUser u) {
        LocalDate today = Tz.today();
        locks.assertEditable(u, u.id, today);
        if (repo.findByUserIdAndWorkDate(u.id, today).isPresent()) throw ApiException.conflict("WORKDAY_EXISTS");
        WorkDay w = new WorkDay();
        w.userId = u.id; w.workDate = today; w.startAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        w = repo.save(w);
        audit.data(u, u.id, "WorkDay", w.id, "CREATE", null, Snapshots.of(w), w.createdAt);
        return w;
    }

    private WorkDay running(AppUser u) {
        // a running day may have started yesterday (work past midnight)
        for (LocalDate d : new LocalDate[]{Tz.today(), Tz.today().minusDays(1)}) {
            WorkDay w = repo.findByUserIdAndWorkDate(u.id, d).orElse(null);
            if (w != null && w.endAt == null) return w;
        }
        throw ApiException.conflict("NO_RUNNING_WORKDAY");
    }

    public WorkDay pause(AppUser u) {
        WorkDay w = running(u);
        locks.assertEditable(u, u.id, w.workDate);
        if (w.breaks.stream().anyMatch(b -> b.endAt == null)) throw ApiException.conflict("ALREADY_PAUSED");
        Map<String, Object> before = Snapshots.of(w);
        WorkBreak b = new WorkBreak();
        b.startAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        w.breaks.add(b);
        w = repo.save(w);
        audit.data(u, u.id, "WorkDay", w.id, "UPDATE", before, Snapshots.of(w), w.createdAt);
        return w;
    }

    public WorkDay resume(AppUser u) {
        WorkDay w = running(u);
        locks.assertEditable(u, u.id, w.workDate);
        WorkBreak open = w.breaks.stream().filter(b -> b.endAt == null).findFirst().orElseThrow(() -> ApiException.conflict("NOT_PAUSED"));
        Map<String, Object> before = Snapshots.of(w);
        open.endAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        w = repo.save(w);
        audit.data(u, u.id, "WorkDay", w.id, "UPDATE", before, Snapshots.of(w), w.createdAt);
        return w;
    }

    public WorkDay stop(AppUser u) {
        WorkDay w = running(u);
        locks.assertEditable(u, u.id, w.workDate);
        Map<String, Object> before = Snapshots.of(w);
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        w.breaks.stream().filter(b -> b.endAt == null).forEach(b -> b.endAt = now);
        w.endAt = now.isAfter(w.startAt) ? now : w.startAt.plusSeconds(60);
        w = repo.save(w);
        audit.data(u, u.id, "WorkDay", w.id, "UPDATE", before, Snapshots.of(w), w.createdAt);
        return w;
    }
}
