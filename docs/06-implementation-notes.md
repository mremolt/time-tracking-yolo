# 6. Implementation Notes (MVP, local demo)

What was built for [05-implementation-plan.md](05-implementation-plan.md), which assumptions were made where Phase 0 decisions are still open, and how the demo is run. **Written by the development side – nothing in 6.1 and 6.3 has been confirmed by HR, payroll or the backoffice yet.**

## 6.1 Assumptions for the open Phase 0 questions (step 0.1 / 0.2)

All of these are configuration or isolated code and can be changed after the stakeholder demo.

| Topic | Assumption in the MVP | Where |
|-------|-----------------------|-------|
| Exempt employees (D-01) | No simplified mode. Everyone records working time. A management-only user simply has no target time. | – |
| Project vs. working time (B-14) | Mismatch is only a hint (as assumed in doc 4). Never blocks. | `Calc` |
| Session / lockout / password | 30 min inactivity; 5 failed logins lock the account for 15 min; password ≥ 10 characters with letter and digit. | `application.yml`, `AuthService` |
| Period | Calendar month. Locked for everybody or for single users, by backoffice only; backoffice can unlock. Locks and unlocks are in the admin log. | `LockService`, `AdminService` |
| Budget thresholds (E-07) | 80 % and 100 % of the budget, total or per calendar month. One warning per project, period and threshold, to the project lead and all backoffice admins who have the notification enabled (in-app + email). | `BudgetService` |
| Holidays (I-04) | Per federal state, maintained per year in *Master data*. A generator proposes nationwide and state holidays (simplified rules; Buß- und Bettag only for SN) – **to be reviewed every year**. | `HolidayCalendar` |
| Absence types (B-15) | Vacation, sick, public holiday (manual, e.g. regional), other. A full day sets the target of the day to 0, a half day halves it. Weekends/holidays are skipped when marking ranges. No leave balances. | `Calc.applyAbsence` |
| Target time | Weekly hours ÷ number of working days, per user, valid from a date (contract changes, federal-state moves). Entry/exit date limit the time span. | `TargetSchedule`, `Calc` |
| Overtime balance | Opening balance + Σ(actual − target) from the balance start date (default: entry date). Today only counts once the working day has been ended; the future never counts. Pure function of stored data ⇒ reproducible. | `Calc.balance` |
| Rule checks (H-06) | > 10 h net per day; > 6 h net needs ≥ 30 min break, > 9 h needs ≥ 45 min; rest < 11 h since the previous recorded day. Warnings only. | `Calc.days` |
| Time zone | Company time zone Europe/Berlin; instants stored in UTC; a working day belongs to the date of its start; work past midnight and DST are handled by real elapsed time (tested). | `Tz` |
| Payroll export (F-13, step 0.2) | **Provisional format**, no sample from payroll yet: one row per employee and month, UTF-8, `;` separated, `.` decimal, columns `PersonnelNumber, LastName, FirstName, Month, ContractHours, TargetHours, WorkedHours, OvertimeMonth, OvertimeBalance, VacationDays, SickDays, OtherAbsenceDays, PublicHolidays`. Needs payroll sign-off. | `ReportService.payroll` |
| Visibility (D-10) | Enforced in `Access` and applied centrally to every report query, export and the dashboard (see 6.2). | `Access`, `ReportService` |

## 6.2 Technical specification in short (step 0.4)

* **Single deployable:** the Angular build is bundled into the Spring Boot jar; REST API under `/api`; H2 file database (`backend/data/`) for the local installation. Hibernate creates the schema (`ddl-auto=update`) – no migration tool yet; introduce Flyway before any productive use.
* **Auth:** own accounts, BCrypt, server session cookie (HttpOnly, SameSite=Lax), CSRF double-submit cookie (Angular default). Deactivation is checked on every API call, so a running session ends at once.
* **Permissions:** one component (`Access`) answers "who may see/change what". Time data of others is only reachable through admin-only calls or through report queries that filter rows per D-10 (own rows / rows on led projects / everything; management only gets rows for aggregation). Exports reuse the same queries.
* **Audit:** `AuditService` writes old/new values for every change of work days, project entries, absences (kind DATA, with the creation time of the object, R-02) and of users, projects, teams, activities, schedules, holidays, locks (kind ADMIN). Deleting is also logged; users/projects are only deactivated/archived.
* **Locking:** `LockService.assertEditable` is called by every write path; employees are rejected (`PERIOD_LOCKED`), admins pass and are logged.
* **Reports:** every report returns one localised `Table`, shown on screen and exported as CSV/XLSX (PDF for project and user reports). CSV cells starting with `=+-@` are neutralised; XLSX cells are always text.
* **i18n:** DE/EN in the UI (`i18n-de.ts`/`i18n-en.ts`, a unit test checks both have identical keys), in emails and in report/export labels (`messages_*.properties`).
* **Accessibility baseline:** labelled form fields, skip link, visible focus, keyboard-operable controls, `aria-live`/`role=alert` messages, dialogs close with Esc, responsive layout down to ~390 px. No formal accessibility audit or screen-reader test has been done.

