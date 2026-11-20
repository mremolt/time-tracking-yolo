package de.tantive.timetracking.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Captured outgoing email (local installation shows mails instead of delivering them). */
@Entity
public class MailMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Instant sentAt = Instant.now();
    public String recipient;
    public String subject;
    @Lob @Column(length = 20000) public String body;
    public String lang;
}
