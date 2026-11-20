package de.tantive.timetracking.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/** Legal working time of one day: start, end and breaks (B-18, D-01). */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "workDate"}))
public class WorkDay {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public Long userId;
    @Column(nullable = false) public LocalDate workDate;
    @Column(nullable = false) public Instant startAt;
    public Instant endAt;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "work_day_id")
    @OrderBy("startAt")
    public List<WorkBreak> breaks = new ArrayList<>();
    public Instant createdAt = Instant.now();

    public long netMinutes(Instant now) {
        Instant end = endAt != null ? endAt : now;
        long total = Math.max(0, java.time.Duration.between(startAt, end).toMinutes());
        for (WorkBreak b : breaks) total -= b.minutes(end);
        return Math.max(0, total);
    }
    public long breakMinutes(Instant now) {
        Instant end = endAt != null ? endAt : now;
        long t = 0;
        for (WorkBreak b : breaks) t += b.minutes(end);
        return t;
    }
}