## 6.3 Demo data and expected results (step 0.3)

`./scripts/start-demo.sh` creates (relative to today, so the data is always "recent"): 3 teams, 9 users covering all roles incl. users with several roles and one deactivated former employee, different federal states (BE, BY, NW, HH), a contract change for Ben (40 h → 32 h Mon–Thu) and a move for Dieter (HH → NW) one month after start, opening balances (+3 h Ben, +12 h Dieter), 5 bookable projects (with/without budget, monthly and total budget, open to everyone, internal defaults, one archived), absences (vacation week, sick day, half days), recording gaps (Eva), a rule violation (Dieter: 11 h with 20 min break), and the oldest month locked.

Calculation scenarios with fixed expected results are part of the automated tests (`CalcTest`): contract change, federal-state holidays, opening balance + absences + half day (expected balance 4:30 after 5 Feb 2026), reproducibility after master-data changes, work past midnight (4 h), DST change (7 h and 9 h real time), each rule check and gap hint. **HR still has to confirm these expectations (step 0.3).**

## 6.4 Coverage of the MVP feature catalog

| Module | Features | Where in the UI |
|--------|----------|-----------------|
| A | A-01…A-05, A-07, A-08 | Login, invitation / reset pages, logout, 30 min timeout, roles, *Settings*, immediate lock-out of deactivated users |
| B | B-01…B-05, B-07…B-10, B-12…B-16, B-18 | *Day* (timers, working time with several breaks, project entries), *Week* (grid, copy, favourites ★ / recent ⟲), *Absences*, *Summary*; hints on day and week |
| C | C-01, C-05 | *Administration → Periods & locks*; admin corrections via *Day → for employee*, logged |
| U | U-01…U-06, U-08, U-10 | *Administration → Users* (incl. contracts with validity, assignments, opening balance), *Import* |
| E | E-01, E-02, E-04, E-06, E-07, E-09 | *Administration → Projects / Master data*, budget columns, internal default projects ADM/TRN/MTG |
| F | F-01…F-07, F-09, F-12…F-14 | *Dashboard* (admin and management view), *Reports* (user, project incl. lead scope, completeness, budget, overtime, payroll), export CSV/XLSX/PDF |
| G | G-03 | notification bell + email (see local mailbox) |
| H | H-01, H-02, H-03, H-05, H-06 | history 🕘 on entries/users/projects, *Administration → Audit log*, *Summary → download my data*, central `Access`, rule warnings |
| I | I-01…I-05 | responsive UI, DE/EN switch, accessibility baseline, holiday calendar, *Help* page |

Phase 2 items (A-06, B-11, U-09, F-10, G-01) are not built. Admins can already correct entries of other users (needed for C-05), which is not the full U-09.

## 6.5 Demo script (step 20)

1. **J3/J4/J6 (Sabine, backoffice):** *Users → New user* (roles, team, entry date, opening balance) → open the local mailbox, follow the invitation link, set a password. *Projects → New project* with lead and budget, mark one "open to everyone". Deactivate a user → that user is logged out immediately. Show *Import* with the sample columns, including an error report.
2. **J1 (Anna, employee):** *Day* → Start / Pause / Stop, add project time by duration and with the project timer, see daily total, target and balance, rule warnings. Switch DE ⇄ EN.
3. **J2 (Anna):** *Week* grid, copy last week, favourites; hints for missing days; *Absences* → mark vacation. Phone-sized window for the same.
4. **J5 (Sabine):** *Dashboard* → *Reports → Completeness* → *Payroll export* → *Periods & locks* → lock last month. As Anna, try to change a day in that month (rejected), as Sabine correct it for Anna and show the entry in the *Audit log*.
5. **Visibility (D-10):** as Paul (project lead) *Project report* shows per-person rows only for his projects; as Mara (management) only totals, no person data, and 403 on completeness/overtime-by-person/payroll; Anna sees only her own rows.
6. **Budgets:** *Reports → Budgets* and the notification/email at 80 % / 100 %.

## 6.6 Known limits (for the feedback round, not for production)

* No Flyway migrations, no HTTPS/reverse proxy, no backups, no monitoring; H2 file database; mail is captured, not sent; the public mailbox page must be disabled outside a local installation.
* The three open reviews of the plan are still open: HR confirmation of rules/expected balances, payroll sign-off of the export format, backoffice review of every step. The accepted risks R-01…R-05 are unchanged.
* Reports are table based (screen/CSV/XLSX/PDF); PDF layout is a simple table without logo or signature lines.
* Balances are computed on the fly per request (fine for ~100 users); caching or materialised monthly closings are not needed yet.
