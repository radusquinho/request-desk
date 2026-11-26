# Request Desk: design (2026-11-26)

## Why
Portfolio project for a junior developer job search. The jobs reviewed on 2026-11-26 kept asking for the same things the portfolio was missing: Spring Boot, Spring Security, React (web), Docker, CI/CD and automated end-to-end tests (Playwright). Automation of business processes (RPA, HR tools, ERP, support) was the most common theme. One project that covers all of these is worth more than several small ones.

## What it is
An internal request desk that automates approvals. Employees submit requests (vacation, expense, purchase). A rule engine decides on its own: approve, reject, or send to a manager. Scheduled jobs escalate requests that pass their deadline. Every decision is written to an audit log, and every status change is published to other systems through webhooks delivered from an outbox with retries.

## Goals
- Backend: Spring Boot 3, Java 21, Spring Security (roles), Spring Data JPA, Flyway, PostgreSQL, scheduling, REST API.
- Frontend: React + TypeScript (Vite): submit form, my requests, manager review queue, audit trail.
- Quality: JUnit 5 + Spring Boot Test (MockMvc), Playwright end-to-end tests.
- Delivery: Dockerfiles, docker-compose, GitHub Actions CI (backend tests on PostgreSQL, frontend build, Playwright, Docker build).
- A public browser demo on GitHub Pages, like the other portfolio projects, with no account and no server cost.

## Non-goals
- Real email/Slack integrations (webhooks only), real SSO, multi-tenant, production hosting.

## Anti-goals
- Anything that needs a paid service.
- Code the candidate couldn't explain in an interview: keep it small and readable.

## Approach (my calls)
- Monorepo `request-desk/` with `backend/` and `frontend/`.
- Tests run on H2 in PostgreSQL mode locally (Docker is not installed); CI runs them against a real PostgreSQL service.
- The GitHub Pages demo runs the frontend in "demo mode": a small TypeScript port of the same rules with localStorage, clearly labelled in the UI. Playwright tests run against demo mode so CI needs no backend.
- Auth: HTTP Basic with three demo users (employee, manager, admin). Enough to show Spring Security roles without building an identity system.
- Rules live in `application.yml` so they can be changed without code.

## Rules (initial)
- Expense <= 100 EUR: auto-approve. Expense > 1,000 EUR: escalate to admin. Otherwise: manager review.
- Vacation <= 2 days requested with >= 15 days notice: auto-approve. Vacation overlapping another approved vacation: manager review. Start date in the past: reject.
- Purchase: always manager review; > 5,000 EUR: reject with a reason.
- Pending review older than the SLA (48 h by default): escalate.
