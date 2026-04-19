import { computed, ref, toValue, watch, type MaybeRefOrGetter } from 'vue'
import { useMutation } from '@tanstack/vue-query'

import { blockTask, completeTask, startTask } from '@/api/tasks.api'
import { queryClient } from '@/plugins/queryClient'
import { getTaskProgress } from '@/modules/tasks/taskProgress'
import {
  readTaskSnapshot,
  readTaskState,
  saveTaskSnapshot,
  saveTaskState,
  type TaskListMode,
} from '@/modules/tasks/taskOfflineState'
import { sortTasksByRoute, sortTasksByUrgency } from '@/modules/tasks/taskOrdering'
import { useAssignedTasksQuery } from '@/queries/useAssignedTasksQuery'
import { queryKeys } from '@/queries/queryKeys'
import type { CurrentUser } from '@/types/auth'
import type { TaskListItem } from '@/types/task'

function canMutateTask(task: TaskListItem, currentUserId?: string) {
  return Boolean(currentUserId && task.assigneeId === currentUserId && !['COMPLETED', 'CANCELLED'].includes(task.status))
}

function mergeUpdatedTask(tasks: TaskListItem[], updatedTask: TaskListItem) {
  return tasks.map((task) => (task.id === updatedTask.id ? { ...task, ...updatedTask } : task))
}

export function useWorkerTasks(user: MaybeRefOrGetter<CurrentUser | null>) {
  const savedState = readTaskState()
  const currentUser = computed(() => toValue(user))
  const userId = computed(() => currentUser.value?.id ?? '')
  const roles = computed(() => currentUser.value?.roles ?? [])
  const assignedTasksQuery = useAssignedTasksQuery(userId, roles)
  const cachedTasks = ref<TaskListItem[]>(readTaskSnapshot())
  const focusMode = ref(savedState.focusMode)
  const listMode = ref<TaskListMode>(savedState.listMode)
  const selectedTaskId = ref<string | null>(savedState.selectedTaskId)

  const rawTasks = computed(() => assignedTasksQuery.data.value ?? cachedTasks.value)
  const sortedTasks = computed(() => (
    listMode.value === 'route'
      ? sortTasksByRoute(rawTasks.value)
      : sortTasksByUrgency(rawTasks.value)
  ))
  const selectedTask = computed(() => sortedTasks.value.find((task) => task.id === selectedTaskId.value) ?? null)
  const tasks = computed(() => (focusMode.value && selectedTask.value ? [selectedTask.value] : sortedTasks.value))
  const progress = computed(() => getTaskProgress(sortedTasks.value))
  const mutation = useMutation({
    mutationFn: async ({ action, task }: { action: 'start' | 'complete' | 'skip', task: TaskListItem }) => {
      if (!canMutateTask(task, userId.value)) {
        throw new Error('Only the assigned worker can update this task.')
      }

      if (action === 'start') {
        return startTask(task.id)
      }

      if (action === 'complete') {
        return completeTask(task.id)
      }

      return blockTask(task.id, 'Skipped by worker')
    },
    onSuccess: (updatedTask) => {
      cachedTasks.value = mergeUpdatedTask(rawTasks.value, updatedTask)
      saveTaskSnapshot(cachedTasks.value)
      queryClient.invalidateQueries({ queryKey: queryKeys.tasks.all })
    },
  })

  function selectTask(taskId: string) {
    selectedTaskId.value = taskId
  }

  function setListMode(mode: TaskListMode) {
    listMode.value = mode
  }

  function toggleFocusMode() {
    if (!selectedTaskId.value && sortedTasks.value[0]) {
      selectedTaskId.value = sortedTasks.value[0].id
    }

    focusMode.value = !focusMode.value
  }

  function updateTask(action: 'start' | 'complete' | 'skip', task: TaskListItem) {
    return mutation.mutateAsync({ action, task })
  }

  watch(assignedTasksQuery.data, (loadedTasks) => {
    if (!loadedTasks) {
      return
    }

    cachedTasks.value = loadedTasks
    saveTaskSnapshot(loadedTasks)

    if (selectedTaskId.value && !loadedTasks.some((task) => task.id === selectedTaskId.value)) {
      selectedTaskId.value = loadedTasks[0]?.id ?? null
    }
  }, { immediate: true })

  watch([focusMode, listMode, selectedTaskId], () => {
    saveTaskState({
      focusMode: focusMode.value,
      listMode: listMode.value,
      selectedTaskId: selectedTaskId.value,
    })
  })

  return {
    canMutateTask: (task: TaskListItem) => canMutateTask(task, userId.value),
    error: assignedTasksQuery.error,
    focusMode,
    isFetching: assignedTasksQuery.isFetching,
    isLoading: assignedTasksQuery.isLoading,
    isUpdating: mutation.isPending,
    listMode,
    progress,
    refetch: assignedTasksQuery.refetch,
    selectTask,
    selectedTaskId,
    selectedTask,
    setListMode,
    sortedTasks,
    tasks,
    toggleFocusMode,
    updateError: mutation.error,
    updateTask,
  }
}
