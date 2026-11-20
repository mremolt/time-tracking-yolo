package de.tantive.timetracking.web;

import de.tantive.timetracking.config.*;
import de.tantive.timetracking.domain.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Change history (H-01) and admin action log (H-02). */
@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final Access access;
    private final Repos.Audits audits;

    public AuditController(Access access, Repos.Audits audits) { this.access = access; this.audits = audits; }

    /** History of one object. Admins see everything, employees only the history of their own time data. */
    @GetMapping("/object/{type}/{id}")
    public List<AuditLog> history(@PathVariable String type, @PathVariable Long id) {
        AppUser me = access.me();
        List<AuditLog> all = audits.findByEntityTypeAndEntityIdOrderByIdDesc(type, id);
        if (me.isAdmin()) return all;
        return all.stream().filter(a -> a.kind == AuditLog.Kind.DATA && me.id.equals(a.subjectUserId)).toList();
    }

    @GetMapping("/admin-log")
    public List<AuditLog> adminLog(@RequestParam(defaultValue = "200") int limit) {
        access.requireAdmin();
        return audits.findByKindOrderByIdDesc(AuditLog.Kind.ADMIN, PageRequest.of(0, Math.min(limit, 1000)));
    }

    @GetMapping("/data-log")
    public List<AuditLog> dataLog(@RequestParam(defaultValue = "200") int limit) {
        access.requireAdmin();
        return audits.findByKindOrderByIdDesc(AuditLog.Kind.DATA, PageRequest.of(0, Math.min(limit, 1000)));
    }

    @GetMapping("/my-log")
    public List<AuditLog> myLog() {
        AppUser me = access.me();
        return audits.findBySubjectUserIdAndKindOrderByIdDesc(me.id, AuditLog.Kind.DATA).stream().limit(200).toList();
    }
}
