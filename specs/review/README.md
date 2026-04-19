# Reviewed Combined Implementation

This folder contains the reviewed, combined implementation plan for the field service application.

It uses the Codex specs as the architectural baseline and folds in the strongest Claude details:

- concrete Spring Boot module structure
- concrete Vue 3 folder structure
- PWA implementation guidance
- Testcontainers, Micrometer, Bucket4j, Actuator, and deployment notes
- secure refresh-token cookie recommendation

The review findings have been resolved in these combined docs:

- Assignment uniqueness is enforced with transactions plus active assignment history constraints.
- Offline sync uses durable event IDs and idempotent outbox commands instead of timestamp-only diffing.
- Realtime events are published only after durable event persistence and transaction commit.
- WebSocket origins and subscriptions must be authorized.
- Redis is not used as a first-line cache for live task lists; database indexes and query caching come first.

Source specs:

- [Codex implementation](../codex)
- [Claude implementation](../claude)

Recommended reading order:

1. [architecture.md](architecture.md)
2. [backend-implementation.md](backend-implementation.md)
3. [frontend-implementation.md](frontend-implementation.md)
4. [api-contract.md](api-contract.md)
5. [data-model.md](data-model.md)
6. [security-performance.md](security-performance.md)
7. [sprints.md](sprints.md)
