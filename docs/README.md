# Time Tracking Application – Planning Documents

High-level feature analysis for an internal time tracking application for a company of roughly 100 employees.

> **Scope of these documents:** feature analysis only. Technical specifications (architecture, data model, APIs, infrastructure, security implementation details) are intentionally left out and will be provided separately by another team.
> **Only fixed technical constraint:** Backend **Spring Boot**, frontend **Angular**.

| # | Document | Content |
|---|----------|---------|
| 1 | [01-product-overview.md](01-product-overview.md) | Goals, non-goals, user roles, domain terms, core user journeys |
| 2 | [02-feature-catalog.md](02-feature-catalog.md) | All features per module with their decided priority |
| 3 | [03-decisions.md](03-decisions.md) | **Decision log** – all product decisions (taken 2026-11-20), with open risks marked ⚠ |
| 4 | [04-roadmap.md](04-roadmap.md) | Release phases (1a, 1b, 2), success criteria, hand-off notes for the technical team |
| 5 | [05-implementation-plan.md](05-implementation-plan.md) | Step-by-step implementation order with scope, dependencies and completion criteria per step |
| 6 | [06-implementation-notes.md](06-implementation-notes.md) | What was built: assumptions for open questions, technical outline, feature coverage, demo script, known limits |

## How to use

1. Read the overview (doc 1) to confirm goals and roles.
2. Note the accepted risks (R-01 – R-05) in doc 3 – they are recorded but not addressed in the MVP.
3. Hand all documents to the technical team as input for their specification.

## Priority legend (used throughout)

| Priority | Meaning |
|----------|---------|
| **MVP** | Needed for the first productive release |
| **Phase 2** | Valuable, planned shortly after go-live |
| **Out of scope** | Deliberately not built (enterprise-level or unrelated) |
