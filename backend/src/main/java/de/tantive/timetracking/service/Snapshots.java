package de.tantive.timetracking.service;

import de.tantive.timetracking.config.Tz;
import de.tantive.timetracking.domain.*;
import java.util.*;

/** Plain-map snapshots of entities for the audit trail (never contains secrets). */
public final class Snapshots {
    private Snapshots() {}

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> r = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) r.put((String) kv[i], kv[i + 1] == null ? null : String.valueOf(kv[i + 1]));
        return r;
    }

    public static Map<String, Object> of(WorkDay w) {
        Map<String, Object> r = m("date", w.workDate, "start", Tz.local(w.startAt), "end", Tz.local(w.endAt));
        List<String> br = new ArrayList<>();
        for (WorkBreak b : w.breaks) br.add(Tz.local(b.startAt) + ".." + Tz.local(b.endAt));
        r.put("breaks", br);
        return r;
    }
    public static Map<String, Object> of(ProjectEntry e) {
        return m("date", e.entryDate, "projectId", e.projectId, "activityId", e.activityId,
                "start", Tz.local(e.startAt), "end", Tz.local(e.endAt), "minutes", e.minutes, "description", e.description);
    }
    public static Map<String, Object> of(Absence a) {
        return m("date", a.absenceDate, "type", a.type, "halfDay", a.halfDay);
    }
    public static Map<String, Object> of(AppUser u) {
        return m("email", u.email, "firstName", u.firstName, "lastName", u.lastName, "personnelNumber", u.personnelNumber,
                "active", u.active, "roles", new TreeSet<>(u.roles), "team", u.team == null ? null : u.team.name,
                "entryDate", u.entryDate, "exitDate", u.exitDate, "openingBalanceMinutes", u.openingBalanceMinutes,
                "openingBalanceDate", u.openingBalanceDate);
    }
    public static Map<String, Object> of(Project p) {
        return m("code", p.code, "name", p.name, "description", p.description, "lead", p.lead == null ? null : p.lead.email,
                "startDate", p.startDate, "endDate", p.endDate, "archived", p.archived, "openToAll", p.openToAll,
                "budgetMinutes", p.budgetMinutes, "budgetPeriod", p.budgetPeriod, "members", new TreeSet<>(p.memberIds));
    }
    public static Map<String, Object> of(Team t) { return m("name", t.name); }
    public static Map<String, Object> of(Activity a) { return m("name", a.name, "active", a.active); }
    public static Map<String, Object> of(TargetSchedule s) {
        return m("userId", s.userId, "validFrom", s.validFrom, "weeklyMinutes", s.weeklyMinutes,
                "workingDays", s.workingDays, "federalState", s.federalState);
    }
    public static Map<String, Object> of(PeriodLock l) {
        return m("month", l.month(), "userId", l.userId, "active", l.active);
    }
    public static Map<String, Object> of(Holiday h) {
        return m("date", h.date, "federalState", h.federalState, "nameDe", h.nameDe, "nameEn", h.nameEn);
    }
}
