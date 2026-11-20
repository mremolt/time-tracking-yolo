package de.tantive.timetracking.web;

import de.tantive.timetracking.config.*;
import de.tantive.timetracking.domain.*;
import de.tantive.timetracking.service.HolidayCalendar;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Selection lists for the recording screens (B-05, B-10, B-12). */
@RestController
@RequestMapping("/api/lookups")
public class LookupController {
    public record BookableProject(Long id, String code, String name, boolean favorite, boolean recent) {}
    public record Simple(Long id, String name) {}

    private final Access access;
    private final Repos.Projects projects;
    private final Repos.Activities activities;
    private final Repos.Teams teams;
    private final Repos.Entries entries;
    private final Repos.Users users;

    public LookupController(Access a, Repos.Projects p, Repos.Activities ac, Repos.Teams t, Repos.Entries e, Repos.Users u) {
        access = a; projects = p; activities = ac; teams = t; entries = e; users = u;
    }

    /** Only assigned projects and projects open to everyone; favourites and recently used first. */
    @GetMapping("/projects")
    public List<BookableProject> bookable(@RequestParam(required = false) Long userId) {
        AppUser me = access.me();
        AppUser subject = access.subject(me, userId);
        Set<Long> recent = new LinkedHashSet<>();
        for (ProjectEntry e : entries.recentForUser(subject.id, PageRequest.of(0, 40))) { recent.add(e.projectId); if (recent.size() >= 5) break; }
        List<BookableProject> out = new ArrayList<>();
        for (Project p : projects.findAll()) {
            if (p.archived || !(me.isAdmin() && userId != null ? true : access.canBook(subject, p))) continue;
            out.add(new BookableProject(p.id, p.code, p.name, subject.favoriteProjectIds.contains(p.id), recent.contains(p.id)));
        }
        out.sort(Comparator.comparing((BookableProject b) -> !b.favorite()).thenComparing(b -> !b.recent()).thenComparing(BookableProject::code));
        return out;
    }

    @GetMapping("/activities")
    public List<Simple> activities() {
        access.me();
        return activities.findAll().stream().filter(a -> a.active).sorted(Comparator.comparing(a -> a.name)).map(a -> new Simple(a.id, a.name)).toList();
    }

    @GetMapping("/teams")
    public List<Simple> teams() {
        access.me();
        return teams.findAll().stream().sorted(Comparator.comparing(t -> t.name)).map(t -> new Simple(t.id, t.name)).toList();
    }

    /** User list for filters: backoffice only. */
    @GetMapping("/users")
    public List<Simple> users() {
        access.requireAdmin();
        return users.findAll().stream().sorted(Comparator.comparing((AppUser u) -> u.lastName).thenComparing(u -> u.firstName))
                .map(u -> new Simple(u.id, u.fullName() + (u.active ? "" : " (inactive)"))).toList();
    }

    /** Projects usable in report filters: everything for admin/management, own led projects for leads, own booked projects otherwise. */
    @GetMapping("/report-projects")
    public List<Simple> reportProjects() {
        AppUser me = access.me();
        Set<Long> led = access.ledProjectIds(me);
        return projects.findAll().stream()
                .filter(p -> me.isAdmin() || me.has(Role.MANAGEMENT) || led.contains(p.id) || p.memberIds.contains(me.id))
                .sorted(Comparator.comparing(p -> p.code)).map(p -> new Simple(p.id, p.code + " " + p.name)).toList();
    }

    @GetMapping("/states")
    public List<String> states() { return HolidayCalendar.STATES; }
}
