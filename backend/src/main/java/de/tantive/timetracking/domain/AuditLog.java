package de.tantive.timetracking.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Change history for time data (kind DATA, H-01) and admin action log (kind ADMIN, H-02). */
@Entity
@Table(indexes = {@Index(columnList = "entityType,entityId"), @Index(columnList = "kind,at")})
public class AuditLog {
    public enum Kind { DATA, ADMIN }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public Instant at = Instant.now();
    @Enumerated(EnumType.STRING) @Column(nullable = false) public Kind kind;
    public Long actorId;
    public String actorName;
    /** the user whose data was changed (for visibility) */
    public Long subjectUserId;
    @Column(nullable = false) public String entityType;
    public Long entityId;
    @Column(nullable = false) public String action;
    @Lob @Column(length = 100000) public String oldValue;
    @Lob @Column(length = 100000) public String newValue;
    /** when the changed object itself was created (R-02) */
    public Instant entityCreatedAt;
}
