package de.tantive.timetracking.service;

import java.util.List;

/** Generic, already localised report table used for the screen and for CSV / Excel / PDF export. */
public record Table(String title, List<String> headers, List<List<String>> rows, List<String> totals, List<String> notes) {
    public Table(String title, List<String> headers, List<List<String>> rows) { this(title, headers, rows, List.of(), List.of()); }
}
