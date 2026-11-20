package de.tantive.timetracking.domain;

import jakarta.persistence.*;

/** Marker that a budget warning for (project, period, threshold) was already sent: exactly once. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"projectId", "periodKey", "threshold"}))
public class BudgetWarning {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long projectId;
    public String periodKey;
    public int threshold;
}
