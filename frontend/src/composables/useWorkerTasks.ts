import { computed, ref } from 'vue'

import { getTaskProgress } from '@/modules/tasks/taskProgress'
import { useAssignedTasksQuery } from '@/queries/useAssignedTasksQuery'

const DEMO_WORKER_ID = '3f047f66-478d-4097-ac18-5a9d27b7df84'

export function useWorkerTasks(workerId = DEMO_WORKER_ID) {
  const assignedTasksQuery = useAssignedTasksQuery(workerId)
  const selectedTaskId = ref<string | null>(null)

  const tasks = computed(() => assignedTasksQuery.data.value ?? [])
  const progress = computed(() => getTaskProgress(tasks.value))

  return {
    error: assignedTasksQuery.error,
    isFetching: assignedTasksQuery.isFetching,
    isLoading: assignedTasksQuery.isLoading,
    progress,
    refetch: assignedTasksQuery.refetch,
    selectedTaskId,
    tasks,
  }
}
