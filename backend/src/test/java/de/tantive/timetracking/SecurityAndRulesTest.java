package de.tantive.timetracking;

import com.fasterxml.jackson.databind.*;
import de.tantive.timetracking.domain.*;
import de.tantive.timetracking.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityAndRulesTest extends TestBase {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EntryService entries;
    @Autowired WorkTimeService work;
    @Autowired AdminService adminService;
    @Autowired Repos.Audits audits;
    @Autowired Repos.Mails mails;
    @Autowired Repos.Entries entryRepo;

    @BeforeEach void setUp() { seed(); }

    MockHttpSession login(AppUser u) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + u.email + "\",\"password\":\"" + PW + "\"}")).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession();
    }

    JsonNode getJson(MockHttpSession s, String url) throws Exception {
        return json.readTree(mvc.perform(get(url).session(s)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    void enter(AppUser u, Project p, String date, int minutes) {
        entries.create(admin, u, new EntryService.Input(LocalDate.parse(date), p.id, dev.id, null, null, minutes, null));
    }

    // ---- login & access ----
    @Test void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test void wrongPasswordLocksAccountAfterFiveAttempts() throws Exception {
        for (int i = 0; i < 5; i++)
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + alice.email + "\",\"password\":\"wrong\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + alice.email + "\",\"password\":\"" + PW + "\"}")).andExpect(status().isLocked());
    }

    @Test void deactivatedUserIsLockedOutImmediately() throws Exception {
        MockHttpSession s = login(alice);
        mvc.perform(get("/api/auth/me").session(s)).andExpect(status().isOk());
        adminService.setActive(admin, alice.id, false);
        mvc.perform(get("/api/auth/me").session(s)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + alice.email + "\",\"password\":\"" + PW + "\"}")).andExpect(status().isUnauthorized());
    }

    @Test void adminEndpointsOnlyForBackoffice() throws Exception {
        for (AppUser u : List.of(alice, carol, dave))
            mvc.perform(get("/api/admin/users").session(login(u))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").session(login(admin))).andExpect(status().isOk());
    }

    @Test void stateChangingRequestsNeedCsrfToken() throws Exception {
        MockHttpSession s = login(alice);
        mvc.perform(post("/api/work/start").session(s)).andExpect(status().isForbidden());
        mvc.perform(post("/api/work/start").session(s).with(csrf())).andExpect(status().isOk());
    }

    @Test void invitationAndPasswordPolicy() throws Exception {
        AppUser n = adminService.createUser(admin, new AdminService.UserIn("new@x.de", "Nina", "New", null, Set.of(Role.EMPLOYEE), null, null, null, null, null, true));
        assertNotNull(n.inviteToken);
        assertTrue(mails.findAllByOrderByIdDesc().stream().anyMatch(m -> m.recipient.equals("new@x.de") && m.body.contains(n.inviteToken)));
        mvc.perform(post("/api/auth/accept-invite").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + n.inviteToken + "\",\"password\":\"short\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/accept-invite").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + n.inviteToken + "\",\"password\":\"Good-Passw0rd-1\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/auth/accept-invite").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + n.inviteToken + "\",\"password\":\"Good-Passw0rd-2\"}")).andExpect(status().isBadRequest());
    }

    // ---- visibility D-10 ----
    @Test void visibilityPerRoleInProjectReport() throws Exception {
        enter(alice, p1, "2026-02-02", 120);   // on carol's project
        enter(bob, p2, "2026-02-02", 60);      // on a project nobody leads
        String q = "?from=2026-02-01&to=2026-02-28&detail=true&format=json";
        // employee: only own rows
        JsonNode a = getJson(login(alice), "/api/reports/project" + q);
        assertEquals(1, a.get("rows").size());
        assertEquals("Alice Test", a.get("rows").get(0).get(1).asText());
        // project lead: per-person rows only on the project she leads
        JsonNode c = getJson(login(carol), "/api/reports/project" + q);
        assertEquals(1, c.get("rows").size());
        assertEquals("Alice Test", c.get("rows").get(0).get(1).asText());
        // management: no per-person data of others at all (detail & user grouping), but aggregates over everything
        MockHttpSession d = login(dave);
        assertEquals(0, getJson(d, "/api/reports/project" + q).get("rows").size());
        assertEquals(0, getJson(d, "/api/reports/project?from=2026-02-01&to=2026-02-28&groupBy=USER").get("rows").size());
        JsonNode agg = getJson(d, "/api/reports/project?from=2026-02-01&to=2026-02-28&groupBy=PROJECT");
        assertEquals(2, agg.get("rows").size());
        assertEquals("3,00", agg.get("totals").get(1).asText().replace('.', ','));
        // backoffice: everything
        assertEquals(2, getJson(login(admin), "/api/reports/project" + q).get("rows").size());
    }

    @Test void workingTimeReportsOnlyForOwnerAndBackoffice() throws Exception {
        String url = "/api/reports/user?from=2026-02-01&to=2026-02-28&userId=" + alice.id;
        mvc.perform(get(url).session(login(bob))).andExpect(status().isForbidden());
        mvc.perform(get(url).session(login(carol))).andExpect(status().isForbidden()); // lead: no working time of others
        mvc.perform(get(url).session(login(dave))).andExpect(status().isForbidden());
        mvc.perform(get(url).session(login(alice))).andExpect(status().isOk());
        mvc.perform(get(url).session(login(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/reports/overtime").session(login(carol))).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/completeness?from=2026-02-01&to=2026-02-28").session(login(dave))).andExpect(status().isForbidden());
        mvc.perform(get("/api/exports/payroll?month=2026-02").session(login(dave))).andExpect(status().isForbidden());
    }

    @Test void managementDashboardHasNoPerPersonData() throws Exception {
        enter(alice, p1, "2026-02-02", 120);
        String body = mvc.perform(get("/api/dashboard?from=2026-02-01&to=2026-02-28").session(login(dave)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("Alice"));
        assertFalse(body.contains("t-alice"));
        mvc.perform(get("/api/dashboard?from=2026-02-01&to=2026-02-28").session(login(alice))).andExpect(status().isForbidden());
    }

    @Test void employeeCannotReadOrChangeOthersData() throws Exception {
        mvc.perform(get("/api/days?from=2026-02-02&to=2026-02-06&userId=" + bob.id).session(login(alice))).andExpect(status().isForbidden());
        enter(bob, p2, "2026-02-02", 60);
        Long id = entryRepo.findAll().stream().filter(e -> e.userId.equals(bob.id)).findFirst().get().id;
        mvc.perform(delete("/api/entries/" + id).session(login(alice)).with(csrf())).andExpect(status().isForbidden());
    }

    @Test void onlyAssignedAndOpenProjectsCanBeBooked() {
        Project other = project("T9", null, false); // alice is not a member
        assertThrows(de.tantive.timetracking.config.ApiException.class, () -> entries.create(alice, alice,
                new EntryService.Input(LocalDate.of(2026, 2, 2), other.id, dev.id, null, null, 60, null)));
        entries.create(alice, alice, new EntryService.Input(LocalDate.of(2026, 2, 2), p3.id, dev.id, null, null, 60, null)); // open to everyone
        adminService.setArchived(admin, p1.id, true);
        assertThrows(de.tantive.timetracking.config.ApiException.class, () -> entries.create(alice, alice,
                new EntryService.Input(LocalDate.of(2026, 2, 2), p1.id, dev.id, null, null, 60, null)));
    }

    // ---- locking ----
    @Test void lockedPeriodBlocksEmployeesButNotAdminCorrections() {
        enter(alice, p1, "2026-02-02", 60);
        Long id = entryRepo.findAll().stream().filter(e -> e.userId.equals(alice.id)).findFirst().get().id;
        adminService.lock(admin, 2026, 2, null);
        var in = new EntryService.Input(LocalDate.of(2026, 2, 2), p1.id, dev.id, null, null, 90, "fix");
        var ex = assertThrows(de.tantive.timetracking.config.ApiException.class, () -> entries.update(alice, id, in));
        assertEquals("PERIOD_LOCKED", ex.code);
        assertThrows(de.tantive.timetracking.config.ApiException.class, () -> entries.create(alice, alice,
                new EntryService.Input(LocalDate.of(2026, 2, 3), p1.id, dev.id, null, null, 30, null)));
        assertThrows(de.tantive.timetracking.config.ApiException.class, () -> entries.delete(alice, id));
        entries.update(admin, id, in); // admin correction allowed ...
        var log = audits.findByEntityTypeAndEntityIdOrderByIdDesc("ProjectEntry", id);
        assertEquals("UPDATE", log.get(0).action); // ... and fully logged
        assertEquals(admin.id, log.get(0).actorId);
        assertTrue(log.get(0).oldValue.contains("60") && log.get(0).newValue.contains("90"));
        assertTrue(audits.findByKindOrderByIdDesc(AuditLog.Kind.ADMIN, org.springframework.data.domain.PageRequest.of(0, 20)).stream().anyMatch(a -> a.action.equals("LOCK")));
        // unlocking lets the employee edit again
        Long lockId = null;
        for (PeriodLock l : lockRepoAll()) if (l.active) lockId = l.id;
        adminService.unlock(admin, lockId);
        entries.update(alice, id, in);
    }

    @Autowired Repos.Locks lockRepo;
    List<PeriodLock> lockRepoAll() { return lockRepo.findAll(); }

    // ---- audit ----
    @Test void everyChangeOfTimeDataIsAudited() {
        enter(alice, p1, "2026-02-02", 60);
        ProjectEntry e = entryRepo.findAll().stream().filter(x -> x.userId.equals(alice.id)).findFirst().get();
        entries.update(alice, e.id, new EntryService.Input(e.entryDate, p1.id, dev.id, null, null, 75, "x"));
        entries.delete(alice, e.id);
        var actions = audits.findByEntityTypeAndEntityIdOrderByIdDesc("ProjectEntry", e.id).stream().map(a -> a.action).toList();
        assertEquals(List.of("DELETE", "UPDATE", "CREATE"), actions);
        assertNotNull(audits.findByEntityTypeAndEntityIdOrderByIdDesc("ProjectEntry", e.id).get(2).entityCreatedAt);
    }

    // ---- budgets ----
    @Test void budgetWarningsAreSentExactlyOncePerThreshold() {
        p1.budgetMinutes = 600; p1.budgetPeriod = Project.BudgetPeriod.TOTAL; projects.save(p1);
        long before = mails.count();
        enter(alice, p1, "2026-02-02", 300);                 // 50 %
        assertEquals(before, mails.count());
        enter(alice, p1, "2026-02-03", 200);                 // 83 % -> 80 % warning
        long afterFirst = mails.count();
        assertTrue(afterFirst > before);
        enter(alice, p1, "2026-02-04", 10);                  // 85 %, no new warning
        assertEquals(afterFirst, mails.count());
        enter(alice, p1, "2026-02-05", 100);                 // 100 %+
        long afterSecond = mails.count();
        assertEquals(afterFirst - before, afterSecond - afterFirst, "same recipients for the 100 % warning");
        enter(alice, p1, "2026-02-06", 100);                 // still above, nothing new
        assertEquals(afterSecond, mails.count());
        // recipients: project lead + backoffice
        var recipients = mails.findAllByOrderByIdDesc().stream().filter(m -> m.subject.contains("udget")).map(m -> m.recipient).collect(java.util.stream.Collectors.toSet());
        assertTrue(recipients.contains(carol.email) && recipients.contains(admin.email));
        assertFalse(recipients.contains(alice.email));
    }

    // ---- exports ----
    @Test void exportsWorkInAllFormats() throws Exception {
        enter(alice, p1, "2026-02-02", 90);
        MockHttpSession s = login(admin);
        for (String f : List.of("csv", "xlsx", "pdf")) {
            byte[] body = mvc.perform(get("/api/reports/project?from=2026-02-01&to=2026-02-28&format=" + f + "&lang=en").session(s))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            assertTrue(body.length > 40, f);
        }
        String csv = mvc.perform(get("/api/exports/payroll?month=2026-02").session(s)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(csv.contains("PersonnelNumber;LastName;FirstName;Month;ContractHours"));
        mvc.perform(get("/api/reports/budget?format=pdf").session(s)).andExpect(status().isBadRequest());
    }

    @Test void csvExportNeutralisesFormulas() {
        String csv = new String(new Exporter().csv(new Table("t", List.of("a"), List.of(List.of("=1+1"), List.of("-1,50")))), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.contains("'=1+1"));
        assertTrue(csv.contains("-1,50") && !csv.contains("'-1,50"));
    }

    // ---- import ----
    @Test void importIsAllOrNothingAndReportsErrors(@Autowired ImportService importer) {
        var rows = List.of(List.of("email", "firstName", "lastName", "team", "roles", "weeklyHours"),
                List.of("i1@x.de", "I", "One", "TeamI", "EMPLOYEE", "40"),
                List.of("t-alice@x.de", "Dup", "Licate", "", "", "40"),
                List.of("i3@x.de", "I", "Three", "", "NOPE", "40"));
        var res = importer.importUsers(admin, rows);
        assertEquals(0, res.imported());
        assertEquals(List.of(3, 4), res.errors().stream().map(ImportService.RowError::line).toList());
    }
}
