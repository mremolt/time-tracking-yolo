package de.tantive.timetracking.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

/** Public holiday. federalState null = nationwide. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"date", "federalState"}))
public class Holiday {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public LocalDate date;
    public String federalState;
    @Column(nullable = false) public String nameDe;
    @Column(nullable = false) public String nameEn;
}
