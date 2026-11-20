package de.tantive.timetracking.service;

import de.tantive.timetracking.config.*;
import de.tantive.timetracking.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Map;

/** Simple absence marker: no approval, counts towards target time (B-15, D-05). */
@Service
@Transactional
public class AbsenceService {
    private final Repos.Absences repo;
    private final LockService locks;
    private final AuditService audit;

    public AbsenceService(Repos.Absences repo, LockService locks, AuditService audit) {
        this.repo = repo; this.locks = locks; this.audit = audit;
    }

    public void set(AppUser actor, AppUser subject, LocalDate date, Absence.Type type, boolean half) {
        locks.assertEditable(actor, subject.id, date);
        Absence a = repo.findByUserIdAndAbsenceDate(subject.id, date).orElse(null);
        Map<String, Object> before = a == null ? null : Snapshots.of(a);
        if (a == null) { a = new Absence(); a.userId = subject.id; a.absenceDate = date; }
        a.type = type; a.halfDay = half;
        a = repo.save(a);
        audit.data(actor, subject.id, "Absence", a.id, before == null ? "CREATE" : "UPDATE", before, Snapshots.of(a), null);
    }

    public void clear(AppUser actor, AppUser subject, LocalDate date) {
        Absence a = repo.findByUserIdAndAbsenceDate(subject.id, date).orElse(null);
        if (a == null) return;
        locks.assertEditable(actor, subject.id, date);
        audit.data(actor, subject.id, "Absence", a.id, "DELETE", Snapshots.of(a), null, null);
        repo.delete(a);
    }
}
