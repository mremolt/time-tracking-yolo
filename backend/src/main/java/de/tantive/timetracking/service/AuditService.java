package de.tantive.timetracking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tantive.timetracking.domain.*;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class AuditService {
    private final Repos.Audits audits;
    private final ObjectMapper json;

    public AuditService(Repos.Audits audits, ObjectMapper json) { this.audits = audits; this.json = json; }

    /** Records one change with old and new values (either may be null for create / delete). */
    public void log(AuditLog.Kind kind, AppUser actor, Long subjectUserId, String entityType, Long entityId,
                    String action, Map<String, Object> oldV, Map<String, Object> newV, Instant entityCreatedAt) {
        AuditLog a = new AuditLog();
        a.kind = kind;
        a.actorId = actor == null ? null : actor.id;
        a.actorName = actor == null ? "system" : actor.fullName();
        a.subjectUserId = subjectUserId;
        a.entityType = entityType;
        a.entityId = entityId;
        a.action = action;
        a.oldValue = write(oldV);
        a.newValue = write(newV);
        a.entityCreatedAt = entityCreatedAt;
        audits.save(a);
    }

    public void data(AppUser actor, Long subject, String type, Long id, String action,
                     Map<String, Object> o, Map<String, Object> n, Instant created) {
        log(AuditLog.Kind.DATA, actor, subject, type, id, action, o, n, created);
    }

    public void admin(AppUser actor, String type, Long id, String action, Map<String, Object> o, Map<String, Object> n) {
        log(AuditLog.Kind.ADMIN, actor, null, type, id, action, o, n, null);
    }

    private String write(Map<String, Object> v) {
        if (v == null) return null;
        try { return json.writeValueAsString(v); } catch (Exception e) { return String.valueOf(v); }
    }
}
