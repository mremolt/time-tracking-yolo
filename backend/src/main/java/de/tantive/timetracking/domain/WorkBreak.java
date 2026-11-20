package de.tantive.timetracking.domain;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;

@Entity
public class WorkBreak {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public Instant startAt;
    public Instant endAt;

    public long minutes(Instant fallbackEnd) {
        Instant e = endAt != null ? endAt : fallbackEnd;
        return Math.max(0, Duration.between(startAt, e).toMinutes());
    }
}
