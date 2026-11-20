package de.tantive.timetracking.config;

import de.tantive.timetracking.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.util.*;

/**
 * The one central permission check (H-05). Everything that depends on who may see or change what
 * (D-10 matrix) goes through here.
 */
@Component
public class Access {
    private final Repos.Users users;
    private final Repos.Projects projects;

    public Access(Repos.Users users, Repos.Projects projects) { this.users = users; this.projects = projects; }

    public AppUser me() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppPrincipal p))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        return users.findById(p.userId()).filter(u -> u.active)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED"));
    }

    public AppUser requireAdmin() {
        AppUser u = me();
        if (!u.isAdmin()) throw ApiException.forbidden();
        return u;
    }

    public AppUser requireAny(Role... roles) {
        AppUser u = me();
        for (Role r : roles) if (u.has(r)) return u;
        throw ApiException.forbidden();
    }

    /** Own data, or any user's data for backoffice admins. Project leads/management never get working time of others. */
    public void requireSelfOrAdmin(AppUser viewer, Long subjectUserId) {
        if (!viewer.id.equals(subjectUserId) && !viewer.isAdmin()) throw ApiException.forbidden();
    }

    /** The subject of a time-data operation: the caller, or (admin only) someone else. */
    public AppUser subject(AppUser actor, Long userId) {
        if (userId == null || userId.equals(actor.id)) return actor;
        if (!actor.isAdmin()) throw ApiException.forbidden();
        return users.findById(userId).orElseThrow(ApiException::notFound);
    }

    public boolean leadsProject(AppUser u, Project p) {
        return p.lead != null && p.lead.id.equals(u.id);
    }

    public Set<Long> ledProjectIds(AppUser u) {
        Set<Long> ids = new HashSet<>();
        if (u.has(Role.PROJECT_LEAD))
            for (Project p : projects.findAll()) if (leadsProject(u, p)) ids.add(p.id);
        return ids;
    }

    /** Employees may book on assigned projects and projects open to everyone; archived ones never (B-05). */
    public boolean canBook(AppUser u, Project p) {
        if (p.archived) return false;
        return p.openToAll || p.memberIds.contains(u.id);
    }

    /**
     * Visibility of one project-time row (D-10): per-person detail is visible for own rows, to the lead of
     * the row's project, and to admins. Management never sees per-person data of others.
     */
    public boolean personVisible(AppUser viewer, Long rowUserId, Long rowProjectId, Set<Long> ledIds) {
        if (viewer.isAdmin()) return true;
        if (viewer.id.equals(rowUserId)) return true;
        return ledIds.contains(rowProjectId);
    }

    /** Visibility of a row for aggregation (sums without person): admin and management see everything. */
    public boolean aggregateVisible(AppUser viewer, Long rowUserId, Long rowProjectId, Set<Long> ledIds) {
        if (viewer.isAdmin() || viewer.has(Role.MANAGEMENT)) return true;
        return personVisible(viewer, rowUserId, rowProjectId, ledIds);
    }
}
