package de.tantive.timetracking.service;

import de.tantive.timetracking.config.*;
import de.tantive.timetracking.domain.*;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Bulk import of users and projects from CSV or Excel with validation and an error report (U-08). All or nothing. */
@Service
public class ImportService {
    public record RowError(int line, String code, String detail) {}
    public record Result(int imported, List<RowError> errors) {}

    private final AdminService admin;
    private final Repos.Users users;
    private final Repos.Teams teams;
    private final Repos.Projects projects;
    private final Repos.Schedules schedules;

    public ImportService(AdminService a, Repos.Users u, Repos.Teams t, Repos.Projects p, Repos.Schedules s) {
        admin = a; users = u; teams = t; projects = p; schedules = s;
    }

    public static List<List<String>> parse(String filename, byte[] data) throws IOException {
        if (filename != null && filename.toLowerCase().endsWith(".xlsx")) {
            List<List<String>> rows = new ArrayList<>();
            try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(data))) {
                DataFormatter fmt = new DataFormatter(Locale.ROOT);
                for (Row r : wb.getSheetAt(0)) {
                    List<String> cells = new ArrayList<>();
                    for (int i = 0; i < r.getLastCellNum(); i++) {
                        Cell c = r.getCell(i);
                        cells.add(c == null ? "" : (c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)
                                ? c.getLocalDateTimeCellValue().toLocalDate().toString() : fmt.formatCellValue(c).trim()));
                    }
                    rows.add(cells);
                }
            }
            return rows;
        }
        String text = new String(data, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) text = text.substring(1);
        List<List<String>> rows = new ArrayList<>();
        String first = text.lines().findFirst().orElse("");
        char sep = first.chars().filter(c -> c == ';').count() >= first.chars().filter(c -> c == ',').count() ? ';' : ',';
        for (String line : text.split("\\r?\\n")) {
            if (line.isBlank()) continue;
            List<String> cells = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            boolean q = false;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (q) { if (c == '"') { if (i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; } else q = false; } else cur.append(c); }
                else if (c == '"') q = true;
                else if (c == sep) { cells.add(cur.toString().trim()); cur.setLength(0); }
                else cur.append(c);
            }
            cells.add(cur.toString().trim());
            rows.add(cells);
        }
        return rows;
    }

    private static String col(Map<String, Integer> idx, List<String> row, String name) {
        Integer i = idx.get(name.toLowerCase());
        return i == null || i >= row.size() ? "" : row.get(i);
    }

    private static Map<String, Integer> header(List<String> h) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < h.size(); i++) m.put(h.get(i).trim().toLowerCase(), i);
        return m;
    }

    private static LocalDate date(String s) { return s.isBlank() ? null : LocalDate.parse(s.trim()); }
    private static int minutes(String s) { return s.isBlank() ? 0 : Math.round(Float.parseFloat(s.replace(',', '.')) * 60); }

    /** columns: email;firstName;lastName;personnelNumber;team;roles;weeklyHours;workingDays;federalState;entryDate;openingBalanceHours */
    @Transactional
    public Result importUsers(AppUser actor, List<List<String>> rows) {
        List<RowError> errors = new ArrayList<>();
        int n = 0;
        if (rows.isEmpty()) return new Result(0, List.of(new RowError(1, "EMPTY_FILE", "")));
        Map<String, Integer> idx = header(rows.get(0));
        for (String req : List.of("email", "firstname", "lastname"))
            if (!idx.containsKey(req)) return new Result(0, List.of(new RowError(1, "MISSING_COLUMN", req)));
        Set<String> seen = new HashSet<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            int line = i + 1;
            try {
                String email = col(idx, r, "email").toLowerCase();
                if (!seen.add(email)) throw ApiException.conflict("EMAIL_EXISTS");
                Set<Role> roles = new HashSet<>();
                for (String s : col(idx, r, "roles").split("[|,/ ]+")) if (!s.isBlank()) roles.add(Role.valueOf(s.trim().toUpperCase()));
                String teamName = col(idx, r, "team");
                Long teamId = null;
                if (!teamName.isBlank()) {
                    Team t = teams.findByNameIgnoreCase(teamName).orElseGet(() -> admin.saveTeam(actor, null, teamName));
                    teamId = t.id;
                }
                LocalDate entry = date(col(idx, r, "entryDate"));
                AppUser u = admin.createUser(actor, new AdminService.UserIn(email, col(idx, r, "firstName"), col(idx, r, "lastName"),
                        col(idx, r, "personnelNumber"), roles.isEmpty() ? Set.of(Role.EMPLOYEE) : roles, teamId, entry, null,
                        minutes(col(idx, r, "openingBalanceHours")), entry, true));
                int weekly = minutes(col(idx, r, "weeklyHours"));
                if (weekly > 0) {
                    Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
                    String wd = col(idx, r, "workingDays");
                    if (wd.isBlank()) days.addAll(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
                    else for (String s : wd.split("[|,/ ]+")) if (!s.isBlank()) days.add(DayOfWeek.valueOf(s.trim().toUpperCase()));
                    String st = col(idx, r, "federalState").toUpperCase();
                    if (!st.isBlank() && !HolidayCalendar.STATES.contains(st)) throw ApiException.bad("STATE_UNKNOWN");
                    admin.saveSchedule(actor, u.id, entry == null ? LocalDate.of(Tz.today().getYear(), 1, 1) : entry, weekly, days, st.isBlank() ? "BE" : st);
                }
                n++;
            } catch (ApiException e) { errors.add(new RowError(line, e.code, "")); }
            catch (RuntimeException e) { errors.add(new RowError(line, "ROW_INVALID", String.valueOf(e.getMessage()))); }
        }
        return finish(n, errors);
    }

    /** columns: code;name;description;leadEmail;openToAll;budgetHours;budgetPeriod;members */
    @Transactional
    public Result importProjects(AppUser actor, List<List<String>> rows) {
        List<RowError> errors = new ArrayList<>();
        int n = 0;
        if (rows.isEmpty()) return new Result(0, List.of(new RowError(1, "EMPTY_FILE", "")));
        Map<String, Integer> idx = header(rows.get(0));
        for (String req : List.of("code", "name"))
            if (!idx.containsKey(req)) return new Result(0, List.of(new RowError(1, "MISSING_COLUMN", req)));
        for (int i = 1; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            int line = i + 1;
            try {
                Long leadId = null;
                String lead = col(idx, r, "leadEmail");
                if (!lead.isBlank()) leadId = users.findByEmailIgnoreCase(lead).orElseThrow(() -> ApiException.bad("USER_UNKNOWN")).id;
                boolean open = List.of("true", "yes", "ja", "1", "x").contains(col(idx, r, "openToAll").toLowerCase());
                String per = col(idx, r, "budgetPeriod");
                Project p = admin.saveProject(actor, null, new AdminService.ProjectIn(col(idx, r, "code"), col(idx, r, "name"),
                        col(idx, r, "description"), leadId, null, null, open, false, minutes(col(idx, r, "budgetHours")),
                        per.isBlank() ? null : Project.BudgetPeriod.valueOf(per.toUpperCase())));
                Set<Long> members = new HashSet<>();
                for (String m : col(idx, r, "members").split("[|]")) if (!m.isBlank())
                    members.add(users.findByEmailIgnoreCase(m.trim()).orElseThrow(() -> ApiException.bad("USER_UNKNOWN")).id);
                if (!members.isEmpty()) admin.setMembers(actor, p.id, members);
                n++;
            } catch (ApiException e) { errors.add(new RowError(line, e.code, "")); }
            catch (RuntimeException e) { errors.add(new RowError(line, "ROW_INVALID", String.valueOf(e.getMessage()))); }
        }
        return finish(n, errors);
    }

    private Result finish(int n, List<RowError> errors) {
        if (!errors.isEmpty()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return new Result(0, errors);
        }
        return new Result(n, errors);
    }
}
