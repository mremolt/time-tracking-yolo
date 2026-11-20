package de.tantive.timetracking.service;

import de.tantive.timetracking.domain.*;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

/** Hour budgets and warnings at 80 % and 100 %, each sent exactly once per period (E-07, G-03). */
@Service
public class BudgetService {
    public static final int[] THRESHOLDS = {80, 100};

    private final Repos.Entries entries;
    private final Repos.BudgetWarnings warnings;
    private final Repos.Users users;
    private final Repos.Notifications notifications;
    private final MailService mail;

    public BudgetService(Repos.Entries e, Repos.BudgetWarnings w, Repos.Users u, Repos.Notifications n, MailService m) {
        entries = e; warnings = w; users = u; notifications = n; mail = m;
    }

    public static String periodKey(Project p, LocalDate d) {
        return p.budgetPeriod == Project.BudgetPeriod.MONTHLY ? YearMonth.from(d).toString() : "TOTAL";
    }

    public long usedMinutes(Project p, LocalDate refDate) {
        long sum = 0;
        for (ProjectEntry e : entries.findByProjectId(p.id)) {
            if (p.budgetPeriod == Project.BudgetPeriod.MONTHLY && !YearMonth.from(e.entryDate).equals(YearMonth.from(refDate))) continue;
            sum += e.minutes + (e.running() ? Calc.runningMinutes(e) : 0);
        }
        return sum;
    }

    /** Called after every change of project time. */
    public void check(Project p, LocalDate entryDate) {
        if (p == null || p.budgetMinutes == null || p.budgetMinutes <= 0) return;
        long used = usedMinutes(p, entryDate);
        long pct = used * 100 / p.budgetMinutes;
        String key = periodKey(p, entryDate);
        for (int t : THRESHOLDS) {
            if (pct >= t && !warnings.existsByProjectIdAndPeriodKeyAndThreshold(p.id, key, t)) {
                BudgetWarning w = new BudgetWarning();
                w.projectId = p.id; w.periodKey = key; w.threshold = t;
                try { warnings.saveAndFlush(w); } catch (org.springframework.dao.DataIntegrityViolationException dup) { continue; }
                Set<AppUser> recipients = new LinkedHashSet<>();
                if (p.lead != null) recipients.add(p.lead);
                for (AppUser u : users.findAll()) if (u.active && u.isAdmin()) recipients.add(u);
                for (AppUser r : recipients) {
                    if (!r.notifyBudget) continue;
                    mail.send(r, "budget", p.code + " " + p.name, t, key, String.format(Locale.ROOT, "%.1f", used / 60.0),
                            String.format(Locale.ROOT, "%.1f", p.budgetMinutes / 60.0));
                    AppNotification n = new AppNotification();
                    n.userId = r.id; n.messageKey = "NOTIF_BUDGET";
                    n.params = p.code + " " + p.name + "|" + t;
                    notifications.save(n);
                }
            }
        }
    }
}
