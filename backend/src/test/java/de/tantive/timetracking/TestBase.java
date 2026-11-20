package de.tantive.timetracking;

import de.tantive.timetracking.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** Seeds a small company for every test (rolled back after each test). */
@Transactional
@org.springframework.test.context.ActiveProfiles("test")
public abstract class TestBase {
    public static final String PW = "Test-Passw0rd!";
    @Autowired protected Repos.Users users;
    @Autowired protected Repos.Teams teams;
    @Autowired protected Repos.Projects projects;
    @Autowired protected Repos.Activities activities;
    @Autowired protected Repos.Schedules schedules;
    @Autowired protected Repos.Holidays holidays;
    @Autowired protected PasswordEncoder encoder;

    protected AppUser admin, alice, bob, carol, dave;
    protected Project p1, p2, p3;
    protected Activity dev;

    protected AppUser user(String email, String first, Role... roles) {
        AppUser u = new AppUser();
        u.email = email; u.firstName = first; u.lastName = "Test"; u.passwordHash = encoder.encode(PW);
        u.roles.addAll(Arrays.asList(roles));
        return users.save(u);
    }

    protected TargetSchedule schedule(AppUser u, LocalDate from, int hours, String days, String state) {
        TargetSchedule s = new TargetSchedule();
        s.userId = u.id; s.validFrom = from; s.weeklyMinutes = hours * 60; s.workingDays = days; s.federalState = state;
        return schedules.save(s);
    }

    protected void seed() {
        admin = user("t-admin@x.de", "Ada", Role.BACKOFFICE_ADMIN);
        alice = user("t-alice@x.de", "Alice", Role.EMPLOYEE);
        bob = user("t-bob@x.de", "Bob", Role.EMPLOYEE);
        carol = user("t-carol@x.de", "Carol", Role.EMPLOYEE, Role.PROJECT_LEAD);
        dave = user("t-dave@x.de", "Dave", Role.MANAGEMENT);
        dev = new Activity(); dev.name = "T-Dev-" + System.nanoTime(); activities.save(dev);
        p1 = project("T1", carol, false, alice, bob);
        p2 = project("T2", null, false, alice, bob);
        p3 = project("T3", null, true);
        for (AppUser u : List.of(alice, bob, carol)) schedule(u, LocalDate.of(2026, 1, 1), 40, "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY", "BE");
    }

    protected Project project(String code, AppUser lead, boolean open, AppUser... members) {
        Project p = new Project();
        p.code = code + "-" + System.nanoTime(); p.name = "Project " + code; p.lead = lead; p.openToAll = open;
        for (AppUser m : members) p.memberIds.add(m.id);
        return projects.save(p);
    }
}
