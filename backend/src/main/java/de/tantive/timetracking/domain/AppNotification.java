package de.tantive.timetracking.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class AppNotification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long userId;
    /** message key + params, translated in the client */
    public String messageKey;
    public String params;
    public Instant createdAt = Instant.now();
    public boolean read;
}
