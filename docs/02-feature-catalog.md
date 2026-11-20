# 2. Feature Catalog

Each feature has an ID, a short description and its **decided priority** (MVP / Phase 2 / Out of scope), based on the decisions in [03-decisions.md](03-decisions.md) (referenced as → **D-xx**).

Modules:
A. Authentication & account · B. Time recording · C. Period closing · U. User administration · E. Project administration · F. Dashboard & reports · G. Notifications · H. Compliance, audit & data protection · I. General usability

Summary: **MVP 60** · **Phase 2 5** · **Out of scope 17** (see tables below; dropped features are kept for traceability).

---

## A. Authentication & Account

| ID | Feature | Description | Priority |
|----|---------|-------------|----------|
| A-01 | Login | Username (email) and password in the app → **D-02**. | MVP |
| A-02 | Invitation / first access | New users receive an email invitation and set their own password. | MVP |
| A-03 | Password reset | Self-service "forgot password". | MVP |
| A-04 | Logout & session timeout | Manual logout; automatic logout after inactivity. | MVP |
| A-05 | Role-based access | Employee, Project Lead, Backoffice Admin, Management (read-only) → **D-10**. | MVP |
| A-06 | Two-factor authentication | Second factor at login. Not in MVP – accepted risk R-03. | Phase 2 |
| A-07 | Personal settings | Language (DE/EN), default project, preferred view, notification preferences. | MVP |
| A-08 | Deactivated users cannot log in | Access ends immediately on deactivation; data remains. | MVP |

## B. Time Recording (Employee)

| ID | Feature | Description | Priority |
|----|---------|-------------|----------|
| B-01 | Manual entry | Add an entry with date, project, activity, duration or start/end, optional description. | MVP |
| B-02 | Week view (grid) | Rows = projects, columns = days; overview and fast entry. | MVP |
| B-03 | Day view / list | Today's working time, breaks and project entries with totals. | MVP |
| B-04 | Edit / delete own entries | Allowed until the period is locked → **D-08**. | MVP |
| B-05 | Only assigned + open projects selectable | → **D-06**. | MVP |
| B-06 | Billable flag | → **D-04b** (no client billing). | Out of scope |
| B-07 | Start/stop timer | "Start work" / "Pause" / "Stop" and project timer; creates entries automatically. | MVP |
| B-08 | Start/end times | Entries recorded as times of day, not only durations → **D-01**. | MVP |
| B-09 | Copy previous day/week | Duplicate recurring project rows. | MVP |
| B-10 | Favourites / recent projects | Most-used projects first. | MVP |
| B-11 | Mandatory description | Per project configurable. | Phase 2 |
| B-12 | Activity selection | From the global activity list → **D-04**. | MVP |
| B-13 | Own summaries & balance | Totals per day/week/month and per project; target vs. actual; overtime balance → **D-07**. | MVP |
| B-14 | Gap hints | Highlight days without entries, below target, or where project time ≠ working time. | MVP |
| B-15 | Absence marker | Vacation, sick, holiday, other – counts towards target time → **D-05**. | MVP |
| B-16 | Mobile-friendly use | Responsive web UI; no native app. | MVP |
| B-17 | Recording deadline | Fixed number of days for changes → **D-08** (until lock instead). | Out of scope |
| B-18 | Working-time recording | Daily start, end and breaks as legal working-time record → **D-01**. | MVP |

## C. Period Closing

| ID | Feature | Description | Priority |
|----|---------|-------------|----------|
| C-01 | Period locking by admin | Backoffice locks a period for all or selected users → **D-03**. | MVP |
| C-02 | Submit timesheet | → **D-03**. | Out of scope |
| C-03 | Approve / return | → **D-03**. | Out of scope |
| C-04 | Approval overview | → **D-03** (completeness report F-06 covers the need). | Out of scope |
| C-05 | Admin correction | Admins correct entries in locked periods; every change logged (H-01). | MVP |
| C-06 | Multi-level approval | Enterprise feature. | Out of scope |

## U. User Administration (Backoffice)

| ID | Feature | Description | Priority |
|----|---------|-------------|----------|
| U-01 | Create / edit users | Name, email, role(s), personnel number, entry/exit date, team, federal state (for holidays). | MVP |
| U-02 | Deactivate / reactivate users | Instead of deleting; historic data stays. | MVP |
| U-03 | Assign roles | Employee, Project Lead, Backoffice Admin, Management. | MVP |
| U-04 | Target working time | Weekly hours and working days with validity dates → **D-07**. | MVP |
| U-05 | Assign projects to user | Complements E-04. | MVP |
| U-06 | Teams / departments | Group users; filter dashboard and reports by team. | MVP |
| U-07 | Cost / billing rates | → **D-09**. | Out of scope |
| U-08 | Bulk import | Initial import of users and projects from a spreadsheet. | MVP |
| U-09 | Act on behalf of user | Admin enters time for a user (corrections in locked periods are covered by C-05). | Phase 2 |
| U-10 | Initial overtime balance | Set the opening balance when a user starts using the system (carry-over from the old method). | MVP *(new, follows from D-07)* |

