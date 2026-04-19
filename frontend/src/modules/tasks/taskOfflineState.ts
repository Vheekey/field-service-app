import type { TaskListItem } from '@/types/task'

const COOKIE_KEY = 'field-service-task-state'
const SNAPSHOT_KEY = 'field-service-task-snapshot'

export type TaskListMode = 'urgency' | 'route'

export type SavedTaskState = {
  focusMode: boolean
  listMode: TaskListMode
  selectedTaskId: string | null
}

const defaultState: SavedTaskState = {
  focusMode: false,
  listMode: 'urgency',
  selectedTaskId: null,
}

function readCookie(name: string) {
  return document.cookie
    .split('; ')
    .find((item) => item.startsWith(`${name}=`))
    ?.split('=')
    .slice(1)
    .join('=')
}

export function readTaskState(): SavedTaskState {
  const raw = readCookie(COOKIE_KEY)

  if (!raw) {
    return defaultState
  }

  try {
    return { ...defaultState, ...JSON.parse(decodeURIComponent(raw)) }
  } catch {
    return defaultState
  }
}

export function saveTaskState(state: SavedTaskState) {
  document.cookie = `${COOKIE_KEY}=${encodeURIComponent(JSON.stringify(state))}; path=/; max-age=604800; SameSite=Lax`
}

export function readTaskSnapshot() {
  try {
    const snapshot = localStorage.getItem(SNAPSHOT_KEY)
    return snapshot ? (JSON.parse(snapshot) as TaskListItem[]) : []
  } catch {
    return []
  }
}

export function saveTaskSnapshot(tasks: TaskListItem[]) {
  localStorage.setItem(SNAPSHOT_KEY, JSON.stringify(tasks))
}
