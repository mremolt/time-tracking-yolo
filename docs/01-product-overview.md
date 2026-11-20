# 1. Product Overview

## 1.1 Purpose

Employees record their daily working time (start, end, breaks) and allocate it to projects. Backoffice manages users and projects and uses reports to see where time goes – per project, per person, and overall. The application replaces spreadsheets or ad-hoc tools with one consistent, reliable record.

## 1.2 Context and sizing

- Around **100 employees**, a **small backoffice team** (estimated 2–5 people) administering the system.
- Internal use only; a single company (no multi-tenancy, no external customers logging in).
- Typical volume: a few hundred time entries per day, a few dozen to a few hundred active projects.
- The application should be **simple to use daily** (recording a day should take under a minute) and **simple to operate** – not an enterprise suite.

## 1.3 Goals

| ID | Goal |
|----|------|
| G1 | Every employee can record time per project quickly, from desktop and from a phone browser. |
| G2 | Backoffice can create and maintain users, teams, projects and activities without technical help. |
| G3 | Backoffice gets a dashboard with an overview plus per-user, per-team and per-project reports, exportable for further use (e.g. controlling, payroll). |
| G4 | Recorded data is trustworthy: complete, traceable, and protected from silent changes after it has been checked. |
| G5 | The solution covers the legal working-time recording duty (start, end, breaks, overtime balance) – decision D-01. |

## 1.4 Non-goals (deliberately not part of this product)

- **Clients, billing, invoicing and money values** – internal hours only (D-04b, D-09).
- **Payroll calculation** – exports only.
- **Full HR system** (contracts, salaries, applicant handling, leave requests/approvals) – only a simple absence marker (D-05).
- **Resource planning / scheduling / Gantt charts.**
- **Employee monitoring** (screenshots, activity levels, GPS tracking, keystroke logging) – not appropriate for this context and problematic under data protection and works-council rules.
- **Approval workflows, multi-company, single sign-on federation** and other enterprise features (D-02, D-03).

## 1.5 User roles

| Role | Who | Main capabilities |
|------|-----|-------------------|
| **Employee** | All staff | Record working time and project time, absences; see own summaries and overtime balance; export own data. |
| **Project Lead** | Selected staff | Everything an employee can do, plus: reports of project time booked on their own projects (D-10). |
| **Backoffice Admin** | Backoffice team | Manage users, teams, projects, activities; see all data; dashboard and all reports; exports; correct entries; lock periods. |
| **Management (read-only)** | Executives | Aggregated dashboard and reports only, no per-person data, no administration (D-10). |

A user can hold more than one role (e.g. a project lead is also an employee).

## 1.6 Domain terms (glossary)

| Term | Meaning |
|------|---------|
| **User** | A person with an account. Has role(s), team, active/inactive status, weekly target working time and federal state (for holidays). |
| **Team** | A group of users (department) used for filtering reports. |
| **Project** | Something time is booked against, e.g. "Website relaunch" or "Administration". Has a status (active/archived), optional hour budget, a project lead, and members (or is open to everyone). |
| **Activity** | A company-wide category of work, e.g. "Development", "Meeting", "Travel" (D-04). |
| **Project time entry** | One record: user, date, project, activity, start/end or duration, optional description. |
| **Period** | Typically one month. Unit for completeness checks, export and locking. |
| **Lock** | A closed period – entries can no longer be changed by the employee. |
| **Working time** | Start, end and breaks of the working day as required by labour law – the basis for overtime (D-01). |
| **Absence** | A day marked as vacation, sick, public holiday or other (D-05). |
| **Overtime balance** | Running difference between actual working time and target time (D-07). |

## 1.7 Core user journeys

**J1 – Employee records a day**
Starts work with the timer (or enters start/end later) → records breaks → allocates the day to projects and activities → sees daily total, target and overtime balance. Rule warnings appear for e.g. missing breaks.

**J2 – Employee reviews the week**
Opens the week view → system hints at gaps (day without entries, project time ≠ working time) → fills gaps, marks absences.

**J3 – Backoffice onboards a new employee**
Creates the user (name, email, role, team, target hours, federal state, start date, opening overtime balance) → assigns projects → user receives an invitation and sets a password.

**J4 – Backoffice sets up a project**
Creates project (name, code, project lead, optional hour budget) → assigns members or marks it open to everyone → project appears in selection lists.

**J5 – Backoffice checks the month**
Opens the dashboard → checks the completeness report → resolves gaps with employees → creates payroll export → locks the month.

**J6 – Employee leaves the company**
Backoffice deactivates the user → login disabled, historic data stays in reports.
