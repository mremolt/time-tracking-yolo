package de.tantive.timetracking.domain;

import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/** Contract data valid from a date: weekly hours, working days and federal state (U-04, I-04). */
@Entity
public class TargetSchedule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public Long userId;
    @Column(nullable = false) public LocalDate validFrom;
    public int weeklyMinutes;
    /** comma separated DayOfWeek names */
    public String workingDays = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY";
    public String federalState = "BE";

    public Set<DayOfWeek> days() {
        Set<DayOfWeek> s = EnumSet.noneOf(DayOfWeek.class);
        if (workingDays != null && !workingDays.isBlank())
            for (String d : workingDays.split(",")) s.add(DayOfWeek.valueOf(d.trim()));
        return s;
    }
}
