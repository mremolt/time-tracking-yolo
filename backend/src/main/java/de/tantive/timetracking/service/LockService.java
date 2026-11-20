package de.tantive.timetracking.service;

import de.tantive.timetracking.config.ApiException;
import de.tantive.timetracking.domain.*;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.Map;

/** Period locking (C-01, D-03). Employees cannot change locked data; admins can (C-05, always audited). */
@Service
public class LockService {
    private final Repos.Locks locks;
    private final AuditService audit;

    public LockService(Repos.Locks locks, AuditService audit) { this.locks = locks; this.audit = audit; }

    public boolean isLocked(Long userId, LocalDate date) {
        return locks.findByLockYearAndLockMonthAndActiveTrue(date.getYear(), date.getMonthValue()).stream()
                .anyMatch(l -> l.userId == null || l.userId.equals(userId));
    }

    public void assertEditable(AppUser actor, Long subjectUserId, LocalDate date) {
        if (!actor.isAdmin() && isLocked(subjectUserId, date))
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "PERIOD_LOCKED",
                    Map.of("month", date.getYear() + "-" + String.format("%02d", date.getMonthValue())));
    }
}
