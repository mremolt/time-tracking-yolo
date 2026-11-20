package de.tantive.timetracking.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "absenceDate"}))
public class Absence {
    public enum Type { VACATION, SICK, HOLIDAY, OTHER }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public Long userId;
    @Column(nullable = false) public LocalDate absenceDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public Type type;
    public boolean halfDay;
}
