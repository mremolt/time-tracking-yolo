package de.tantive.timetracking.web;

import de.tantive.timetracking.config.*;
import de.tantive.timetracking.domain.*;
import de.tantive.timetracking.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

/** Personal settings (A-07), notifications and own data export (H-03). */
@RestController
@RequestMapping("/api/me")
public class MeController {
    public record Settings(String language, Long defaultProjectId, String preferredView, Boolean notifyBudget) {}
    public record Favorites(Set<Long> projectIds) {}

    private final Access access;
    private final Repos.Users users;
    private final Repos.Notifications notifications;
    private final ReportService reports;
    private final ExportResponses responses;

    public MeController(Access a, Repos.Users u, Repos.Notifications n, ReportService r, ExportResponses e) {
        access = a; users = u; notifications = n; reports = r; responses = e;
    }

    @PutMapping("/settings")
    public Dtos.UserDto settings(@RequestBody Settings in) {
        AppUser me = access.me();
        if (in.language() != null) {
            if (!List.of("de", "en").contains(in.language())) throw ApiException.bad("VALIDATION");
            me.language = in.language();
        }
        me.defaultProjectId = in.defaultProjectId();
        if (in.preferredView() != null) {
            if (!List.of("day", "week").contains(in.preferredView())) throw ApiException.bad("VALIDATION");
            me.preferredView = in.preferredView();
        }
        if (in.notifyBudget() != null) me.notifyBudget = in.notifyBudget();
        return Dtos.UserDto.of(users.save(me));
    }

    @PutMapping("/favorites")
    public Set<Long> favorites(@RequestBody Favorites in) {
        AppUser me = access.me();
        me.favoriteProjectIds.clear();
        me.favoriteProjectIds.addAll(in.projectIds());
        users.save(me);
        return me.favoriteProjectIds;
    }

    @GetMapping("/favorites")
    public Set<Long> favorites() { return access.me().favoriteProjectIds; }

    @GetMapping("/notifications")
    public List<AppNotification> notifications() { return notifications.findByUserIdOrderByIdDesc(access.me().id).stream().limit(50).toList(); }

    @PostMapping("/notifications/read")
    public void read() {
        for (AppNotification n : notifications.findByUserIdOrderByIdDesc(access.me().id)) if (!n.read) { n.read = true; notifications.save(n); }
    }

    /** Employees download all of their own records. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam LocalDate from, @RequestParam LocalDate to, @RequestParam String format) {
        AppUser me = access.me();
        return responses.of(reports.ownData(me, from, to, me.language), format, "my-data");
    }
}
