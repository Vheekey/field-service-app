# Reviewed Frontend Implementation

## Stack

- Vue 3
- TypeScript
- Vite
- Vue Router
- Pinia
- TanStack Vue Query
- Dexie for IndexedDB
- MapLibre GL JS or Leaflet
- STOMP WebSocket client or EventSource client
- Vite PWA plugin
- Vitest + Vue Testing Library + Playwright

## State Management

Use **Pinia + Vue Query**.

Use Vue Query for server state:

- assigned tasks
- dispatcher task list
- task detail
- worker route
- vehicle detail
- shift summaries

Use Pinia for client-owned state:

- auth/session shell
- selected task
- map viewport and filters
- online/offline status
- socket connection status
- last seen server `eventId`
- offline outbox UI state
- conflict banners/modals

This avoids rebuilding server-cache behavior manually in Pinia.

## Folder Structure

```text
frontend/
└── src/
    ├── main.ts
    ├── App.vue
    ├── router/
    │   └── index.ts
    ├── api/
    │   ├── client.ts
    │   ├── auth.api.ts
    │   ├── tasks.api.ts
    │   ├── shifts.api.ts
    │   ├── vehicles.api.ts
    │   ├── workers.api.ts
    │   └── sync.api.ts
    ├── queries/
    │   ├── useAssignedTasksQuery.ts
    │   ├── useTaskDetailQuery.ts
    │   ├── useDispatcherTasksQuery.ts
    │   └── useWorkerRouteQuery.ts
    ├── mutations/
    │   ├── useStartTaskMutation.ts
    │   ├── useCompleteTaskMutation.ts
    │   ├── useBlockTaskMutation.ts
    │   └── useAssignTaskMutation.ts
    ├── stores/
    │   ├── auth.store.ts
    │   ├── connectivity.store.ts
    │   ├── outbox.store.ts
    │   └── ui.store.ts
    ├── offline/
    │   ├── db.ts
    │   ├── outbox.ts
    │   ├── syncRunner.ts
    │   └── conflictResolution.ts
    ├── realtime/
    │   ├── realtimeClient.ts
    │   └── eventHandlers.ts
    ├── views/
    │   ├── worker/
    │   │   ├── ShiftView.vue
    │   │   ├── TaskListView.vue
    │   │   ├── TaskDetailView.vue
    │   │   └── MapView.vue
    │   └── dispatcher/
    │       ├── DashboardView.vue
    │       ├── TaskCreateView.vue
    │       ├── TaskDetailView.vue
    │       └── WorkersView.vue
    ├── components/
    │   ├── task/
    │   ├── map/
    │   ├── shift/
    │   ├── layout/
    │   └── common/
    └── workers/
        └── sw.ts
```

## Offline Data Model

Use Dexie tables:

```ts
type CachedTask = {
  id: string
  version: number
  status: string
  payload: unknown
  updatedAt: string
}

type OutboxCommand = {
  clientMutationId: string
  idempotencyKey: string
  type: 'START_TASK' | 'COMPLETE_TASK' | 'BLOCK_TASK' | 'ADD_COMMENT'
  payload: unknown
  status: 'PENDING' | 'SYNCING' | 'APPLIED' | 'CONFLICT' | 'FAILED'
  retryCount: number
  createdAt: string
  lastTriedAt?: string
}
```

Do not delete the entire queue after one drain attempt. Mark each command independently.

## Reconnect Flow

On reconnect:

1. Reconnect realtime client.
2. Read latest stored `eventId`.
3. Call `GET /api/v1/sync?since=<eventId>`.
4. Apply returned events in ascending order.
5. Persist latest `eventId`.
6. Replay pending outbox commands through `POST /api/v1/sync/outbox`.
7. Mark each command based on server result.
8. Refetch active queries with Vue Query.

Do not rely on `updatedSince=<timestamp>` as the only sync mechanism. Timestamps can collide, drift, or miss boundary updates.

## Offline-Safe Actions

MVP offline-safe worker actions:

- start task
- block task
- add comment
- complete task with basic metadata

Online-only for MVP:

- reassignment
- accepting urgent new assignment
- large media upload
- dispatcher edits

## Realtime Handling

Realtime event handling should be targeted:

- update a task detail query if the event contains enough fresh state
- invalidate one task detail query if state is partial
- invalidate assigned task list for assignment/unassignment
- invalidate dispatcher map/list queries by filter or viewport

Avoid refetching every task list for every event.

Each event includes `eventId`; store it after applying the event.

## Worker Routes

Prefer server-provided route ordering for consistency:

- `GET /api/v1/workers/{workerId}/route`

The frontend may still sort locally for display fallback:

1. `IN_PROGRESS` first
2. urgent/high priority next
3. due soon
4. nearest
5. completed last

## Auth Handling

Recommended:

- short-lived access token in memory
- refresh token in secure HttpOnly SameSite cookie
- refresh on app boot and on `401`
- clear cached sensitive data on logout

If refresh token must be returned in JSON during development, treat that as temporary only.

## PWA Caching

Use service worker caching for static assets and cautious network-first API reads.

Do not let service worker cached API responses hide sync conflicts. The source of truth for offline task state should be IndexedDB plus the event/outbox sync protocol.

## Frontend Tests

Required:

- worker completes task online
- worker completes task offline and syncs later
- failed reconnect does not lose queued command
- conflict result keeps local captured data
- WebSocket event invalidates only relevant queries
- role-based route guards
- dispatcher assignment flow
