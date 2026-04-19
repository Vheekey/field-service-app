# Field Service Implementation README

This repository contains the first implementation slice of a field service application for dispatchers and field workers. The implementation follows the reviewed architecture in `specs/review`: a Spring Boot modular monolith API, PostgreSQL/PostGIS as the source of truth, REST for durable commands, and a Vue 3 worker-facing frontend.

## Repository Layout

```text
backend/   Spring Boot 3 API, domain modules, Flyway migrations, tests
frontend/  Vue 3 + Vite frontend, worker task UI, auth state, API clients
specs/     Architecture, API contract, data model, and sprint references
docs/      Implementation-facing documentation
```

## How to Set Up Locally

Use these steps to get a fresh checkout running for local development.

### 1. Install prerequisites

- Java 21
- Maven 3.9+
- Node.js 20+
- npm
- PostgreSQL 15+ with PostGIS available

The backend expects PostgreSQL on `localhost:5432` by default. Choose one database setup option.

### 2. Set up the database with Docker

If you do not have PostGIS installed locally, you can run a disposable database with Docker:

```sh
docker run --name field-service-postgis \
  -e POSTGRES_DB=field_service \
  -e POSTGRES_USER=field_service \
  -e POSTGRES_PASSWORD=field_service \
  -p 5432:5432 \
  postgis/postgis:16-3.4
```

To restart that database later:

```sh
docker start field-service-postgis
```

### 3. Set up the database without Docker

Use this path if you already run PostgreSQL locally or want a native install.

Install PostgreSQL and PostGIS:

```sh
brew install postgresql@16 postgis
brew services start postgresql@16
```

If your shell cannot find the PostgreSQL commands after installation, add PostgreSQL to your path:

```sh
export PATH="/opt/homebrew/opt/postgresql@16/bin:$PATH"
```

On Intel Macs, the Homebrew path may be:

```sh
export PATH="/usr/local/opt/postgresql@16/bin:$PATH"
```

Create the local database user and database:

```sh
createuser field_service --pwprompt
createdb field_service --owner field_service
```

Use `field_service` as the password unless you plan to override the Spring datasource settings.

The first backend start runs Flyway migrations, including `pgcrypto` and `postgis` extension setup. If the `field_service` user cannot create extensions, connect as a PostgreSQL superuser and enable them first:

```sh
psql -d field_service -c 'CREATE EXTENSION IF NOT EXISTS pgcrypto;'
psql -d field_service -c 'CREATE EXTENSION IF NOT EXISTS postgis;'
```

Check that the app user can connect:

```sh
psql 'postgresql://field_service:field_service@localhost:5432/field_service' -c 'select current_database();'
```

### 4. Configure local environment

Set a local JWT signing secret before starting the backend:

```sh
export APP_SECURITY_JWT_SECRET='replace-with-a-long-local-secret'
```

If your database connection differs from the defaults in `backend/src/main/resources/application.yml`, override it with Spring environment variables:

```sh
export SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5432/field_service'
export SPRING_DATASOURCE_USERNAME='field_service'
export SPRING_DATASOURCE_PASSWORD='field_service'
```

### 5. Start the backend

From the backend directory:

```sh
cd backend
mvn spring-boot:run
```

The API starts at `http://localhost:8080`. Check it with:

```sh
curl http://localhost:8080/actuator/health
```

### 6. Start the frontend

In a second terminal, install dependencies and start Vite:

```sh
cd frontend
npm install
npm run dev
```

The frontend starts at `http://localhost:5173` and proxies `/api` requests to the backend through `frontend/vite.config.ts`.

For frontend-only work, the app uses mock task and shift data by default. To call the Spring API instead, start Vite with:

```sh
VITE_USE_MOCK_API=false npm run dev
```

### 7. Log in

Use the seeded worker account:

```text
Email:    worker@example.com
Password: Password123!
```

Other seeded accounts are listed in the Seed Users section below.

### 8. Run verification checks

Backend tests:

```sh
cd backend
mvn test
```

Frontend typecheck and production build:

```sh
cd frontend
npm run typecheck
npm run build
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
- Shift APIs for starting, ending, and reading the authenticated worker's current active shift.
- Global API error shape and validation handling.
- Unit/controller tests for auth, security conversion, task application behavior, and shift application behavior.

Implemented frontend foundations:

- Vue 3, Vue Router, Pinia, and TanStack Vue Query.
- Login flow with stored access token and refresh-cookie support.
- Worker task list screen with progress summary, task cards, current shift display, and offline/connectivity state.
- API clients for auth, tasks, and current-shift reads.
- Mock task and shift data for fallback/frontend development paths.

Known incomplete or placeholder areas:

- Vehicle endpoints currently return `501 Not Implemented`.
- Sync endpoints currently return `501 Not Implemented`.
- Realtime publishing package exists as a foundation, but end-to-end WebSocket/SSE delivery is not complete.
- Idempotency headers are required on some task commands, but full replay persistence is still part of the sync/outbox work.
- The frontend reads the real current-shift endpoint only when `VITE_USE_MOCK_API=false`; shift start/end UI actions are not wired yet.
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

The worker task and shift queries use mock data by default for frontend-only development. To call the Spring API instead, set:

```sh
export VITE_USE_MOCK_API='false'
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

Implemented shift endpoints:

```text
POST  /shifts/start
POST  /shifts/{shiftId}/end
GET   /shifts/current
```

Placeholder endpoints:

```text
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
2. Add frontend controls and API helpers for starting and ending shifts.
3. Finish durable sync/outbox replay with idempotency records.
4. Wire realtime publish-after-commit events to WebSocket or SSE subscribers.
5. Add integration tests around Flyway migrations, repository queries, and authorization boundaries.
6. Add dispatcher-facing task creation and assignment screens.
