import { useQuery } from '@tanstack/vue-query'
import { computed, type Ref } from 'vue'

import { listTasks, listWorkerTasks } from '@/api/tasks.api'
import { mockTasks } from '@/mocks/tasks'
import { queryKeys } from './queryKeys'
import type { TaskListItem } from '@/types/task'
import type { UserRole } from '@/types/auth'

const USE_MOCK_TASKS = import.meta.env.VITE_USE_MOCK_API !== 'false'

async function fetchAssignedTasks(userId: string, roles: UserRole[]): Promise<TaskListItem[]> {
  if (USE_MOCK_TASKS) {
    return Promise.resolve([...mockTasks])
  }

  const canViewAllTasks = roles.some((role) => role === 'ADMIN' || role === 'DISPATCHER')

  if (canViewAllTasks) {
    const response = await listTasks({ size: 100, sort: 'dueAt,asc' })
    return response.items
  }

  return listWorkerTasks(userId)
}

export function useAssignedTasksQuery(userId: Ref<string>, roles: Ref<UserRole[]>) {
  return useQuery({
    enabled: computed(() => Boolean(userId.value)),
    queryKey: computed(() => queryKeys.tasks.list(`${userId.value}:${roles.value.join(',')}`)),
    queryFn: () => fetchAssignedTasks(userId.value, roles.value),
  })
}
