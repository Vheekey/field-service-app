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

export type TaskProgressSummary = {
  total: number
  completed: number
  active: number
  blocked: number
  percentComplete: number
}
