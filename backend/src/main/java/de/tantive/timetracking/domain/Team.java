package de.tantive.timetracking.domain;

import jakarta.persistence.*;

@Entity
public class Team {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(unique = true, nullable = false) public String name;
}
