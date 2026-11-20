package de.tantive.timetracking.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.YearMonth;

/** A locked calendar month, for everybody (userId == null) or one user (C-01). */
@Entity
public class PeriodLock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public int lockYear;
    @Column(nullable = false) public int lockMonth;
    public Long userId;
    public boolean active = true;
    public Instant lockedAt = Instant.now();
    public Long lockedBy;
    public Instant unlockedAt;

    public YearMonth month() { return YearMonth.of(lockYear, lockMonth); }
}
