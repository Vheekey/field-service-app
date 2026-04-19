# Frontend Spec — FieldOps Vue 3

---

## Stack

| Concern | Library | Rationale |
|---|---|---|
| Framework | Vue 3 + TypeScript | Composition API, strong TS support |
| Build | Vite | Fast HMR, first-class PWA plugin |
| Routing | Vue Router 4 | Official, fine-grained navigation guards |
| UI State | **Pinia** | Official Vue state, composable, devtools |
| Server State | **TanStack Vue Query** | Stale-while-revalidate, offline retry, background refetch |
| HTTP | Axios | Interceptors for token refresh |
| WebSocket | @stomp/stompjs + sockjs-client | STOMP protocol over SockJS |
| Offline queue | idb-keyval | Lightweight IndexedDB wrapper |
| Maps | MapLibre GL JS | Open-source, no API key required |
| Styling | Tailwind CSS + custom tokens | Utility-first, responsive by default |
| PWA | vite-plugin-pwa | Service worker, web manifest, background sync |
| Testing | Vitest + Vue Testing Library + Playwright | Unit + E2E |
| Icons | Lucide Vue | Clean, consistent |

---

## Pinia + Vue Query Split

This is the most important architectural decision in the frontend.

```
┌────────────────────────────────────────────────────────────┐
│  Pinia Stores (UI state — persisted in memory)             │
│  ┌──────────────┐ ┌───────────────┐ ┌──────────────────┐  │
│  │  authStore   │ │  shiftStore   │ │  wsStore         │  │
│  │  accessToken │ │  currentShift │ │  connectionState │  │
│  │  workerInfo  │ │  activeTaskId │ │  lastSyncAt      │  │
│  │  role        │ │  filterState  │ │  pendingQueue    │  │
│  └──────────────┘ └───────────────┘ └──────────────────┘  │
│                                                            │
│  TanStack Vue Query (server state — cached + synced)       │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  useTasks()          GET /tasks                     │   │
│  │  useTask(id)         GET /tasks/{id}                │   │
│  │  useShift()          GET /shifts/current            │   │
│  │  useWorkers()        GET /workers (dispatcher)      │   │
│  └─────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────┘
```

**Rule of thumb**:
- Does the data come from the server and need to be fresh? → **Vue Query**
- Is it pure UI state (who's logged in, what's selected, connection status)? → **Pinia**

---

## Application Roles / Views

### Worker App (`/worker/*`)

```
/worker/login              Login screen
/worker/shift              Shift overview + task list (home)
/worker/tasks/:id          Task detail + action buttons
/worker/map                Mini-map with own tasks pinned
/worker/profile            Profile + shift history
```

### Dispatcher App (`/dispatch/*`)

```
/dispatch/login            Login screen
/dispatch/dashboard        Fleet map + worker status overview
/dispatch/shifts           Shift management list
/dispatch/shifts/:id       Shift detail + task assignment
/dispatch/tasks/new        Create task form
/dispatch/tasks/:id        Task detail + audit trail
/dispatch/workers          Worker list
```

---

## Component Architecture

```
src/
├── main.ts
├── App.vue
├── router/
│   └── index.ts             Route definitions + guards
├── stores/
│   ├── auth.store.ts        Pinia — auth state
│   ├── shift.store.ts       Pinia — current shift UI state
│   └── ws.store.ts          Pinia — WebSocket connection + offline queue
├── composables/
│   ├── useTaskList.ts       Vue Query wrapper for GET /tasks
│   ├── useTaskDetail.ts     Vue Query wrapper for GET /tasks/:id
│   ├── useTaskMutation.ts   PATCH /tasks/:id/status with optimistic update
│   ├── useShift.ts          Vue Query wrapper for GET /shifts/current
│   ├── useWebSocket.ts      STOMP connection lifecycle
│   ├── useOfflineQueue.ts   IndexedDB queue drain on reconnect
│   └── useGeoLocation.ts    Periodic GPS ping
├── api/
│   ├── axios.ts             Axios instance + interceptors
│   ├── tasks.api.ts
│   ├── shifts.api.ts
│   ├── workers.api.ts
│   └── auth.api.ts
├── views/
│   ├── worker/
│   │   ├── ShiftView.vue
│   │   ├── TaskDetailView.vue
│   │   └── MapView.vue
│   └── dispatcher/
│       ├── DashboardView.vue
│       ├── ShiftDetailView.vue
│       └── TaskCreateView.vue
├── components/
│   ├── task/
│   │   ├── TaskCard.vue       Status badge, urgency dot, type icon
│   │   ├── TaskList.vue       Sorted/grouped list
│   │   ├── TaskStatusButton.vue  Context-aware action CTA
│   │   └── TaskAuditTimeline.vue
│   ├── shift/
│   │   ├── ShiftProgressBar.vue
│   │   └── ShiftSummaryCard.vue
│   ├── map/
│   │   ├── FieldMap.vue       MapLibre GL wrapper
│   │   ├── TaskPin.vue        Map marker for task
│   │   └── WorkerPin.vue      Map marker for worker
│   ├── layout/
│   │   ├── WorkerLayout.vue   Bottom-nav shell for worker
│   │   └── DispatchLayout.vue Sidebar shell for dispatcher
│   └── common/
│       ├── OfflineBanner.vue  Shown when navigator.onLine = false
│       ├── ConflictModal.vue  409 conflict resolution prompt
│       ├── Toast.vue
│       └── LoadingSpinner.vue
└── workers/
    └── sw.ts                  Service worker (Vite PWA)
```

