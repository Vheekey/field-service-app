# Field Service Implementation README

This repository contains the first implementation slice of a field service application for dispatchers and field workers. The implementation follows the reviewed architecture in `specs/review`: a Spring Boot modular monolith API, PostgreSQL/PostGIS as the source of truth, REST for durable commands, and a Vue 3 worker-facing frontend.

## Repository Layout

```text
backend/   Spring Boot 3 API, domain modules, Flyway migrations, tests
frontend/  Vue 3 + Vite frontend, worker task UI, auth state, API clients
specs/     Architecture, API contract, data model, and sprint references
docs/      Implementation-facing documentation
```

## Current Implementation Scope

Implemented backend foundations:

- Spring Boot 3.3 application using Java 21.
- Domain-oriented package structure for identity, workers, tasks, assignments, shifts, vehicles, sync, realtime, audit, and common code.
- PostgreSQL/PostGIS schema migrations with matching rollback scripts.
- JWT access tokens and HttpOnly refresh-token cookies.
- Role-based API authorization for admin, dispatcher, and field worker workflows.
- Task lifecycle APIs for create, list, detail, update, assign, unassign, start, complete, block, comments, and audit events.
- Worker task and route endpoints backed by assigned task ordering.
- Global API error shape and validation handling.
- Unit/controller tests for auth, security conversion, and task application behavior.

Implemented frontend foundations:

- Vue 3, Vue Router, Pinia, and TanStack Vue Query.
- Login flow with stored access token and refresh-cookie support.
- Worker task list screen with progress summary, task cards, current shift display, and offline/connectivity state.
- API clients for auth, tasks, and shifts.
- Mock task and shift data for fallback/frontend development paths.

Known incomplete or placeholder areas:

- Shift endpoints currently return `501 Not Implemented`.
- Vehicle endpoints currently return `501 Not Implemented`.
- Sync endpoints currently return `501 Not Implemented`.
- Realtime publishing package exists as a foundation, but end-to-end WebSocket/SSE delivery is not complete.
- Idempotency headers are required on some task commands, but full replay persistence is still part of the sync/outbox work.
- There is no checked-in Docker Compose file yet, so local PostgreSQL/PostGIS must be provided separately.

## Backend

### Requirements

- Java 21
- Maven 3.9+
- PostgreSQL with PostGIS enabled
- Database named `field_service`
- Local database user/password matching `backend/src/main/resources/application.yml`, unless overridden:

```text
username: field_service
password: field_service
```

### Configuration

The backend defaults live in `backend/src/main/resources/application.yml`.

Important defaults:

```text
API base path:        /api/v1
Database URL:         jdbc:postgresql://localhost:5432/field_service
CORS origin:          http://localhost:5173
JWT issuer:           field-service
Refresh cookie name:  refresh_token
```

For local development, set a stronger JWT secret when running anything beyond disposable testing:

```sh
export APP_SECURITY_JWT_SECRET='replace-with-a-long-local-secret'
```

### Run

From `backend/`:

```sh
./mvnw spring-boot:run
```

If Maven Wrapper is not present in your checkout, use:

```sh
mvn spring-boot:run
```

The API starts on `http://localhost:8080`.

Useful URLs:

```text
Health:       http://localhost:8080/actuator/health
OpenAPI JSON: http://localhost:8080/v3/api-docs
Swagger UI:   http://localhost:8080/swagger-ui.html
```

### Test

From `backend/`:

```sh
mvn test
```

## Frontend

### Requirements

- Node.js 20+
- npm

### Install

From `frontend/`:

```sh
npm install
```

### Run

From `frontend/`:

```sh
npm run dev
```

The frontend starts on `http://localhost:5173`.

The frontend API client defaults to `/api/v1`. For a Vite dev server talking directly to the Spring API, set:

```sh
export VITE_API_BASE_URL='http://localhost:8080/api/v1'
```

### Build and Typecheck

From `frontend/`:

```sh
npm run typecheck
npm run build
```

## Seed Users

Flyway migration `V14__seed_auth_users.sql` creates three local users:

```text
admin@example.com       ADMIN
dispatcher@example.com  DISPATCHER
worker@example.com      FIELD_WORKER
```

The frontend login form defaults to:

```text
Email:    worker@example.com
Password: Password123!
```

## API Summary

Base path:

```text
/api/v1
```

Implemented auth endpoints:

```text
POST /auth/login
POST /auth/refresh
POST /auth/logout
GET  /me
```

Implemented task endpoints:

```text
POST  /tasks
GET   /tasks
GET   /tasks/{taskId}
PATCH /tasks/{taskId}
POST  /tasks/{taskId}/assign
POST  /tasks/{taskId}/unassign
POST  /tasks/{taskId}/start
POST  /tasks/{taskId}/complete
POST  /tasks/{taskId}/block
POST  /tasks/{taskId}/comments
GET   /tasks/{taskId}/events
```

Implemented worker endpoints:

```text
GET  /workers/{workerId}/tasks
GET  /workers/{workerId}/route
POST /workers/me/location
```

Placeholder endpoints:

```text
POST  /shifts/start
POST  /shifts/{shiftId}/end
GET   /shifts/current
GET   /vehicles/{vehicleId}
PATCH /vehicles/{vehicleId}
GET   /sync?since={eventId}
POST  /sync/outbox
```

## Architecture Notes

The codebase is organized as a modular monolith. Each business area owns its domain, API, application, and persistence packages where applicable.

Key rules carried into the implementation:

- PostgreSQL is the durable source of truth.
- REST commands are authoritative.
- Realtime events are notifications, not durable state.
- Clients recover missed updates through sync/event IDs.
- Task assignment and lifecycle changes are protected by transactions and version checks.
- Field workers can only access their own assigned work.
- Dispatchers and admins own task creation, mutation, assignment, and rebalancing workflows.

## Development References

Start with these specs when continuing implementation:

```text
specs/review/architecture.md
specs/review/backend-implementation.md
specs/review/frontend-implementation.md
specs/review/api-contract.md
specs/review/data-model.md
specs/review/security-performance.md
specs/review/sprints.md
```

## Suggested Next Work

Recommended next implementation steps:

1. Add a local Docker Compose file for PostgreSQL/PostGIS.
2. Complete shift application service and connect the frontend current-shift query to the real API.
3. Finish durable sync/outbox replay with idempotency records.
4. Wire realtime publish-after-commit events to WebSocket or SSE subscribers.
5. Add integration tests around Flyway migrations, repository queries, and authorization boundaries.
6. Add dispatcher-facing task creation and assignment screens.
