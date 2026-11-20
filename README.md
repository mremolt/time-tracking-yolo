# Time Tracking – MVP

Internal time tracking for ~100 employees: legal working time (start/end/breaks), project time, target/actual and overtime balance, absences, period locking, budgets and reports. Implements the MVP scope of Phases 1a and 1b from [docs/](docs/README.md) (plan: [docs/05-implementation-plan.md](docs/05-implementation-plan.md)).

**Stack:** Spring Boot 3.5 (Java 21+) · Angular 21 · H2 file database (local installation). Assumptions, feature coverage and the demo script: [docs/06-implementation-notes.md](docs/06-implementation-notes.md).

## Quick start (local demo)

Prerequisites: JDK 21+, Maven 3.9+, Node 20+ with npm.

```bash
./scripts/build.sh        # builds Angular + Spring Boot into one jar and runs all tests
./scripts/start-demo.sh   # fresh demo database with demo data, serves everything on http://localhost:8080
```

Open <http://localhost:8080>. Emails are **not sent**; they are captured and shown at <http://localhost:8080/mailbox> (invitations, password resets, budget warnings).

| Role | Login | Password |
|------|-------|----------|
| Backoffice admin | `sabine.admin@demo.local` | `Demo-Passw0rd1` |
| Management | `mara.manager@demo.local` | `Demo-Passw0rd1` |
| Project lead | `paul.lead@demo.local`, `clara.weber@demo.local` | `Demo-Passw0rd1` |
| Employees | `anna.schmidt@`, `ben.mueller@`, `dieter.fischer@`, `eva.becker@demo.local` | `Demo-Passw0rd1` |
| Bootstrap admin | `admin@example.com` | `ChangeMe-12345` |

`RESET=0 ./scripts/start-demo.sh` keeps the existing demo data. `./scripts/start.sh` starts without demo data (only the bootstrap admin).

## Development

```bash
cd backend  && mvn spring-boot:run -Dspring-boot.run.profiles=demo   # API on :8080
cd frontend && npx ng serve                                          # UI on :4200, proxies /api to :8080
cd backend  && mvn test                                              # backend tests (calculations, permissions, locking, audit, budgets, exports)
cd frontend && npx ng test --watch=false                             # frontend unit tests
```

## Configuration (`backend/src/main/resources/application.yml`)

| Setting | Default | Meaning |
|---------|---------|---------|
| `server.servlet.session.timeout` | 30m | automatic logout after inactivity |
| `app.max-failed-logins` / `app.lockout-minutes` | 5 / 15 | account lockout |
| `app.min-password-length` | 10 (+ letter and digit) | password rule |
| `app.invite-valid-hours` / `app.reset-valid-hours` | 72 / 2 | link validity |
| `app.mail-viewer-enabled` | true | public mailbox page, **local installation only** |
| `app.bootstrap-admin-*` | `admin@example.com` | first admin, created only if the database is empty – change the password |
| `app.demo-data` | false (true in profile `demo`) | load fictitious demo data into an empty database |

## Project layout

```
backend/   Spring Boot: domain (entities, repositories), service (calculation, audit, reports, import), web (REST), config (security, central permission check)
frontend/  Angular: core (api, auth, i18n DE/EN), pages (one standalone component per screen)
scripts/   build and start scripts
docs/      planning documents 1–5 and implementation notes (06)
```

Not part of this MVP (see plan §5.6): production hosting, real data, formal security/accessibility review, legal review, rollout, Phase 2 features.
