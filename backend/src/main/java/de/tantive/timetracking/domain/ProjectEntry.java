package de.tantive.timetracking.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/** Project time entry (B-01). endAt == null with startAt != null means a running project timer. */
@Entity
@Table(indexes = {@Index(columnList = "userId,entryDate"), @Index(columnList = "projectId")})
public class ProjectEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public Long userId;
    @Column(nullable = false) public LocalDate entryDate;
    @Column(nullable = false) public Long projectId;
    @Column(nullable = false) public Long activityId;
    public Instant startAt;
    public Instant endAt;
    public int minutes;
    @Column(length = 1000) public String description;
    public Instant createdAt = Instant.now();

    public boolean running() { return startAt != null && endAt == null; }
}
