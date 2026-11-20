package de.tantive.timetracking.domain;

import jakarta.persistence.*;

@Entity
public class Activity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(unique = true, nullable = false) public String name;
    public boolean active = true;
}