---

## Offline / Reconnect Implementation

### Offline Detection

```typescript
// stores/ws.store.ts
const isOnline = ref(navigator.onLine)
window.addEventListener('online',  () => { isOnline.value = true;  onReconnect() })
window.addEventListener('offline', () => { isOnline.value = false })
```

### Mutation Queue (IndexedDB)

When a worker marks a task complete while offline:

```typescript
// composables/useOfflineQueue.ts
interface QueuedMutation {
  id: string
  endpoint: string
  method: 'PATCH' | 'POST'
  body: unknown
  queuedAt: string
}

// On offline mutation → push to IndexedDB
await set('mutation_queue', [...existing, mutation])

// On reconnect → drain in order
async function drainQueue() {
  const queue = await get<QueuedMutation[]>('mutation_queue') ?? []
  for (const mut of queue) {
    try {
      await axios({ method: mut.method, url: mut.endpoint, data: mut.body })
    } catch (e) {
      if (isConflict(e)) showConflictModal(mut)
    }
  }
  await del('mutation_queue')
}
```

### Reconnect Diff Fetch

```typescript
// After WS reconnect
const { lastSyncAt } = wsStore
await queryClient.invalidateQueries({ queryKey: ['tasks'] })
// Vue Query will refetch with updatedSince automatically via:
// GET /tasks?updatedSince=<lastSyncAt>
```

---

## WebSocket Integration

```typescript
// composables/useWebSocket.ts
export function useWebSocket() {
  const client = new Client({
    webSocketFactory: () => new SockJS('/ws'),
    connectHeaders: { Authorization: `Bearer ${authStore.accessToken}` },
    reconnectDelay: 5000,         // base; library does exponential backoff
    onConnect: () => {
      wsStore.setConnected(true)
      client.subscribe(`/user/queue/tasks`, onTaskEvent)
      onReconnect()               // fetch diff + drain queue
    },
    onDisconnect: () => wsStore.setConnected(false),
  })
  return client
}

function onTaskEvent(message: IMessage) {
  const event: WsEvent = JSON.parse(message.body)
  switch (event.event) {
    case 'task.assigned':
      queryClient.invalidateQueries({ queryKey: ['tasks'] })
      showToast(`New task assigned: ${event.payload.title}`)
      break
    case 'task.updated':
      queryClient.setQueryData(['tasks', event.taskId], event.payload)
      break
    case 'task.cancelled':
      queryClient.invalidateQueries({ queryKey: ['tasks'] })
      showToast(`Task cancelled: ${event.payload.title}`, 'warning')
      break
  }
}
```

---

## Optimistic Updates (409 Conflict Handling)

```typescript
// composables/useTaskMutation.ts
const mutation = useMutation({
  mutationFn: (vars) => tasksApi.patchStatus(vars.id, vars.body),
  onMutate: async (vars) => {
    await queryClient.cancelQueries({ queryKey: ['tasks', vars.id] })
    const previous = queryClient.getQueryData(['tasks', vars.id])
    // Optimistically update UI
    queryClient.setQueryData(['tasks', vars.id], old => ({
      ...old,
      status: vars.body.status
    }))
    return { previous }
  },
  onError: (err, vars, ctx) => {
    // Roll back
    queryClient.setQueryData(['tasks', vars.id], ctx.previous)
    if (err.response?.status === 409) {
      showConflictModal()   // "This task was updated — reload?"
      queryClient.invalidateQueries({ queryKey: ['tasks', vars.id] })
    }
  },
  onSettled: () => {
    queryClient.invalidateQueries({ queryKey: ['tasks'] })
  }
})
```

---

## Task Sorting Logic (client-side display order)

Workers see tasks sorted by:
1. **Urgency** (5 → 1) first
2. Within same urgency: **distance from current GPS** ascending
3. Status: `IN_PROGRESS` pinned to top, `COMPLETED` pushed to bottom

```typescript
function sortTasks(tasks: Task[], workerLat: number, workerLng: number) {
  return [...tasks].sort((a, b) => {
    if (a.status === 'IN_PROGRESS') return -1
    if (b.status === 'IN_PROGRESS') return 1
    if (a.status === 'COMPLETED' && b.status !== 'COMPLETED') return 1
    if (b.status === 'COMPLETED' && a.status !== 'COMPLETED') return -1
    if (b.urgency !== a.urgency) return b.urgency - a.urgency
    return distanceTo(a, workerLat, workerLng) - distanceTo(b, workerLat, workerLng)
  })
}
```

---

## PWA Configuration

```typescript
// vite.config.ts (VitePWA plugin)
VitePWA({
  registerType: 'autoUpdate',
  manifest: {
    name: 'FieldOps',
    short_name: 'FieldOps',
    theme_color: '#0F172A',
    display: 'standalone',
    orientation: 'portrait',
    icons: [/* 192, 512, maskable */]
  },
  workbox: {
    runtimeCaching: [
      {
        urlPattern: /\/api\/v1\/tasks/,
        handler: 'NetworkFirst',
        options: { cacheName: 'tasks-cache', networkTimeoutSeconds: 5 }
      },
      {
        urlPattern: /\/api\/v1\/shifts/,
        handler: 'NetworkFirst',
        options: { cacheName: 'shifts-cache', networkTimeoutSeconds: 5 }
      }
    ]
  }
})
```

`NetworkFirst` strategy: tries the network; if it fails (offline), serves from cache. Worker still sees their task list even with no connectivity.
