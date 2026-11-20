package de.tantive.timetracking.config;

import de.tantive.timetracking.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.Set;

/** Creates the first admin account and the internal default projects when the database is empty (step 2, E-09). */
@Component
@org.springframework.core.annotation.Order(1)
public class Bootstrap implements ApplicationRunner {
    private final Repos.Users users;
    private final Repos.Projects projects;
    private final Repos.Activities activities;
    private final PasswordEncoder encoder;
    @Value("${app.bootstrap-admin-email}") String email;
    @Value("${app.bootstrap-admin-password}") String password;

    public Bootstrap(Repos.Users u, Repos.Projects p, Repos.Activities a, PasswordEncoder e) { users = u; projects = p; activities = a; encoder = e; }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() == 0) {
            AppUser a = new AppUser();
            a.email = email; a.firstName = "Admin"; a.lastName = "Backoffice";
            a.roles.addAll(Set.of(Role.BACKOFFICE_ADMIN, Role.EMPLOYEE));
            a.passwordHash = encoder.encode(password);
            users.save(a);
        }
        if (activities.count() == 0)
            for (String n : new String[]{"Development", "Meeting", "Training", "Travel", "Administration"}) {
                Activity act = new Activity(); act.name = n; activities.save(act);
            }
        if (projects.count() == 0) {
            String[][] internal = {{"ADM", "Administration"}, {"TRN", "Training"}, {"MTG", "Internal meetings"}};
            for (String[] i : internal) {
                Project p = new Project(); p.code = i[0]; p.name = i[1]; p.openToAll = true; p.internal = true;
                projects.save(p);
            }
        }
    }
}
