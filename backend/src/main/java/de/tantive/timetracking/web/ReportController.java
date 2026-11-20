package de.tantive.timetracking.web;

import de.tantive.timetracking.config.*;
import de.tantive.timetracking.domain.AppUser;
import de.tantive.timetracking.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;

/** Reports and exports. Visibility is enforced in {@link ReportService} (D-10), never in the UI only. */
@RestController
@RequestMapping("/api")
public class ReportController {
    private final Access access;
    private final ReportService reports;
    private final DashboardService dashboard;
    private final ExportResponses responses;

    public ReportController(Access a, ReportService r, DashboardService d, ExportResponses e) { access = a; reports = r; dashboard = d; responses = e; }

    private ResponseEntity<?> out(Table t, String format, String name, boolean pdfAllowed) {
        if (format == null || format.equals("json")) return ResponseEntity.ok(t);
        if (format.equals("pdf") && !pdfAllowed) throw ApiException.bad("FORMAT_UNKNOWN");
        return responses.of(t, format, name);
    }

    private String lang(AppUser me, String lang) { return lang == null ? me.language : lang; }

    @GetMapping("/reports/project")
    public ResponseEntity<?> project(@RequestParam LocalDate from, @RequestParam LocalDate to, @RequestParam(required = false) Long teamId,
                                     @RequestParam(required = false) Long projectId, @RequestParam(required = false) Long userId,
                                     @RequestParam(required = false) Long activityId, @RequestParam(required = false) List<ReportService.Group> groupBy,
                                     @RequestParam(defaultValue = "false") boolean detail, @RequestParam(required = false) String format,
                                     @RequestParam(required = false) String lang) {
        AppUser me = access.me();
        Table t = reports.projectReport(me, new ReportService.Filter(from, to, teamId, projectId, userId, activityId),
                groupBy == null ? List.of() : groupBy, detail, lang(me, lang));
        return out(t, format, "project-report", true);
    }

    @GetMapping("/reports/user")
    public ResponseEntity<?> user(@RequestParam LocalDate from, @RequestParam LocalDate to, @RequestParam(required = false) Long userId,
                                  @RequestParam(required = false) String format, @RequestParam(required = false) String lang) {
        AppUser me = access.me();
        return out(reports.userReport(me, userId == null ? me.id : userId, from, to, lang(me, lang)), format, "working-time-record", true);
    }

    @GetMapping("/reports/completeness")
    public ResponseEntity<?> completeness(@RequestParam LocalDate from, @RequestParam LocalDate to, @RequestParam(required = false) Long teamId,
                                          @RequestParam(required = false) String format, @RequestParam(required = false) String lang) {
        AppUser me = access.me();
        return out(reports.completeness(me, from, to, teamId, lang(me, lang)), format, "completeness", false);
    }

    @GetMapping("/reports/budget")
    public ResponseEntity<?> budget(@RequestParam(required = false) String format, @RequestParam(required = false) String lang) {
        AppUser me = access.me();
        return out(reports.budgetReport(me, lang(me, lang)), format, "budget", false);
    }

    @GetMapping("/reports/overtime")
    public ResponseEntity<?> overtime(@RequestParam(required = false) LocalDate asOf, @RequestParam(defaultValue = "false") boolean byTeam,
                                      @RequestParam(required = false) Long teamId, @RequestParam(required = false) String format,
                                      @RequestParam(required = false) String lang) {
        AppUser me = access.me();
        return out(reports.overtimeReport(me, asOf == null ? Tz.today() : asOf, byTeam, teamId, lang(me, lang)), format, "overtime", false);
    }

    @GetMapping("/exports/payroll")
    public ResponseEntity<?> payroll(@RequestParam String month, @RequestParam(required = false) String format) {
        AppUser me = access.me();
        Table t = reports.payroll(me, YearMonth.parse(month));
        return out(t, format == null ? "csv" : format, "payroll-" + month, false);
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@RequestParam LocalDate from, @RequestParam LocalDate to) { return dashboard.dashboard(from, to); }
}
