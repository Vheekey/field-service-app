import { useQuery } from '@tanstack/vue-query'

import { mockTasks } from '@/mocks/tasks'
import { queryKeys } from './queryKeys'
import type { TaskListItem } from '@/types/task'

const USE_MOCK_TASKS = import.meta.env.VITE_USE_MOCK_API !== 'false'

async function fetchAssignedTasks(_workerId: string): Promise<TaskListItem[]> {
  if (USE_MOCK_TASKS) {
    return Promise.resolve([...mockTasks])
  }

  const { listWorkerTasks } = await import('@/api/tasks.api')
  return listWorkerTasks(_workerId)
}

export function useAssignedTasksQuery(workerId: string) {
  return useQuery({
    queryKey: queryKeys.tasks.assigned(workerId),
    queryFn: () => fetchAssignedTasks(workerId),
  })
}
