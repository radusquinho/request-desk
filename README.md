# Request Desk

Internal requests (vacation, expenses, purchases) decided by rules first and people second. A Spring Boot API applies configurable approval rules, escalates reviews that miss their deadline, keeps a full audit trail and publishes every status change as a webhook. A React app lets employees submit requests and managers work through the review queue.

**[Try the browser demo](https://radusquinho.github.io/request-desk/)** · [Portfolio](https://radusquinho.com/)

## Try it

1. Submit an expense of 40 EUR as `ana`: the rules approve it on the spot.
2. Submit one of 450 EUR: it waits for a manager. Switch to `marta` and approve it from the review queue.
3. Submit another of 450 EUR and click **Skip 49 h**. The SLA job escalates it. `marta` can no longer decide it; `admin` can.
4. Open any request to see its audit trail and the webhook events waiting in the outbox.

The public demo runs the same rules in the browser with `localStorage`, so it needs no server. The full stack runs with Docker.

## Rules

| Request | Rule | Result |
| --- | --- | --- |
| Expense | up to 100 EUR | approved automatically |
| Expense | above 1,000 EUR | escalated to an admin |
| Expense | anything in between | manager review |
| Purchase | up to 5,000 EUR | manager review |
| Purchase | above 5,000 EUR | rejected: goes through procurement |
| Vacation | up to 2 days with 15 days' notice | approved automatically |
| Vacation | overlaps another approved vacation | manager review |
| Vacation | starts in the past | rejected |
| Any pending review | no decision within 48 h | escalated to an admin |

Thresholds live in `backend/src/main/resources/application.yml` (`desk.rules.*`), so they change without touching code.

## Run it

**Full stack with Docker** (PostgreSQL + API + UI):

```sh
docker compose up --build
```

UI on http://localhost:3000, API on http://localhost:8080. Users `ana`, `luis` (employees), `marta` (manager) and `admin`, password `demo`.

**Backend only** (Java 21, no database needed, uses in-memory H2):

```sh
cd backend
./mvnw spring-boot:run
curl -u ana:demo -H "Content-Type: application/json" \
  -d '{"type":"EXPENSE","title":"Taxi","amount":40}' http://localhost:8080/api/requests
```

**Frontend only** (Node 24):

```sh
cd frontend
npm ci
npm run dev                                   # browser demo mode
VITE_API_URL=http://localhost:8080 npm run dev  # against the API
```

## Tests

```sh
cd backend && ./mvnw verify          # 25 JUnit tests: rules, API + security, SLA job, webhook backoff and signing
cd frontend && npx playwright test   # 5 end-to-end scenarios against the production build
```

CI (GitHub Actions) runs the backend tests twice, on H2 and on a real PostgreSQL service, runs the Playwright suite, and builds both Docker images. A second workflow deploys the browser demo to GitHub Pages.

## Design

- **Spring Boot 4, Java 21.** Spring Web MVC, Spring Data JPA, Flyway migrations, Bean Validation, scheduling, Actuator health.
- **Rule engine** (`rules/RuleEngine.java`) is plain Java with no database or clock access, so it is tested in isolation with parameterized tests.
- **Security.** Stateless HTTP Basic with roles (`EMPLOYEE`, `MANAGER`, `ADMIN`). Spring Security guards the endpoints; the service adds the rules that depend on the request itself: nobody reviews their own request, and escalated requests are for admins only. A second decision on a closed request returns `409 Conflict` instead of overwriting the first.
- **Time is injected.** A `Clock` bean lets the tests move time forward to check the 48 h SLA without waiting.
- **Transactional outbox.** Each status change writes an audit row and an outbox message in the same transaction, so no event is lost or sent for a change that rolled back. A scheduled dispatcher posts pending messages signed with HMAC-SHA256 (`X-Signature`) and an `Idempotency-Key` header, retries with exponential backoff (1, 2, 4, 8 minutes) and marks a message `FAILED` after five attempts.
- **React + TypeScript (Vite).** One `Api` interface with two implementations: `HttpApi` for the Spring Boot backend and `DemoApi`, a port of the same rules for the static demo.

## API

| Method | Path | Role |
| --- | --- | --- |
| `GET` | `/api/me` | employee |
| `POST` | `/api/requests` | employee |
| `GET` | `/api/requests/mine` | employee |
| `GET` | `/api/requests/{id}` | owner or manager: request, audit trail and webhook events |
| `GET` | `/api/review/queue` | manager (admins also see escalations) |
| `POST` | `/api/review/{id}/approve` · `/reject` | manager, body `{"comment": "..."}` optional |
| `GET` | `/api/audit` | manager: last 100 events |

MIT licensed.
