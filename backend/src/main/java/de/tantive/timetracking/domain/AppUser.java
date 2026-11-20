package de.tantive.timetracking.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "app_user")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(unique = true, nullable = false) public String email;
    public String firstName;
    public String lastName;
    public String personnelNumber;
    public String passwordHash;
    public boolean active = true;
    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    public Set<Role> roles = new HashSet<>();
    @ManyToOne public Team team;
    public LocalDate entryDate;
    public LocalDate exitDate;
    public String language = "de";
    public Long defaultProjectId;
    public String preferredView = "day";
    public boolean notifyBudget = true;
    public int failedLogins;
    public Instant lockedUntil;
    /** Opening overtime balance in minutes, counted from openingBalanceDate (U-10). */
    public int openingBalanceMinutes;
    public LocalDate openingBalanceDate;
    @ElementCollection(fetch = FetchType.EAGER)
    public Set<Long> favoriteProjectIds = new HashSet<>();
    @Column(unique = true) public String inviteToken;
    public Instant inviteExpires;
    @Column(unique = true) public String resetToken;
    public Instant resetExpires;

    public String fullName() { return firstName + " " + lastName; }
    public boolean has(Role r) { return roles.contains(r); }
    public boolean isAdmin() { return roles.contains(Role.BACKOFFICE_ADMIN); }
}
