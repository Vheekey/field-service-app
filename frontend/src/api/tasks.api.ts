import { apiRequest } from './client'
import type { PageResponse, TaskDetail, TaskListItem } from '@/types/task'

export type ListTasksParams = {
  assigneeId?: string
  page?: number
  size?: number
  sort?: string
  status?: string
}

function paramsToQuery(params: ListTasksParams = {}) {
  const query = new URLSearchParams()

  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.set(key, String(value))
    }
  })

  const serialized = query.toString()
  return serialized ? `?${serialized}` : ''
}

function idempotencyKey(action: string, taskId: string) {
  return `${action}-${taskId}-${crypto.randomUUID()}`
}

export function listTasks(params?: ListTasksParams) {
  return apiRequest<PageResponse<TaskListItem>>(`/tasks${paramsToQuery(params)}`)
}

export function listWorkerTasks(workerUserId: string) {
  return apiRequest<TaskListItem[]>(`/workers/${workerUserId}/tasks`)
}

export function listWorkerRoute(workerUserId: string) {
  return apiRequest<TaskListItem[]>(`/workers/${workerUserId}/route`)
}

export function startTask(taskId: string) {
  return apiRequest<TaskDetail>(`/tasks/${taskId}/start`, {
    method: 'POST',
    headers: {
      'Idempotency-Key': idempotencyKey('start', taskId),
    },
  })
}

export function completeTask(taskId: string, completionNotes?: string) {
  return apiRequest<TaskDetail>(`/tasks/${taskId}/complete`, {
    method: 'POST',
    headers: {
      'Idempotency-Key': idempotencyKey('complete', taskId),
    },
    body: JSON.stringify({ completionNotes }),
  })
}

export function blockTask(taskId: string, reason: string) {
  return apiRequest<TaskDetail>(`/tasks/${taskId}/block`, {
    method: 'POST',
    headers: {
      'Idempotency-Key': idempotencyKey('block', taskId),
    },
    body: JSON.stringify({ reason }),
  })
}
