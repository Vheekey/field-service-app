export type TaskType = 'BATTERY_SWAP' | 'REBALANCE' | 'REPAIR' | 'INSPECTION' | 'OTHER'

export type TaskStatus = 'OPEN' | 'ASSIGNED' | 'IN_PROGRESS' | 'BLOCKED' | 'COMPLETED' | 'CANCELLED'

export type TaskPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'

export type GeoPoint = {
  latitude: number
  longitude: number
}

export type TaskListItem = {
  id: string
  type: TaskType
  status: TaskStatus
  priority: TaskPriority
  title: string
  location: GeoPoint
  address?: string
  dueAt?: string
  assigneeId?: string
  version: number
}

export type TaskDetail = TaskListItem & {
  vehicleId?: string | null
  description?: string | null
  requirements?: Record<string, unknown> | null
  assignedAt?: string | null
  startedAt?: string | null
  completedAt?: string | null
  blockedAt?: string | null
  cancelledAt?: string | null
}

export type PageResponse<T> = {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export type TaskProgressSummary = {
  total: number
  completed: number
  active: number
  blocked: number
  percentComplete: number
}