## E. Project Administration (Backoffice)

| ID | Feature | Description | Priority |
|----|---------|-------------|----------|
| E-01 | Create / edit projects | Name, code, description, project lead, start/end date. | MVP |
| E-02 | Project status | Active / archived. | MVP |
| E-03 | Clients | → **D-04b**. | Out of scope |
| E-04 | Project members | Assign users; mark project as "open to everyone" → **D-06**. | MVP |
| E-05 | Billable default | → **D-04b**. | Out of scope |
| E-06 | Activity list | Maintain the global list of activity types → **D-04**. | MVP |
| E-07 | Hour budget | Planned hours per project (total or per month), used vs. planned, warnings at e.g. 80 % / 100 %. | MVP |
| E-08 | Money budget | → **D-09**. | Out of scope |
| E-09 | Internal default projects | E.g. Administration, Training, Internal meetings. | MVP |
| E-10 | Sub-projects / hierarchy | Activities cover the need. | Out of scope |

## F. Dashboard & Reports

| ID | Feature | Description | Priority |
|----|---------|-------------|----------|
| F-01 | Overview dashboard | Total hours, hours per project/team, weekly trend, overtime totals, missing time, projects near budget. | MVP |
| F-02 | Project report | Hours per user, per activity, per week/month; entry list; budget consumption. | MVP |
| F-03 | User report | Working time, project hours, target vs. actual, overtime balance, absences. | MVP |
| F-04 | Flexible filtering & grouping | Filter and group freely by period, team, project, user, activity. | MVP |
| F-05 | Export | CSV / Excel for all reports; PDF for user and project reports (e.g. monthly working-time record). | MVP |
| F-06 | Completeness report | Who has missing days or gaps for the period – basis for locking. | MVP |
| F-07 | Budget report | All projects with budget, consumption, warning state. | MVP |
| F-08 | Utilization report | → **D-10**. | Out of scope |
| F-09 | Overtime report | Overtime balances per user and team → **D-07**. | MVP |
| F-10 | Saved report views | Save filter combinations for reuse. | Phase 2 |
| F-11 | Scheduled reports by email | Not wanted. | Out of scope |
| F-12 | Project lead view | Leads see project-time reports for their own projects → **D-10**. | MVP |
| F-13 | Payroll export | Defined export of working time, overtime and absences per month, agreed with payroll. | MVP |
| F-14 | Management view | Aggregated dashboard without per-person data → **D-10**. | MVP *(new, follows from D-10)* |

## G. Notifications

| ID | Feature | Description | Priority |
|----|---------|-------------|----------|
| G-01 | Reminder for missing time | Email before month-end lock / for missing days. | Phase 2 |
| G-02 | Approval notifications | → **D-03**. | Out of scope |
| G-03 | Budget warnings | Email to project lead / backoffice at budget thresholds (part of E-07). | MVP |
| G-04 | Chat integration | Not wanted. | Out of scope |

## H. Compliance, Audit & Data Protection

| ID | Feature | Description | Priority |
|----|---------|-------------|----------|
| H-01 | Change history for entries | Who created/changed which entry when (old/new value). | MVP |
| H-02 | Admin action log | User/project administration and period locks. | MVP |
| H-03 | Own data view & export | Employees view and download their own records (right of access; planned right to a copy). | MVP |
| H-04 | Automatic retention & deletion | → **D-11** (keep forever) – accepted risk R-01. | Out of scope |
| H-05 | Visibility rule set | Enforced per role as defined in **D-10**. | MVP |
| H-06 | Working-time rule checks | Warnings for >10 h/day, missing breaks (30 min > 6 h, 45 min > 9 h), < 11 h rest → **D-01**. | MVP |

## I. General Usability

| ID | Feature | Description | Priority |
|----|---------|-------------|----------|
| I-01 | Responsive web UI | Desktop, tablet, phone browser. | MVP |
| I-02 | Languages | German and English → **D-12**. | MVP |
| I-03 | Accessibility | Keyboard operation, contrast, screen-reader friendly forms. | MVP |
| I-04 | Public holidays calendar | Per federal state; excluded from target time → **D-07**. | MVP |
| I-05 | Help / onboarding | In-app hints and a short user guide (DE/EN). | MVP |
| I-06 | Integrations | Not wanted; exports cover the need. | Out of scope |
