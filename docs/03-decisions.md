# 3. Decision Log

Status: **all decisions taken** (2026-11-20). The original options and reasoning are summarised so that the reasons stay traceable. Points marked ⚠ carry a known risk; these risks are **accepted for the MVP** and recorded in the [risk register](#accepted-risks-mvp) at the end of this document. No mitigation is implemented in the MVP.

| ID | Topic | Decision | Recommended was |
|----|-------|----------|-----------------|
| D-01 | Project time vs. legal working time | **Both from the start** – working time (start/end/breaks) and project allocation | Project time, extensible |
| D-02 | Login method | **Own username/password** in the app ⚠ | Company SSO |
| D-03 | Approval workflow | **Admin period lock**, no individual approval | ✔ same |
| D-04 | Activities | **One global activity list** | ✔ same |
| D-04b | Clients / billing | **No** – internal projects only, no billable flag | – |
| D-05 | Absences | **Simple absence marker**, no approval | ✔ same |
| D-06 | Bookable projects | **Assigned projects + projects open to everyone** | ✔ same |
| D-07 | Target hours & overtime balance | **Yes, in MVP** | ✔ same |
| D-08 | Recording deadline | **Editable until the period is locked** ⚠ | Within X days |
| D-09 | Money values | **No** – hours only | ✔ same |
| D-10 | Visibility of per-person data | **Employee + backoffice; project leads for their own projects; management aggregated only** ⚠ | ✔ same |
| D-11 | Data retention | **Keep forever**, no automatic deletion ⚠ | Decide with DPO |
| D-12 | UI language | **German + English** | – |

---

## D-01 – Project time and legal working time → **Both from the start**

**Background:** Since the Federal Labour Court decision of 13 Sep 2022 (BAG 1 ABR 22/21), employers in Germany must record start, end and duration of working time. A government draft amending the Working Hours Act (June 2026, not yet passed as of Oct 2026) plans electronic, same-day recording, a 2-year transition for companies with 50–249 employees, an employee right to a copy, and fines up to €30,000.

**Consequences:**
- The working day is recorded with start, end and breaks (B-08, B-18); project time is allocated within it.
- Rule checks for maximum daily hours, breaks and rest periods (H-06).
- Target hours, public holidays, absences and overtime balance become MVP (U-04, I-04, B-15, F-09, D-07).
- Exempt groups (e.g. executive staff under § 18 ArbZG) may need a simplified mode – to clarify with HR.

## D-02 – Login → **Own username/password**

**Consequences:** invitation (A-02), password reset (A-03) and account lockout handling are MVP. Two-factor login (A-06) is Phase 2.
⚠ Risk accepted for MVP → **R-03**.

## D-03 – Approval → **Admin period lock**

Backoffice locks a period (typically a month); afterwards only admins can change entries, and every change is logged. No submit/approve workflow (C-02 – C-04, G-02 dropped).

## D-04 – Activities and clients → **Global activity list, no clients**

- One company-wide list of activity types (e.g. Development, Meeting, Training, Travel) selectable on every project (B-12, E-06).
- No client list, no billable flag, no client reports (B-06, E-03, E-05 dropped). If billing to external parties becomes relevant later, clients and billable flag can be added.

## D-05 – Absences → **Simple absence marker**

Employees (or admins) mark days as vacation, sick, public holiday, other. No leave requests or balances. Required so that target vs. actual hours are correct. Now **MVP** because of D-07.

## D-06 – Bookable projects → **Assigned + open projects**

Employees see projects they are assigned to plus projects flagged as "open to everyone" (Administration, Training, …).

## D-07 – Target hours & overtime → **Yes, MVP**

Contract hours per user with validity dates, working days, public holidays (by federal state where relevant), absences → target vs. actual and running overtime balance.

## D-08 – Recording deadline → **Until period lock**

Employees can create and change entries until backoffice locks the period.
⚠ Risk accepted for MVP → **R-02**.

## D-09 – Money values → **No**

Reports show hours only. No rates, costs or money budgets (U-07, E-08 dropped).

## D-10 – Visibility → **Leads see their own projects**

| Viewer | Own data | Per-person data of others | Aggregated data |
|--------|----------|---------------------------|-----------------|
| Employee | ✔ | – | – |
| Project lead | ✔ | Only project time on their own projects (not working time, overtime or absences) | Own projects |
| Backoffice admin | ✔ | All | All |
| Management (read-only) | ✔ | – | All |

No per-person utilization ranking (F-08 dropped).
⚠ Risk accepted for MVP → **R-04**.

## D-11 – Data retention → **Keep forever**

No automatic deletion; H-04 dropped.
⚠ Risk accepted for MVP → **R-01**.

## D-12 – UI language → **German + English**

Both languages from the start; each user chooses their language in personal settings (A-07).

---

## Accepted risks (MVP)

The following risks are known and **accepted for the MVP**. No mitigation is part of the MVP scope. They are to be reviewed before the MVP is extended or rolled out beyond its initial use.

| ID | Risk | From | Possible mitigation (after MVP) |
|----|------|------|--------------------------------|
| R-01 | **Indefinite data retention.** Keeping personal data forever conflicts with the GDPR principle of storage limitation (Art. 5 (1)(e)) and with erasure requests of former employees. The draft working-time law mentions retention of up to 2 years. | D-11 | Confirm with data protection officer; add retention rules (H-04) or manual anonymisation of former employees. |
| R-02 | **Late recording allowed.** The draft working-time law requires recording on the day of work; entries can be created/changed until the period lock. | D-08 | Once the law is in force: recording deadline (B-17) and/or a report of late entries based on the change history (H-01). |
| R-03 | **No second login factor.** Access to personal working-time data relies on app passwords only, including admin accounts. | D-02 | Two-factor login (A-06), at least for admins; alternatively company SSO. |
| R-04 | **Co-determination / privacy not yet cleared.** Introducing working-time recording and the visibility rules typically requires works council co-determination (§ 87 (1) no. 6 BetrVG) and a privacy notice. | D-01, D-10 | Involve works council and data protection officer; adjust visibility rules if required. |
| R-05 | **Legal status open.** The ArbZG amendment is a draft (status Oct 2026); final rules (deadline, exemptions, break/rest checks) may differ from the current assumptions. | D-01 | Re-check legal status before go-live and when the law is passed. |
