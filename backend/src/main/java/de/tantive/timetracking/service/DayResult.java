package de.tantive.timetracking.service;

import de.tantive.timetracking.domain.Absence;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DayResult(LocalDate date, int targetMinutes, int baseTargetMinutes, long workMinutes, long breakMinutes,
                        long projectMinutes, String holidayDe, String holidayEn, Absence.Type absence, boolean absenceHalf,
                        Instant start, Instant end, boolean running, boolean hasWorkDay,
                        List<Msg> warnings, List<Msg> hints) {}
