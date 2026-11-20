package de.tantive.timetracking.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.*;

@Entity
public class Project {
    public enum BudgetPeriod { TOTAL, MONTHLY }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(unique = true, nullable = false) public String code;
    @Column(nullable = false) public String name;
    @Column(length = 2000) public String description;
    @ManyToOne public AppUser lead;
    public LocalDate startDate;
    public LocalDate endDate;
    public boolean archived;
    public boolean openToAll;
    public boolean internal;
    public Integer budgetMinutes;
    @Enumerated(EnumType.STRING) public BudgetPeriod budgetPeriod = BudgetPeriod.TOTAL;
    @ElementCollection(fetch = FetchType.EAGER)
    public Set<Long> memberIds = new HashSet<>();
}
